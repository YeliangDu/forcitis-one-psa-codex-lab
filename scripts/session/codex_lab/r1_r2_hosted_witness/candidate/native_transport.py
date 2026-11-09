"""Bounded existing-proxy RPC adapter, derived from the real pilot client.

No new server/thread, resume, turn/start, auth refresh or configuration RPC.
Dynamic tool handling is internal to this live channel, never a CLI mock route.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import select
import struct
import subprocess
import time

MAX_FRAME = 2 * 1024 * 1024


class NativeProxy:
    def __init__(self, installation, record, native_call):
        self.cfg, self.record, self.native_call = installation, record, native_call
        self.process = None
        self.buffer = bytearray()
        self.counter = 0
        self.connection_id = None

    def connect(self):
        if self.process is not None:
            raise ValueError('native-connection-already-open')
        endpoint = self.check_endpoint()
        binary = self.cfg['proxy_binary']
        if not Path(binary).is_absolute() or not Path(binary).is_file():
            raise ValueError('approved-proxy-binary-missing')
        self.connection_id = os.urandom(24).hex()
        self.process = subprocess.Popen([binary, 'app-server', 'proxy', '--sock', str(endpoint)],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, bufsize=0)
        self.deadline = time.monotonic() + 15
        try:
            self.check_endpoint()  # Recheck named identities before handshake.
            key = base64.b64encode(os.urandom(16)).decode()
            self._write(('GET /rpc HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\n'
                         'Upgrade: websocket\r\nSec-WebSocket-Version: 13\r\n'
                         'Sec-WebSocket-Key: ' + key + '\r\n\r\n').encode())
            while b'\r\n\r\n' not in self.buffer:
                self._more()
                if len(self.buffer) > 32768:
                    raise ValueError('oversize-native-upgrade')
            size = self.buffer.index(b'\r\n\r\n') + 4
            lines = self._take(size).decode('latin1').split('\r\n')
            expected = base64.b64encode(hashlib.sha1(
                (key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
            if lines[0].split()[1] != '101' or not any(
                line.lower().startswith('sec-websocket-accept:') and line.split(':', 1)[1].strip() == expected
                for line in lines):
                raise ValueError('native-upgrade-not-authentic-to-request')
            self.check_endpoint()
            result = self.rpc('initialize', dict(clientInfo=dict(name='lab-r1-r2-broker', version='1'),
                                                 capabilities=dict(experimentalApi=True)), initializing=True)
            self._send(dict(method='initialized'))
            return result
        except BaseException:
            self.close()
            raise

    def check_endpoint(self):
        import stat
        pin = json.loads(Path(__file__).with_name('pilot_endpoint_pin.json').read_bytes())
        alias = Path('/private/tmp/lab60-pilot.cnCGuJHo/server.sock')
        if self.cfg['native_endpoint'] not in (str(alias), '/tmp/lab60-pilot.cnCGuJHo/server.sock'):
            raise ValueError('only-existing-pilot-alias-allowed')
        target = Path(pin['link_text'])
        if (pin['alias']['path'] != str(alias)
                or target.parent != Path('/private/tmp/codex-daemon-' + str(os.geteuid()))
                or not target.is_absolute() or target.resolve(strict=True) != target
                or target.parent.resolve(strict=True) != target.parent
                or alias.parent.resolve(strict=True) != alias.parent):
            raise ValueError('pilot-endpoint-path-mismatch')
        for key, path, kind, private_mode in (
            ('alias', alias, stat.S_IFLNK, 0o700),
            ('alias_parent', alias.parent, stat.S_IFDIR, 0o700),
            ('target', target, stat.S_IFSOCK, 0o600),
            ('target_parent', target.parent, stat.S_IFDIR, 0o700)):
            info = path.lstat()
            actual = dict(path=str(path), dev=info.st_dev, ino=info.st_ino, uid=info.st_uid,
                          mode=stat.S_IMODE(info.st_mode), kind=stat.S_IFMT(info.st_mode))
            if (actual != pin[key] or info.st_uid != os.geteuid()
                    or stat.S_IFMT(info.st_mode) != kind or stat.S_IMODE(info.st_mode) != private_mode):
                raise ValueError('pilot-endpoint-identity-changed')
        if os.readlink(alias) != pin['link_text'] or alias.resolve(strict=True) != target:
            raise ValueError('pilot-alias-target-moved')
        return target

    def _write(self, data):
        offset = 0
        while offset < len(data):
            count = os.write(self.process.stdin.fileno(), data[offset:])
            if count <= 0:
                raise EOFError('native-pipe-write-failed')
            offset += count

    def _more(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0 or not select.select([self.process.stdout], [], [], remaining)[0]:
            raise TimeoutError('native-read-deadline')
        data = os.read(self.process.stdout.fileno(), 8192)
        if not data:
            raise EOFError('native-proxy-closed')
        self.buffer.extend(data)
        if len(self.buffer) > MAX_FRAME + 32768:
            raise ValueError('native-buffer-bound')

    def _take(self, size):
        while len(self.buffer) < size:
            self._more()
        result = bytes(self.buffer[:size])
        del self.buffer[:size]
        return result

    def _send(self, value):
        self._frame(1, json.dumps(value, separators=(',', ':'), allow_nan=False).encode())

    def _frame(self, opcode, payload):
        if len(payload) > MAX_FRAME:
            raise ValueError('native-request-bound')
        mask = os.urandom(4)
        size = len(payload)
        prefix = bytes([128 | size]) if size < 126 else (
            bytes([254]) + struct.pack('!H', size) if size < 65536 else bytes([255]) + struct.pack('!Q', size))
        self._write(bytes([128 | opcode]) + prefix + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(payload)))

    def _message(self):
        data = bytearray()
        first = True
        while True:
            a, b = self._take(2)
            opcode, size = a & 15, b & 127
            if b & 128 or a & 112:
                raise ValueError('invalid-server-frame')
            if size >= 126:
                size = int.from_bytes(self._take(2 if size == 126 else 8), 'big')
            if size > MAX_FRAME:
                raise ValueError('native-frame-bound')
            value = self._take(size)
            if opcode == 8:
                raise EOFError('native-websocket-closed')
            if opcode in (9, 10):
                if not a & 128 or size > 125:
                    raise ValueError('invalid-native-heartbeat')
                if opcode == 9:
                    self._frame(10, value)
                continue
            if opcode != (1 if first else 0):
                raise ValueError('invalid-native-fragment')
            first = False
            data.extend(value)
            if len(data) > MAX_FRAME:
                raise ValueError('native-message-bound')
            if a & 128:
                raw = bytes(data)
                return raw, json.loads(raw.decode('utf-8'))

    def rpc(self, method, params, *, initializing=False):
        allowed = {'thread/loaded/list', 'thread/read', 'thread/goal/get'}
        if not (initializing and method == 'initialize') and method not in allowed:
            raise ValueError('native-rpc-not-in-readonly-capability')
        if method.startswith('thread/') and method != 'thread/loaded/list':
            if params.get('threadId') != self.cfg['native_session']:
                raise ValueError('native-session-not-bound')
        self.counter += 1
        request_id = self.counter
        request = dict(id=request_id, method=method, params=params)
        self._send(request)
        self.deadline = time.monotonic() + 15
        for _ in range(512):
            raw, message = self._message()
            self.record(dict(connection_id=self.connection_id, request=request,
                             raw_utf8=raw.decode(), raw_sha256=hashlib.sha256(raw).hexdigest()))
            if message.get('id') == request_id and 'method' not in message:
                if 'error' in message:
                    raise ValueError('native-read-rpc-refused')
                return message['result']
            if 'method' in message and 'id' in message:
                # Unknown approvals are never granted. Dynamic-call identity is
                # supplied by this live connection, not caller-controlled CLI JSON.
                answer = self.native_call(self, message)
                if answer is not None:
                    self._send(dict(id=message['id'], result=answer))
        raise ValueError('native-observation-window-bound')

    def inspect(self):
        loaded = self.rpc('thread/loaded/list', {})
        thread = self.rpc('thread/read', dict(threadId=self.cfg['native_session'], includeTurns=False))
        return dict(loaded=loaded, thread=thread, connection_id=self.connection_id,
                    execution_capability='hold-no-bound-dynamic-tools')

    def close(self):
        if self.process is not None:
            if self.process.stdin:
                self.process.stdin.close()
            try:
                self.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.process.terminate()  # Only this broker-created proxy, never its native host.
                self.process.wait(timeout=3)
            self.process = None
