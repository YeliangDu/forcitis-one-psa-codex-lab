"""Bound AF_UNIX parent receiver; caller is independent hosted test supervisor."""
import base64
import copy
import json
import os
from pathlib import Path
import socket
import struct
import threading

LIMIT = 4 * 1024 * 1024


def need(ok, reason):
    if not ok:
        raise AssertionError(reason)


def decode(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, 'IPC_DUPLICATE_JSON')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda value: need(False, 'IPC_NONFINITE_JSON'))


def exact(a, b):
    return json.dumps(a, sort_keys=True, allow_nan=False) == json.dumps(b, sort_keys=True, allow_nan=False)


class ParentReceiver:
    def __init__(self, socket_path, raw_ledger, allowed_reads, host_probe,
                 expected_approval, protected_merge, recovery_probe=None,
                 ordinary_boundary=None):
        self.path = Path(socket_path)
        need(self.path.is_absolute() and self.path.parent.resolve(strict=True) == self.path.parent
             and not self.path.exists(), 'IPC_UNSAFE_SOCKET_PATH')
        self.ledger, self.allowed = raw_ledger, copy.deepcopy(allowed_reads)
        self.host, self.approval, self.merge = host_probe, copy.deepcopy(expected_approval), protected_merge
        self.recovery, self.ordinary = recovery_probe, ordinary_boundary
        self.peers, self.audit, self.errors = {}, [], []
        self.stop = threading.Event()
        self.listener = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.listener.bind(str(self.path))
        self.path.chmod(0o600)
        self.listener.listen(8)
        self.listener.settimeout(0.1)
        self.thread = threading.Thread(target=self.serve, daemon=True)

    def register_peer(self, role, pid):
        need(role in ('old', 'successor') and type(pid) is int and pid > 0
             and pid not in self.peers, 'IPC_UNVERIFIED_CHILD_ROLE_PID')
        self.peers[pid] = role

    def start(self):
        self.thread.start()
        return self

    def close(self):
        self.stop.set()
        self.thread.join(timeout=5)
        self.listener.close()
        need(not self.thread.is_alive(), 'IPC_RECEIVER_STILL_RUNNING')

    def serve(self):
        while not self.stop.is_set():
            try:
                connection, _ = self.listener.accept()
            except socket.timeout:
                continue
            with connection:
                try:
                    # Linux hosted real peer PID/UID, never RPC claimed role.
                    pid, uid, _ = struct.unpack('3i', connection.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12))
                    need(uid == os.geteuid() and pid in self.peers, 'IPC_UNKNOWN_PEER_NO_AUTHORITY')
                    connection.settimeout(20)
                    raw = connection.makefile('rb').readline(LIMIT + 1)
                    need(raw.endswith(b'\n') and 0 < len(raw) <= LIMIT, 'IPC_INVALID_FRAME')
                    value = decode(raw)
                    result, reply = self.handle(self.peers[pid], value)
                    if reply:
                        connection.sendall(json.dumps(dict(version=1, result=result),
                                           allow_nan=False, separators=(',', ':')).encode() + b'\n')
                except Exception as error:
                    self.errors.append(type(error).__name__ + ':' + str(error))
                    # Refusal closes frame; no fake success or retry.

    def handle(self, role, request):
        need(type(request) is dict and set(request) == {'version', 'method', 'arguments'}
             and type(request['version']) is int and request['version'] == 1
             and type(request['arguments']) is dict, 'IPC_REQUEST_SCHEMA')
        method, args = request['method'], request['arguments']
        if method == 'read':
            need(set(args) == {'key', 'scope'}, 'IPC_READ_SCHEMA')
            key = args['key']
            need(key in self.allowed and role in self.allowed[key]['roles']
                 and exact(args['scope'], self.allowed[key]['scope']), 'IPC_WRONG_RAW_KEY_SCOPE_PHASE')
            raw = self.ledger.read(key, args['scope'])
            self.audit.append(dict(role=role, method='read', key=key))
            return {'base64': base64.b64encode(raw).decode()}, True
        if method == 'protected-cas':
            need(role == 'old' and set(args) == {'approval', 'expected_base'}
                 and exact(args['approval'], self.approval)
                 and args['expected_base'] == self.approval['base'], 'IPC_PROTECTED_SUBJECT_NOT_EXACT')
            required = {key for key, record in self.allowed.items() if record.get('before_protected_cas')}
            self.ledger.require_complete({key for key, _ in self.ledger.reads} | required)
            # The actual old launcher validates raw records before requesting;
            # independent raw audit checker must additionally validate their use.
            self.audit.append(dict(role=role, method=method))
            if self.recovery:
                self.recovery.cas_then_interrupt_old(args['expected_base'], self.merge)
                return None, False  # Actual old peer killed/reaped, NO reply.
            self.host.cas(args['expected_base'], self.merge)
            return {'requested': True}, True
        if method == 'ordinary-cas':
            need(role == 'successor' and self.ordinary is not None, 'IPC_ORDINARY_TEST_BOUNDARY_UNBOUND')
            need(set(args) == {'intent', 'expected_base'}, 'IPC_ORDINARY_SCHEMA')
            self.audit.append(dict(role=role, method=method))
            return self.ordinary.cas(args['intent'], args['expected_base']), True
        need(False, 'IPC_METHOD_NOT_ALLOWLISTED')
