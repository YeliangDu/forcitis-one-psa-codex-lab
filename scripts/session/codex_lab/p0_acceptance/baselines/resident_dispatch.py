"""Private advisory dispatch records. No App, network, Git or send capability.

Registry/event authority must be verified by the outer collector. A prepared
send-once string is advice, never an atomic dispatch claim or permission to send.
Same-UID cooperative directory ownership is required; this is not a sandbox.
claim_send durably fences a single initial dispatch; it does not perform one.
"""
from contextlib import contextmanager
import copy
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import tempfile
import uuid


ORIGIN = 'git@github.com:YeliangDu/forcitis-one-psa-codex-lab.git'
LIMIT = 65536
ASSIGNMENT = {'issue', 'role', 'thread_id', 'cwd', 'base', 'head', 'phase',
              'run_id', 'run_attempt', 'handoff'}
FIELDS = {'generation', 'revision', 'assignment', 'state', 'next_action',
          'turn_id', 'terminal_turn', 'resume_claimed', 'delivery_sha256', 'last_event', 'send_claim'}
STATES = {'prepared', 'claimed', 'pending', 'unknown', 'active', 'accepted', 'compacted',
          'terminal', 'completed', 'held'}


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _positive(value):
    return type(value) is int and 0 < value <= 2**63 - 1


def _uuid(value):
    _require(type(value) is str and str(uuid.UUID(value)) == value
             and uuid.UUID(value).variant == uuid.RFC_4122, 'invalid-native-UUID')


def _sha(value, length=40):
    return type(value) is str and re.fullmatch('[0-9a-f]{' + str(length) + '}', value) is not None


def _text(value):
    return type(value) is str and 0 < len(value) <= 4096 and not any(ord(c) < 32 or ord(c) == 127 for c in value)


def _physical(value, *, private=False):
    raw = os.fspath(value)
    _require(type(raw) is str and raw and '..' not in raw.split('/'), 'invalid-physical-path')
    path = Path(raw)
    _require(path.is_absolute() and str(path) == raw and path.resolve(strict=True) == path,
             'nonphysical-directory')
    info = path.stat()
    _require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.geteuid()
             and (stat.S_IMODE(info.st_mode) == 0o700 if private else not info.st_mode & 0o022),
             'unsafe-directory')
    return path, (info.st_dev, info.st_ino)


def _file(info):
    _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
             and info.st_nlink == 1 and stat.S_IMODE(info.st_mode) == 0o600, 'unsafe-state-file')


def _identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_ctime_ns


def _pairs(pairs):
    value = {}
    for key, item in pairs:
        _require(key not in value, 'duplicate-JSON-key')
        value[key] = item
    return value


def _constant(_value):
    raise ValueError('nonfinite-JSON')


