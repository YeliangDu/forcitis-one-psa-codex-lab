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


def installation(path, pin):
    raw = private_file(path)
    need(hashlib.sha256(raw).hexdigest() == pin, 'external-installation-pin-mismatch')
    cfg = json.loads(raw)
    need(set(cfg) == {'native_endpoint', 'native_session', 'physical_cwd', 'role',
                     'proxy_binary', 'gh_binary', 'runtime_directory'}, 'installation-fields')
    need(cfg['native_session'] == '01a1be37-1c95-71e0-ae7d-3947f3fb9cac', 'new-role-registration-not-authorized')
    workspace = physical(cfg['physical_cwd'])
    need(workspace.is_dir() and workspace.name == 'forcitis-one-psa-codex-lab',
         'approved-lab-workspace-required')
    need(type(cfg['role']) is str and cfg['role'].startswith('one-'), 'bound-role-required')
    need(str(Path(cfg['native_endpoint'])) in ('/tmp/lab60-pilot.cnCGuJHo/server.sock',
                                              '/private/tmp/lab60-pilot.cnCGuJHo/server.sock'), 'wrong-pilot-endpoint')
    directory = physical(cfg['runtime_directory'])
    info = directory.stat()
    need(info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) == 0o700
         and directory.name == 'r1-r2-runtime' and '.git' not in directory.parts, 'private-runtime-directory-required')
    for name in ('proxy_binary', 'gh_binary'):
        need(Path(cfg[name]).is_absolute(), 'absolute-approved-binary-required')
    cfg['pin'] = pin
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
        self.store = Store(cfg['runtime_directory'], cfg)
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
            connection = NativeProxy(self.cfg, self.native_record, self.native_call)
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


def serve(cfg):
    os.umask(0o077)
    directory = physical(cfg['runtime_directory'])
    lock_path = directory / 'broker.lock'
    fd = os.open(str(lock_path), os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        path = directory / 'broker.sock'
        # Stale socket is not silently removed: owner/operator readback first.
        need(not path.exists() and not path.is_symlink(), 'broker-socket-existing-reconcile-before-start')
        broker = Broker(cfg)
        with Server(str(path), Handler) as server:
            os.chmod(path, 0o600)
            server.broker = broker
            try:
                server.serve_forever(poll_interval=0.5)
            finally:
                if broker.native:
                    broker.native.close()
                broker.store.db.close()
                path.unlink()
    finally:
        os.close(fd)


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    sub = parser.add_subparsers(dest='mode', required=True)
    start = sub.add_parser('serve')
    start.add_argument('--installation', required=True)
    start.add_argument('--installation-sha256', required=True)
    call = sub.add_parser('call')
    call.add_argument('--socket', required=True)
    call.add_argument('--request', required=True)
    args = parser.parse_args()
    if args.mode == 'serve':
        serve(installation(args.installation, args.installation_sha256))
    else:
        # Thin CLI owns no native endpoint, generation state or external effects.
        path = physical(args.socket)
        info = path.stat()
        need(stat.S_ISSOCK(info.st_mode) and info.st_uid == os.geteuid()
             and info.st_mode & 0o077 == 0, 'private-broker-socket-required')
        request = json.loads(private_file(args.request))
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(30)
            client.connect(str(path))
            send(client, request)
            print(wire(message(client)))


if __name__ == '__main__':
    main()
