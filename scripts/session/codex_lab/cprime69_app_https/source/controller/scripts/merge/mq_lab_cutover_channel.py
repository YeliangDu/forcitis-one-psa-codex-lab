"""Explicit hosted scratch IPC; no default network, repository or write port.

The parent test owns the AF_UNIX server and its raw ledger/CAS implementation.
This client cannot update a Git ref. A lost reply raises, never reconnects/retries.
"""
import base64
import json
import os
import re
from pathlib import Path
import socket
import stat
import time

LIMIT = 4 * 1024 * 1024


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate-channel-key')
        result[key] = value
    return result


def decode(raw):
    return json.loads(raw, object_pairs_hook=unique,
                      parse_constant=lambda value: invalid(value))


def invalid(value):
    raise ValueError('nonfinite-channel-JSON')


class ScratchChannel:
    def __init__(self, root, socket_path):
        self.root, self.path = Path(root), Path(socket_path)
        if (os.environ.get('GITHUB_ACTIONS') != 'true'
                or not self.root.is_absolute() or self.root.resolve(strict=True) != self.root
                or self.root.name != 'forcitis-one-psa-codex-lab'
                or not self.path.is_absolute() or self.path.resolve(strict=True) != self.path
                or not self.path.is_relative_to(self.root)):
            raise ValueError('hosted-physical-scratch-channel-required')
        info = self.path.lstat()
        if not stat.S_ISSOCK(info.st_mode) or info.st_uid != os.geteuid() or stat.S_IMODE(info.st_mode) & 0o077:
            raise ValueError('test-owned-private-unix-socket-required')
        self.identity = info.st_dev, info.st_ino
        # Supervisor registers this actual Popen PID with SO_PEERCRED receiver,
        # THEN publishes its private ready marker. This is only a start barrier;
        # socket authority still comes from the parent's actual OS peer check.
        ready = self.root / 'child-ready' / (str(os.getpid()) + '.json')
        deadline = time.monotonic() + 10
        while True:
            try:
                fd = os.open(ready, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            except FileNotFoundError:
                if time.monotonic() >= deadline:
                    raise ValueError('test-owned-peer-registration-barrier-unbound')
                time.sleep(0.005)
                continue
            with os.fdopen(fd, 'rb') as stream:
                info = os.fstat(stream.fileno())
                if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                        or info.st_nlink != 1 or stat.S_IMODE(info.st_mode) != 0o600
                        or info.st_size > 256):
                    raise ValueError('unsafe-child-ready-marker')
                value = decode(stream.read(257))
            if type(value) is not dict or set(value) != {'pid'} or type(value['pid']) is not int or value['pid'] != os.getpid():
                raise ValueError('child-ready-marker-not-exact-PID')
            break

    def call(self, method, **arguments):
        if method not in ('read', 'protected-cas', 'ordinary-cas'):
            raise ValueError('channel-method-not-allowlisted')
        current = self.path.lstat()
        if (current.st_dev, current.st_ino) != self.identity:
            raise ValueError('test-owned-socket-moved')
        raw = json.dumps(dict(version=1, method=method, arguments=arguments),
                         allow_nan=False, separators=(',', ':')).encode() + b'\n'
        if len(raw) > LIMIT:
            raise ValueError('oversize-channel-request')
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(20)
            connection.connect(str(self.path))
            connection.sendall(raw)
            with connection.makefile('rb') as stream:
                answer = stream.readline(LIMIT + 1)
        if not answer.endswith(b'\n') or len(answer) > LIMIT:
            raise ValueError('missing-or-oversize-channel-reply')
        value = decode(answer)
        if type(value) is not dict or set(value) != {'version', 'result'} or type(value['version']) is not int or value['version'] != 1:
            raise ValueError('invalid-channel-reply')
        return value['result']

    def ordinary_cas(self, intent, *, expected_base, merge_sha):
        if (type(intent) is not dict or set(intent) != {'number', 'head', 'base', 'target'}
                or type(intent['number']) is not int or intent['number'] <= 0
                or intent['target'] != 'main' or expected_base != intent['base']
                or any(type(intent[key]) is not str or re.fullmatch('[0-9a-f]{40}', intent[key]) is None
                       for key in ('head', 'base'))
                or type(merge_sha) is not str or re.fullmatch('[0-9a-f]{40}', merge_sha) is None
                or merge_sha in (intent['base'], intent['head'])):
            raise ValueError('ordinary-cas-subject-not-exact')
        # Separate method/namespace: the parent must check the ordinary V2 row,
        # actual new base and ordinary parents. Never call HostProbe.cas(), which
        # is intentionally restricted to the single protected O/H transition.
        return self.call('ordinary-cas', intent=intent, expected_base=expected_base, merge_sha=merge_sha)

    def read(self, key, scope):
        value = self.call('read', key=key, scope=scope)
        if type(value) is not dict or set(value) != {'base64'} or type(value['base64']) is not str:
            raise ValueError('raw-ledger-bytes-required')
        return base64.b64decode(value['base64'], validate=True)
