"""Single public CLI/Broker. Starts only by an explicit operator command.

No replay oracle, arbitrary RPC/shell passthrough, formal queue or writer route.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import socket
import socketserver
import stat
import struct
import subprocess
import threading

from runtime_store import Store, need, wire
from native_transport import NativeProxy
from orphan_terminal import ArchiveStore as RoleStore, ArchiveSupport as RoleSupport

LIMIT = 2 * 1024 * 1024


def physical(path):
    value = Path(path)
    need(value.is_absolute() and str(value.resolve(strict=True)) == str(value)
         and not any(p.is_symlink() for p in (value, *value.parents)), 'physical-path-required')
    return value


def private_file(path, limit=LIMIT):
    value = physical(path)
    fd = os.open(str(value), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        need(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and info.st_nlink == 1
             and info.st_mode & 0o077 == 0 and 0 < info.st_size <= limit, 'private-input-required')
        raw = bytearray()
        while len(raw) <= limit:
            part = os.read(fd, min(65536, limit + 1 - len(raw)))
            if not part:
                break
            raw.extend(part)
        need(len(raw) <= limit, 'input-size-bound')
        return bytes(raw)
    finally:
        os.close(fd)


def installation(path, pin, binding):
    raw = private_file(path)
    need(hashlib.sha256(raw).hexdigest() == pin, 'existing-installation-pin-mismatch')
    cfg = json.loads(raw)
    need(set(cfg) == {'native_endpoint','native_session','physical_cwd','role',
                     'proxy_binary','gh_binary','runtime_directory'}, 'existing-installation-fields')
    selected = binding.view()
    need(cfg['runtime_directory'] == selected['ledger']['directory']
         and pin == selected['ledger']['installation_sha256']
         and cfg['physical_cwd'] == selected['role']['physical_cwd']
         and cfg['role'] == selected['role']['name']
         and Path(cfg['proxy_binary']).resolve(strict=True) == Path(selected['native']['proxy']['path']), 'old-installation-and-private-binding-disagree')
    old_proxy = Path(cfg['proxy_binary'])
    old_before = old_proxy.lstat()
    binary = Path(selected['native']['proxy']['path'])
    need(binary.resolve(strict=True) == binary and binary.is_file()
         and hashlib.sha256(binary.read_bytes()).hexdigest() == selected['native']['proxy']['sha256'],
         'independently-selected-physical-proxy-target-mismatch')
    old_after = old_proxy.lstat()
    need((old_before.st_dev,old_before.st_ino,old_before.st_mtime_ns,old_before.st_ctime_ns) ==
         (old_after.st_dev,old_after.st_ino,old_after.st_mtime_ns,old_after.st_ctime_ns)
         and old_after.st_uid in (0,os.geteuid())
         and old_proxy.resolve(strict=True) == binary, 'approved-old-proxy-alias-drift')
    cfg['pin'], cfg['binding'] = pin, binding
    return cfg


def receive(stream, count):
    out = bytearray()
    while len(out) < count:
        part = stream.recv(count - len(out))
        if not part:
            raise EOFError('broker-client-closed')
        out.extend(part)
    return bytes(out)


def message(stream):
    size = struct.unpack('!I', receive(stream, 4))[0]
    need(0 < size <= LIMIT, 'broker-message-bound')
    value = json.loads(receive(stream, size))
    need(type(value) is dict, 'broker-object-required')
    return value


def send(stream, value):
    raw = wire(value).encode()
    need(len(raw) <= LIMIT, 'broker-output-bound')
    stream.sendall(struct.pack('!I', len(raw)) + raw)


class Broker:
    def __init__(self, cfg):
        self.cfg = cfg
        self.binding = cfg['binding']
        self.session_id = self.binding.view()['native']['expected_registered_session']
        self.store = RoleStore(cfg['runtime_directory'], cfg)
        # Serialize authorization and every actually executed local operation
        # against revoke/publish. SQLite alone cannot fence an in-flight read.
        self.execution_gate = threading.RLock()
        self.native = None

    def native_record(self, value):
        with self.store.tx() as db:
            self.store.event(db, 'actual-native-wire-receipt', value)

    def native_call(self, connection, request):
        with self.execution_gate:
            if connection is not self.native or request.get('method') != 'item/tool/call':
                self.store.archive(request)
                return None
            # Existing pilot has no dynamic tools. No turn has been dispatched
            # by this broker, so any pending/replayed call is unbound/obsolete.
            # Do not feed a fake result back to a native old call.
            self.store.archive(dict(connection_id=connection.connection_id, request=request,
                                    disposition='unbound-call-no-reply'))
            return None

    def _native_inspect(self):
        if self.native is None:
            connection = NativeProxy(self.binding, self.native_record, self.native_call)
            self.native = connection
            try:
                initialized = connection.connect()
                self.native_record(dict(connection_id=connection.connection_id, initialization=initialized))
            except BaseException:
                self.native = None
                raise
        try:
            return self.native.inspect()
        except BaseException:
            self.native.close()
            self.native = None
            raise

    def _ci_readback(self, effect_id):
        # Call only after owner authorization under execution_gate. This adapter
        # is GET-only; missing exact external-effect causality remains unknown.
        with self.store.tx() as db:
            effect = db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone()
            need(effect is not None, 'unknown-effect')
            target = json.loads(effect['target'])
        endpoint = ('repos/' + target['repository'] + '/actions/runs/' + str(target['run_id']))
        result = subprocess.run([self.cfg['gh_binary'], 'api', '--method', 'GET', endpoint],
                                capture_output=True, timeout=20, check=False)
        need(result.returncode == 0 and len(result.stdout) <= LIMIT, 'github-readback-unavailable')
        run = json.loads(result.stdout)
        need(run.get('id') == target['run_id'] and run.get('head_sha') == target['head']
             and run.get('repository', {}).get('full_name') == target['repository'], 'github-readback-tuple-mismatch')
        observation = dict(path=endpoint, raw_utf8=result.stdout.decode(),
                           raw_sha256=hashlib.sha256(result.stdout).hexdigest(),
                           observed_attempt=run.get('run_attempt'))
        return self.store.observe_effect(effect_id, observation)

    def handle(self, request):
        command = request.get('command')
        args = request.get('arguments', {})
        need(type(args) is dict, 'command-arguments-required')
        with self.execution_gate:
            if command == 'owner.acquire':
                return dict(action='granted', auth=self.store.acquire(args['client'], args['expected_generation']))
            auth = request.get('auth')
            # Every other public route, including reads and denied operations,
            # checks the persistent generation, not a client-supplied role flag.
            with self.store.tx() as db:
                self.store.owner(db, auth)
            if command == 'owner.release':
                return self.store.release(auth)
            if command == 'assignment.publish':
                return self.store.publish(auth, args['assignment'])
            if command == 'assignment.revoke':
                return self.store.revoke(auth)
            if command == 'pause':
                return self.store.pause(auth, True)
            if command == 'resume':
                return self.store.pause(auth, False)  # Broker pause only; not thread/resume.
            if command == 'inspect':
                return self.store.snapshot(auth)
            if command == 'effect.prepare':
                return self.store.prepare_effect(auth, args['revision'], args.get('operation_key', 'ignored'),
                                                 args['kind'], args['target'])
            if command == 'notification.stage':
                return self.store.stage_notification(auth, args['delivery_id'], args['body'])
            if command == 'wait.drain':
                return self.store.drain(auth, args['revision'])
            if command == 'effect.reconcile':
                with self.store.tx() as db:
                    self.store.binding(db, auth, args['revision'], paused_ok=True)
                    effect = db.execute('select revision from effects where effect_id=?', (args['effect_id'],)).fetchone()
                    need(effect is not None and effect['revision'] == args['revision'], 'stale-effect-readback')
                return self._ci_readback(args['effect_id'])
            if command == 'native.inspect':
                return self._native_inspect()
            if command in ('native.resume', 'native.start-turn', 'tool.execute', 'tool.reply'):
                return dict(action='hold', reason='existing-native-id-has-no-bound-broker-only-dynamic-tools')
            if command == 'effect.issue':
                return dict(action='hold', reason='independently-authorized-external-executor-not-installed')
            if command == 'plan':
                return dict(action='hold', reason='generic-same-H-readonly-plan-approval-collector-not-bound',
                            formal_enqueue_allowed=False, writer_allowed=False)
            # No route or imported callable can reach enqueue/once/merge/writer.
            raise ValueError('broker-command-denied')


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(30)
        try:
            request = message(self.request)
            result = self.server.broker.handle(request)
        except (ValueError, KeyError, TypeError, OSError, EOFError, RuntimeError, subprocess.SubprocessError):
            result = dict(action='hold', reason='broker-request-refused-or-capability-unavailable')
        send(self.request, result)


class Server(socketserver.ThreadingUnixStreamServer):
    daemon_threads = False
    block_on_close = True


def serve(cfg, *, verifier=None, branch_installation=None, resource_guard=None, goal_installation=None,
          plan_installation=None, broker_type=None, startup_authority=None):
    from startup_authority import StartupAuthority
    need(type(startup_authority) is StartupAuthority, 'independent-typed-startup-authorization-required')
    need(broker_type is not None, 'portable-factory-composition-required')
    # This guard holds the EXISTING original broker lock across Store/socket and
    # the entire runtime. It creates no lock, DB, schema, directory or socket.
    with startup_authority.acquire(cfg):
        os.umask(0o077)
        directory = physical(cfg['runtime_directory'])
        path = directory / 'broker.sock'
        need(not path.exists() and not path.is_symlink(), 'broker-socket-existing-reconcile-before-start')
        broker = broker_type(cfg)
        broker.install_verifier(verifier)
        broker.install_branch(branch_installation, resource_guard)
        broker.install_goal(goal_installation)
        broker.install_plan(plan_installation)
        with Server(str(path), Handler) as server:
            os.chmod(path, 0o600)
            server.broker = broker
            try:
                server.serve_forever(poll_interval=0.5)
            finally:
                broker.close_handoff_observers()
                if broker.native:
                    broker.native.close()
                broker.store.db.close()
                path.unlink()


def main():
    raise SystemExit('HOLD: use the independently pinned portable_broker.py factory')


if __name__ == '__main__':
    main()