class ResidentDispatchStore:
    def __init__(self, path, registry, lab_root, common_dir):
        self.root, self.root_id = _physical(lab_root)
        self.common, self.common_id = _physical(common_dir)
        _require(self.root.name == 'forcitis-one-psa-codex-lab'
                 and self.common == self.root / '.git', 'wrong-Lab-identity')
        raw = os.fspath(path)
        self.path = Path(raw)
        _require(type(raw) is str and self.path.is_absolute() and str(self.path) == raw
                 and '..' not in self.path.parts, 'invalid-state-path')
        self.parent, self.parent_id = _physical(self.path.parent, private=True)
        _require(self.path.is_relative_to(self.root), 'state-outside-Lab')
        self.registry = copy.deepcopy(registry)
        self._scope()

    def _scope(self):
        for path, identity, private in ((self.root, self.root_id, False),
                                       (self.common, self.common_id, False),
                                       (self.parent, self.parent_id, True)):
            _require(_physical(path, private=private)[1] == identity, 'directory-identity-moved')
        _require(type(self.registry) is dict and self.registry, 'missing-verified-registry')
        ids = set()
        for role, entry in self.registry.items():
            _require(_text(role) and type(entry) is dict
                     and set(entry) == {'thread_id', 'cwd', 'common_dir', 'fetch_origin', 'push_origin'},
                     'invalid-registry-schema')
            _uuid(entry['thread_id'])
            _require(entry['thread_id'] not in ids and entry['common_dir'] == str(self.common)
                     and entry['fetch_origin'] == ORIGIN and entry['push_origin'] == ORIGIN,
                     'unverified-resident-Lab-identity')
            ids.add(entry['thread_id'])
            _physical(entry['cwd'])

    def _assignment(self, value):
        _require(type(value) is dict and set(value) == ASSIGNMENT, 'invalid-assignment-schema')
        _require(_positive(value['issue']) and _positive(value['run_id'])
                 and _positive(value['run_attempt']) and _sha(value['base']) and _sha(value['head'])
                 and _text(value['phase']) and _text(value['role']), 'invalid-exact-assignment')
        _uuid(value['thread_id'])
        registered = self.registry.get(value['role'])
        _require(registered is not None and registered['thread_id'] == value['thread_id']
                 and registered['cwd'] == value['cwd'], 'resident-identity-rebind-refused')
        _physical(value['cwd'])
        relative = value['handoff']
        _require(_text(relative) and not PurePosixPath(relative).is_absolute()
                 and str(PurePosixPath(relative)) == relative
                 and '..' not in PurePosixPath(relative).parts and '\\' not in relative,
                 'unsafe-relative-handoff')

    def _validate(self, value):
        # Existing advisory records remain readable without silently upgrading
        # them or treating an old active/unknown record as unclaimed permission.
        _require(type(value) is dict and set(value) in (FIELDS, FIELDS - {'send_claim'}), 'invalid-state-schema')
        _require(_positive(value['generation']) and _positive(value['revision'])
                 and value['revision'] >= value['generation'] and type(value['state']) is str and value['state'] in STATES
                 and type(value['resume_claimed']) is bool, 'invalid-state-values')
        self._assignment(value['assignment'])
        claim = value.get('send_claim')
        if claim is not None:
            _require(type(claim) is dict and set(claim) == {'claim_id', 'generation', 'thread_id'},
                     'invalid-send-claim-schema')
            _uuid(claim['claim_id'])
            _require(type(claim['generation']) is int and claim['generation'] == value['generation']
                     and claim['thread_id'] == value['assignment']['thread_id']
                     and value['state'] != 'prepared', 'send-claim-identity-mismatch')
        _require(value['state'] != 'claimed' or claim is not None, 'missing-send-claim')
        for field in ('turn_id', 'terminal_turn'):
            if value[field] is not None:
                _uuid(value[field])
        if value['terminal_turn'] is not None:
            _require(value['terminal_turn'] == value['turn_id'], 'terminal-handle-mismatch')
        expected = ('send-once' if value['state'] == 'prepared' else 'none'
                    if value['state'] in ('completed', 'held') else 'resume-same-thread'
                    if value['state'] == 'terminal' and not value['resume_claimed'] else 'observe')
        _require(value['next_action'] == expected, 'inconsistent-advisory-action')
        _require((value['state'] == 'completed' and _sha(value['delivery_sha256'], 64))
                 or (value['state'] != 'completed' and value['delivery_sha256'] is None), 'invalid-delivery-state')
        _require(value['state'] != 'terminal' or value['terminal_turn'] is not None, 'missing-terminal-proof')
        last = value['last_event']
        if last is not None:
            _require(type(last) is dict and set(last) == {'kind', 'turn_id', 'reason'}
                     and type(last['kind']) is str and last['kind'] in
                     {'send-accepted', 'send-pending', 'send-unknown', 'turn-active', 'assignment-accepted',
                      'contextCompaction', 'turn-terminal', 'verified-handoff', 'explicit-hold', 'resume-claimed', 'send-claimed'},
                     'invalid-persisted-event')
            if last['kind'] == 'send-claimed':
                _require(value['state'] == 'claimed' and last['turn_id'] is None, 'invalid-claim-event')
            else:
                _uuid(last['turn_id'])
            _require(last['turn_id'] == value['turn_id'] and
                     (_text(last['reason']) if last['kind'] == 'explicit-hold' else last['reason'] is None),
                     'inconsistent-persisted-event')
        _require((value['state'] == 'prepared') == (last is None), 'missing-persisted-event')
        return value

    def _read(self):
        self._scope()
        try:
            fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        except FileNotFoundError:
            return None
        with os.fdopen(fd, 'rb') as stream:
            before = os.fstat(stream.fileno())
            _file(before)
            _require(0 < before.st_size <= LIMIT, 'state-too-large')
            raw = stream.read(LIMIT + 1)
            _require(len(raw) <= LIMIT and _identity(before) == _identity(os.fstat(stream.fileno()))
                     and _identity(before) == _identity(self.path.lstat()), 'state-changed-during-read')
        return self._validate(json.loads(raw.decode('utf-8'), object_pairs_hook=_pairs, parse_constant=_constant))

    @contextmanager
    def _lock(self):
        self._scope()
        lock_path = self.path.with_name(self.path.name + '.lock')
        fd = os.open(lock_path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        try:
            _file(os.fstat(fd))
            fcntl.flock(fd, fcntl.LOCK_EX)
            _require(_identity(os.fstat(fd)) == _identity(lock_path.lstat()), 'lock-inode-moved')
            self._scope()
            yield
        finally:
            os.close(fd)

    def _write(self, value):
        self._validate(value)
        raw = (json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False) + '\n').encode('utf-8')
        _require(len(raw) <= LIMIT, 'state-too-large')
        self._scope()
        fd, name = tempfile.mkstemp(prefix='.' + self.path.name + '-', dir=self.parent)
        try:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(raw)
                stream.flush()
                os.fsync(stream.fileno())
            self._scope()
            os.replace(name, self.path)
            parent = os.open(self.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                _require((os.fstat(parent).st_dev, os.fstat(parent).st_ino) == self.parent_id,
                         'publication-directory-moved')
                os.fsync(parent)
            finally:
                os.close(parent)
        finally:
            try:
                os.unlink(name)
            except FileNotFoundError:
                pass
        return copy.deepcopy(value)

    def read(self):
        return self._read()

    def _cas(self, generation, revision):
        _require(type(revision) is int and revision >= 0, 'invalid-revision')
        current = self._read()
        _require(current is not None and current['revision'] == revision
                 and type(generation) is int and current['generation'] == generation, 'stale-dispatch-CAS')
        return current

    def prepare(self, assignment, expected_revision):
        with self._lock():
            self._assignment(assignment)
            current = self._read()
            _require(type(expected_revision) is int and expected_revision >= 0
                     and (current['revision'] if current else 0) == expected_revision, 'stale-dispatch-CAS')
            if current is not None:
                _require(all(current['assignment'][key] == assignment[key] for key in ('role', 'thread_id', 'cwd')),
                         'resident-replacement-refused')
                if current['assignment'] == assignment:
                    return current  # Repeated prepare must not advise another send.
                _require(current['state'] == 'completed' or
                         (current['state'] == 'prepared' and current.get('send_claim') is None
                          and current['turn_id'] is None), 'unfinished-dispatch-cannot-be-superseded')
            return self._write(dict(generation=current['generation'] + 1 if current else 1,
                                    revision=expected_revision + 1, assignment=copy.deepcopy(assignment),
                                    state='prepared', next_action='send-once', turn_id=None,
                                    terminal_turn=None, resume_claimed=False, delivery_sha256=None, last_event=None,
                                    send_claim=None))

    def claim_send(self, generation, claim_id, expected_revision):
        """Consume the sole initial claim before external dispatch, never retry.

        Only a successful durable return yields send-claimed. Any exception
        after publication is ambiguous: reread this generation, never resend.
        A claimed generation without a native handle is already fenced.
        """
        with self._lock():
            current = self._cas(generation, expected_revision)
            _uuid(claim_id)
            if (current.get('send_claim') is not None or current['state'] != 'prepared'
                    or current['turn_id'] is not None):
                return dict(action='observe', thread_id=current['assignment']['thread_id'])
            claim = dict(claim_id=claim_id, generation=generation,
                         thread_id=current['assignment']['thread_id'])
            current.update(send_claim=claim, state='claimed', next_action='observe',
                           revision=current['revision'] + 1,
                           last_event=dict(kind='send-claimed', turn_id=None, reason=None))
            self._write(current)
            return dict(action='send-claimed', **claim)

    def _handoff(self, current, witness):
        expected = dict(current['assignment'], generation=current['generation'])
        if type(witness) is not dict or set(witness) != set(expected) | {'sha256'}:
            return False
        if any(witness[key] != value or type(witness[key]) is not type(value) for key, value in expected.items()):
            return False
        if not _sha(witness['sha256'], 64):
            return False
        path = self.root / current['assignment']['handoff']
        try:
            _require(path.resolve(strict=True) == path and path.is_relative_to(self.root), 'unsafe-handoff-path')
            for parent in path.parents:
                _physical(parent)
                if parent == self.root:
                    break
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            with os.fdopen(fd, 'rb') as stream:
                before = os.fstat(stream.fileno())
                _require(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
                         and before.st_nlink == 1 and not before.st_mode & 0o022
                         and before.st_size <= 16 * 1024 * 1024, 'unsafe-artifact')
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                _require(_identity(before) == _identity(os.fstat(stream.fileno()))
                         and _identity(before) == _identity(path.lstat()), 'artifact-moved')
            return digest == witness['sha256']
        except (OSError, ValueError):
            return False

    def record(self, generation, event, expected_revision):
        with self._lock():
            current = self._cas(generation, expected_revision)
            _require(type(event) is dict, 'invalid-event')
            kind = event.get('kind')
            extras = {'assignment-accepted': {'issue', 'phase'}, 'turn-terminal': {'text', 'terminal'},
                      'verified-handoff': {'witness'}, 'merge-success': {'text'}, 'explicit-hold': {'reason'}}
            claim = current.get('send_claim')
            claim_fields = {'claim_id'} if claim is not None else set()
            _require(type(kind) is str and kind in set(extras) | {'send-accepted', 'send-pending', 'send-unknown', 'turn-active', 'contextCompaction'}
                     and set(event) == {'kind', 'thread_id', 'turn_id'} | extras.get(kind, set()) | claim_fields,
                     'invalid-event-schema')
            if claim is not None:
                _require(event['claim_id'] == claim['claim_id'], 'wrong-send-claim')
            _uuid(event['turn_id'])
            _require(event['thread_id'] == current['assignment']['thread_id'], 'wrong-event-resident')
            if current['turn_id'] is not None and event['turn_id'] != current['turn_id']:
                _require(claim is None and current['state'] == 'terminal' and current['resume_claimed']
                         and kind == 'send-accepted', 'wrong-native-turn')
            if kind == 'assignment-accepted':
                _require(type(event['issue']) is int and event['issue'] == current['assignment']['issue']
                         and event['phase'] == current['assignment']['phase'], 'wrong-accepted-assignment')
            if kind == 'turn-terminal':
                _require(event['terminal'] is True and type(event['text']) is str
                         and len(event['text']) <= 16384, 'not-authoritative-terminal')
            if kind == 'explicit-hold':
                _require(_text(event['reason']), 'missing-explicit-hold-reason')
            if kind == 'merge-success':
                _require(type(event['text']) is str and len(event['text']) <= 16384, 'invalid-claim-text')
            if current['state'] in ('completed', 'held'):
                return current
            value = copy.deepcopy(current)
            value['turn_id'] = event['turn_id']
            value['terminal_turn'] = None
            value['last_event'] = dict(kind=kind, turn_id=event['turn_id'], reason=event.get('reason'))
            if kind == 'verified-handoff':
                if not self._handoff(current, event['witness']):
                    return current
                value.update(state='completed', next_action='none', delivery_sha256=event['witness']['sha256'])
            elif kind == 'merge-success':
                return current  # A native self-assertion is never external proof.
            elif kind == 'explicit-hold':
                value.update(state='held', next_action='none')
            elif kind == 'turn-terminal':
                value.update(state='terminal', terminal_turn=event['turn_id'],
                             next_action='observe' if value['resume_claimed'] else 'resume-same-thread')
            else:
                states = {'send-accepted': 'active', 'send-pending': 'pending', 'send-unknown': 'unknown',
                          'turn-active': 'active', 'assignment-accepted': 'accepted', 'contextCompaction': 'compacted'}
                value.update(state=states[kind], next_action='observe')
            value['revision'] += 1
            return self._write(value)

    def claim_resume(self, generation, terminal_turn, expected_revision):
        with self._lock():
            current = self._cas(generation, expected_revision)
            _uuid(terminal_turn)
            action = 'observe'
            if (current['state'] == 'terminal' and current['terminal_turn'] == terminal_turn
                    and not current['resume_claimed']):
                current.update(resume_claimed=True, next_action='observe', revision=current['revision'] + 1)
                current['last_event'] = dict(kind='resume-claimed', turn_id=terminal_turn, reason=None)
                self._write(current)
                action = 'resume-same-thread'
            return dict(action=action, thread_id=current['assignment']['thread_id'])
