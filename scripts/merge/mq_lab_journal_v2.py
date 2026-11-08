"""Inert explicit storage seam; caller holds runner then canonical merge lock.

No CLI, initialization, upgrade authority or migration-on-open. Upgrade support
is storage-only reservation/confirmation/adoption audit; no activation/send API.
"""
from contextlib import closing, contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import stat

import mq_lab_control_authorization as control_authorization


FIELDS = ('number', 'status', 'head', 'base', 'target', 'release_key', 'merge_sha')
V1 = """CREATE TABLE releases (
                number INTEGER PRIMARY KEY CHECK(number > 0),
                status TEXT NOT NULL CHECK(status IN ('reserved', 'confirmed')),
                head TEXT NOT NULL, base TEXT NOT NULL, target TEXT NOT NULL CHECK(target = 'main'),
                release_key TEXT NOT NULL, merge_sha TEXT,
                CHECK((status = 'reserved' AND merge_sha IS NULL) OR
                      (status = 'confirmed' AND merge_sha IS NOT NULL)))"""
ALTER = "ALTER TABLE releases ADD COLUMN mode TEXT NOT NULL DEFAULT 'ordinary' CHECK(mode IN ('ordinary','control-upgrade'))"
BINDINGS = """CREATE TABLE upgrade_bindings (
    upgrade_id TEXT PRIMARY KEY,
    number INTEGER NOT NULL UNIQUE REFERENCES releases(number) ON DELETE RESTRICT,
    binding_sha256 TEXT NOT NULL UNIQUE,
    approval_json TEXT NOT NULL,
    phase TEXT NOT NULL CHECK(phase IN ('reserved','confirmed','adopted')),
    adopted_main TEXT,
    CHECK((phase = 'adopted' AND adopted_main IS NOT NULL AND length(adopted_main) = 40
           AND adopted_main NOT GLOB '*[^0-9a-f]*') OR
          (phase IN ('reserved','confirmed') AND adopted_main IS NULL)))"""


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _sha(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{40}', value) is not None


def _number(value):
    _require(type(value) is int and 0 < value <= 9223372036854775807, 'invalid-number')


def _record(value, status):
    expected = set(FIELDS) - ({'merge_sha'} if status == 'reserved' else set())
    _require(type(value) is dict and set(value) == expected, 'invalid-record-shape')
    _number(value['number'])
    _require(status in ('reserved', 'confirmed') and value['status'] == status
             and _sha(value['head']) and _sha(value['base']) and value['target'] == 'main'
             and type(value['release_key']) is str and bool(value['release_key'].strip())
             and not any(ord(c) < 32 or ord(c) == 127 for c in value['release_key']), 'invalid-record')
    if status == 'confirmed':
        _require(_sha(value['merge_sha']), 'invalid-merge')
    return dict(value)


def _identity(info):
    return info.st_dev, info.st_ino, info.st_uid, info.st_mode, info.st_nlink


class _Storage:
    def __init__(self, path):
        raw = os.fspath(path)
        _require(type(raw) is str and raw and '..' not in raw.split('/'), 'invalid-path')
        self.path = Path(raw)
        _require(self.path.is_absolute() and str(self.path) == raw
                 and self.path.parent.resolve(strict=True) == self.path.parent, 'nonphysical-path')
        parent = self.path.parent.stat()
        _require(stat.S_ISDIR(parent.st_mode) and parent.st_uid == os.geteuid()
                 and stat.S_IMODE(parent.st_mode) == 0o700, 'unsafe-directory')
        self.parent = _identity(parent)
        self.file = _identity(self._regular(self.path))

    @staticmethod
    def _regular(path):
        info = path.lstat()
        _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
                 and info.st_nlink == 1 and stat.S_IMODE(info.st_mode) == 0o600, 'unsafe-file')
        return info

    def check(self):
        _require(self.path.parent.resolve(strict=True) == self.path.parent
                 and _identity(self.path.parent.stat()) == self.parent
                 and _identity(self._regular(self.path)) == self.file, 'storage-identity-moved')
        for suffix in ('-journal', '-wal', '-shm'):
            sidecar = Path(str(self.path) + suffix)
            try:
                self._regular(sidecar)
            except FileNotFoundError:
                continue
            # No WAL or outstanding rollback transaction is admitted at entry.
            _require(suffix == '-journal' and sidecar.stat().st_size == 0, 'outstanding-sidecar')

    @contextmanager
    def connection(self):
        self.check()
        with closing(sqlite3.connect(self.path.as_uri() + '?mode=rw', uri=True, timeout=2)) as db:
            self.check()
            db.enable_load_extension(False)
            db.execute('PRAGMA trusted_schema=OFF')
            _require(db.execute('PRAGMA trusted_schema').fetchone() == (0,), 'trusted-schema-enabled')
            db.execute('PRAGMA foreign_keys=ON')
            _require(db.execute('PRAGMA foreign_keys').fetchone() == (1,), 'foreign-keys-disabled')
            # Inspect rather than silently converting an unknown WAL database.
            _require(db.execute('PRAGMA journal_mode').fetchone()[0].lower() == 'delete', 'unsafe-journal-mode')
            db.execute('PRAGMA synchronous=FULL')
            _require(db.execute('PRAGMA synchronous').fetchone() == (2,), 'unsafe-synchronous')
            yield db
        self.check()

    def sync(self):
        self.check()
        fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW)
        try:
            _require(_identity(os.fstat(fd)) == self.file, 'fsync-identity-moved')
            os.fsync(fd)
        finally:
            os.close(fd)
        fd = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            _require(_identity(os.fstat(fd)) == self.parent, 'fsync-directory-moved')
            os.fsync(fd)
        finally:
            os.close(fd)
        self.check()


def _objects(db):
    return db.execute('SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name').fetchall()


def _expected(version):
    # SQLite itself supplies the exact ALTER serialization and autoindex shape.
    with closing(sqlite3.connect(':memory:')) as db:
        db.execute(V1)
        if version == 2:
            db.execute(ALTER)
            db.execute(BINDINGS)
        return _objects(db)


def _canonical_approval(approval):
    # Snapshot via the one canonical encoding, then reuse the existing complete
    # structural validator. This does not authenticate external proof/reviews.
    text = json.dumps(approval, ensure_ascii=False, sort_keys=True,
                      separators=(',', ':'), allow_nan=False)
    _require(len(text.encode('utf-8')) <= control_authorization.LIMIT, 'upgrade-approval-too-large')
    value = json.loads(text, object_pairs_hook=control_authorization._unique,
                       parse_constant=control_authorization._constant)
    control_authorization._validate(value)
    _number(value['number'])
    raw = json.dumps(dict(version=1, approval=value), ensure_ascii=False,
                     sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')
    return value, text, hashlib.sha256(raw).hexdigest()


def _upgrade_rows(db, rows):
    releases = {row[0]: row for row in rows}
    modes = dict(db.execute('SELECT number,mode FROM releases'))
    _require(all(mode in ('ordinary', 'control-upgrade') for mode in modes.values()), 'invalid-release-mode')
    bound = set()
    for upgrade_id, number, digest, text, phase, adopted in db.execute(
            'SELECT upgrade_id,number,binding_sha256,approval_json,phase,adopted_main FROM upgrade_bindings'):
        _require(type(text) is str and len(text.encode('utf-8')) <= control_authorization.LIMIT,
                 'invalid-stored-upgrade-JSON')
        value = json.loads(text, object_pairs_hook=control_authorization._unique,
                           parse_constant=control_authorization._constant)
        approval, canonical, expected_digest = _canonical_approval(value)
        _require(text == canonical and digest == expected_digest and upgrade_id == approval['upgrade_id']
                 and type(number) is int and number == approval['number'] and number not in bound,
                 'stored-upgrade-binding-mismatch')
        release = releases.get(number)
        _require(phase in ('reserved', 'confirmed', 'adopted') and release is not None,
                 'unknown-upgrade-phase-or-missing-release')
        merge = None if phase == 'reserved' else release[6]
        _require(phase == 'reserved' or _sha(merge), 'invalid-confirmed-upgrade-merge')
        _require(adopted == merge if phase == 'adopted' else adopted is None, 'invalid-adopted-main')
        expected = (number, 'reserved' if phase == 'reserved' else 'confirmed', approval['head'], approval['base'], 'main',
                    'control-upgrade:v1:' + digest, merge)
        # Adoption only audits a confirmed merge; it never changes its identity.
        _require(release == expected
                 and modes.get(number) == 'control-upgrade', 'upgrade-release-correspondence-mismatch')
        bound.add(number)
    _require(bound == {number for number, mode in modes.items() if mode == 'control-upgrade'},
             'unpaired-upgrade-release')


def _schema(db, version):
    _require(db.execute('PRAGMA user_version').fetchone() == (version,), 'unknown-version')
    _require(_objects(db) == _expected(version), 'unknown-schema-or-objects')
    _require(db.execute('PRAGMA integrity_check').fetchall() == [('ok',)], 'integrity-failed')
    _require(db.execute('PRAGMA foreign_key_check').fetchall() == [], 'foreign-key-failed')
    rows = db.execute('SELECT ' + ','.join(FIELDS) + ' FROM releases ORDER BY number').fetchall()
    for row in rows:
        value = dict(zip(FIELDS, row))
        if value['status'] == 'reserved':
            _require(value.pop('merge_sha') is None, 'corrupt-reservation')
        _record(value, value['status'])
    if version == 2:
        _upgrade_rows(db, rows)
    return rows


def _exact_upgrade_readback(approval, readback):
    fields = {'repository', 'repository_id', 'number', 'head', 'base', 'target',
              'merged', 'merge_sha', 'merge_parents', 'target_tip', 'target_contains_merge'}
    _require(type(readback) is dict and set(readback) == fields
             and type(readback['merge_parents']) is list, 'missing-or-unknown-upgrade-readback-fields')
    proof = json.loads(json.dumps(readback, allow_nan=False),
                       object_pairs_hook=control_authorization._unique,
                       parse_constant=control_authorization._constant)
    for key in ('repository', 'repository_id', 'number', 'head', 'base', 'target'):
        _require(type(proof[key]) is type(approval[key]) and proof[key] == approval[key],
                 'upgrade-readback-subject-mismatch')
    _require(proof['merged'] is True and proof['target_contains_merge'] is True
             and _sha(proof['merge_sha']) and _sha(proof['target_tip'])
             and type(proof['merge_parents']) is list
             and proof['merge_parents'] == [approval['base'], approval['head']]
             and proof['merge_sha'] not in (approval['base'], approval['head']), 'upgrade-readback-not-exactly-confirmed')
    # The independently reviewed collector establishes actual remote transport,
    # ordered parents and target ancestry. This predicate never performs I/O.
    return proof


def _exact_adoption(approval, fresh_proof, *, public_successor=False):
    """Validate normalized independent evidence, never collect or authorize."""
    _require(type(public_successor) is bool, 'invalid-adoption-scope')
    shape = control_authorization._shape
    fields = {'repository', 'repository_id', 'upgrade_id', 'number', 'base', 'head', 'target',
              'merge_sha', 'main_before', 'main_after', 'loaded_commit', 'manifest_evidence', 'health'}
    shape(fresh_proof, fields)
    shape(fresh_proof['manifest_evidence'], {'old_manifest', 'new_manifest', 'loaded_manifest', 'source_commit', 'workflow_blob'})
    health = fresh_proof['health']
    shape(health, {'run', 'latest_run_id', 'latest_attempt', 'job', 'artifact', 'payload'})
    shape(health['run'], {'id', 'run_attempt', 'event', 'head_branch', 'head_sha', 'path', 'status', 'conclusion', 'repository'})
    shape(health['run']['repository'], {'id', 'full_name', 'private'})
    shape(health['job'], {'id', 'name', 'head_sha', 'conclusion', 'runner_group_name', 'labels'})
    shape(health['artifact'], {'id', 'name', 'expired', 'workflow_run'})
    shape(health['artifact']['workflow_run'], {'id', 'head_sha', 'repository_id'})
    shape(health['payload'], {'kind', 'event', 'head_sha', 'observed_head', 'run_id', 'run_attempt', 'healthy', 'checks'})
    shape(health['payload']['checks'], {'pin', 'light', 'secret'})
    _require(type(health['job']['labels']) is list, 'invalid-native-health-labels')
    raw = json.dumps(fresh_proof, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False).encode('utf-8')
    _require(len(raw) <= 64 * 1024, 'adoption-proof-too-large')
    proof = json.loads(raw, object_pairs_hook=control_authorization._unique,
                       parse_constant=control_authorization._constant)
    for key in ('repository', 'repository_id', 'upgrade_id', 'number', 'base', 'head', 'target'):
        _require(type(proof[key]) is type(approval[key]) and proof[key] == approval[key], 'adoption-subject-mismatch')
    merge = proof['merge_sha']
    _require(_sha(merge) and all(type(proof[key]) is str and proof[key] == merge
                               for key in ('main_before', 'main_after', 'loaded_commit')),
             'adoption-main-or-loaded-successor-mismatch')
    _require(proof['manifest_evidence'] == dict(old_manifest=approval['old_manifest'], new_manifest=approval['new_manifest'],
             loaded_manifest=approval['new_manifest'], source_commit=approval['proof']['source_commit'],
             workflow_blob=approval['proof']['workflow_blob']), 'adoption-old-verified-manifest-mismatch')
    health = proof['health']
    run, job, artifact, payload = (health[key] for key in ('run', 'job', 'artifact', 'payload'))
    for value in (run['id'], run['run_attempt'], health['latest_run_id'], health['latest_attempt'],
                  job['id'], artifact['id'], artifact['workflow_run']['id'], payload['run_id'], payload['run_attempt']):
        _number(value)
    _require(type(run['repository']['id']) is int and run['repository']['id'] == approval['repository_id']
             and run['repository']['full_name'] == approval['repository'] and run['repository']['private'] is (False if public_successor else True),
             'adoption-native-health-repository-mismatch')
    _require(run['event'] == 'push' and run['head_branch'] == 'main' and run['head_sha'] == merge
             and run['path'] == '.github/workflows/lab-pr-ci.yml' and run['status'] == 'completed'
             and run['conclusion'] == 'success' and health['latest_run_id'] == run['id']
             and health['latest_attempt'] == run['run_attempt'], 'adoption-health-not-latest-exact-success')
    _require(job['name'] == 'Lab main health' and job['head_sha'] == merge and job['conclusion'] == 'success'
             and job['runner_group_name'] == 'GitHub Actions' and job['labels'] == ['ubuntu-24.04'],
             'adoption-native-health-job-unverified')
    workflow_run = artifact['workflow_run']
    _require(artifact['name'] == f"lab-main-health-{merge}-{run['run_attempt']}" and artifact['expired'] is False
             and workflow_run['id'] == run['id'] and workflow_run['head_sha'] == merge
             and type(workflow_run['repository_id']) is int and workflow_run['repository_id'] == approval['repository_id'],
             'adoption-native-health-artifact-mismatch')
    _require(payload['kind'] == 'main-health' and payload['event'] == 'push' and payload['head_sha'] == merge
             and payload['observed_head'] == merge and payload['run_id'] == run['id']
             and payload['run_attempt'] == run['run_attempt'] and payload['healthy'] is True
             and payload['checks'] == dict(pin='success', light='success', secret='success'),
             'adoption-exact-health-payload-required')
    # Latest-run completeness, actual process transition and old-trusted manifest
    # computation are collector duties; matching fields cannot establish truth.
    return proof


def _digest(storage):
    storage.check()
    fd = os.open(storage.path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as stream:
        before = os.fstat(stream.fileno())
        _require(_identity(before) == storage.file, 'digest-identity-moved')
        value = hashlib.file_digest(stream, 'sha256').hexdigest()
        after = os.fstat(stream.fileno())
        _require((before.st_size, before.st_mtime_ns, before.st_ctime_ns)
                 == (after.st_size, after.st_mtime_ns, after.st_ctime_ns), 'snapshot-moved')
    storage.check()
    return value


def _backup(storage, path, rows):
    path = Path(path)
    _require(path.is_absolute() and path.parent == storage.path.parent and path != storage.path,
             'backup-must-be-separate-same-private-directory')
    fd = os.open(path, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    os.close(fd)
    # Failure deliberately preserves evidence; never overwrite or auto-restore.
    backup = _Storage(path)
    with storage.connection() as source, backup.connection() as target:
        source.backup(target)
        _require(_schema(target, 0) == rows, 'backup-rows-differ')
    backup.sync()
    return backup


def migrate_v1(path, backup_path, expected_v1_sha256):
    """Explicit migration only; caller owns outer locks and migration intent.

    Holds never promise rollback after commit was attempted. Backup is retained.
    """
    published = False
    try:
        _require(type(expected_v1_sha256) is str
                 and re.fullmatch('[0-9a-f]{64}', expected_v1_sha256), 'invalid-snapshot-digest')
        storage = _Storage(path)
        with storage.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            try:
                version = db.execute('PRAGMA user_version').fetchone()[0]
                if version == 2:
                    _schema(db, 2)
                    db.rollback()
                    return dict(action='skip', reason='exact-v2-already-present')
                rows = _schema(db, 0)
                _require(_digest(storage) == expected_v1_sha256, 'snapshot-digest-mismatch')
                _backup(storage, backup_path, rows)
                _require(_digest(storage) == expected_v1_sha256, 'snapshot-moved-after-backup')
                db.execute(ALTER)
                db.execute(BINDINGS)
                db.execute('PRAGMA user_version=2')
                _require(_schema(db, 2) == rows, 'migration-changed-identities')
                published = True  # COMMIT failure can have an uncertain outcome.
                db.commit()
            except BaseException:
                db.rollback()
                raise
        storage.sync()
        return dict(action='migrated', reason='exact-v2-and-private-verified-v1-backup-durable')
    except (OSError, ValueError, TypeError, sqlite3.Error):
        return dict(action='hold', reason='unknown-after-commit-attempt' if published else 'migration-refused-no-commit')


class ReleaseJournalV2:
    """Existing-only storage; upgrade reservation is not permission to send."""
    def __init__(self, path):
        self.storage = _Storage(path)
        with self.storage.connection() as db:
            _schema(db, 2)

    @contextmanager
    def _transaction(self):
        with self.storage.connection() as db:
            db.execute('BEGIN IMMEDIATE')
            try:
                _schema(db, 2)
                yield db
                _schema(db, 2)
                db.commit()
            except BaseException:
                db.rollback()
                raise
        self.storage.sync()

    @staticmethod
    def _read(db, number):
        row = db.execute('SELECT ' + ','.join(FIELDS) + ',mode FROM releases WHERE number=?', (number,)).fetchone()
        if row is None:
            return None
        _require(row[-1] == 'ordinary', 'ordinary-adapter-cannot-touch-upgrade')
        value = dict(zip(FIELDS, row[:-1]))
        if value['status'] == 'reserved':
            _require(value.pop('merge_sha') is None, 'corrupt-reservation')
        return _record(value, value['status'])

    def load(self, number):
        _number(number)
        with self.storage.connection() as db:
            db.execute('BEGIN')
            _schema(db, 2)
            result = self._read(db, number)
            db.rollback()
            return result

    def reserve(self, record):
        value = _record(record, 'reserved')
        _require(not value['release_key'].startswith('control-upgrade:'), 'upgrade-namespace-is-not-ordinary')
        with self._transaction() as db:
            if self._read(db, value['number']) is not None:
                return False
            db.execute('INSERT INTO releases (' + ','.join(FIELDS[:-1]) + ') VALUES (?,?,?,?,?,?)',
                       tuple(value[field] for field in FIELDS[:-1]))
        return True

    def confirm(self, record):
        value = _record(record, 'confirmed')
        _require(not value['release_key'].startswith('control-upgrade:'), 'upgrade-namespace-is-not-ordinary')
        expected = {k: v for k, v in value.items() if k != 'merge_sha'}
        expected['status'] = 'reserved'
        with self._transaction() as db:
            if self._read(db, value['number']) != expected:
                return False
            changed = db.execute("""UPDATE releases SET status='confirmed',merge_sha=?
                WHERE number=? AND status='reserved' AND head=? AND base=? AND target=?
                  AND release_key=? AND merge_sha IS NULL AND mode='ordinary'""",
                (value['merge_sha'], value['number'], value['head'], value['base'], value['target'], value['release_key']))
            _require(changed.rowcount == 1, 'reservation-changed')
        return True

    def load_upgrade(self, number):
        """Read an existing complete pair for recovery; never grants send authority."""
        _number(number)
        with self.storage.connection() as db:
            db.execute('BEGIN')
            try:
                _schema(db, 2)
                release = db.execute('SELECT ' + ','.join(FIELDS) + ',mode FROM releases WHERE number=?',
                                     (number,)).fetchone()
                binding = db.execute('SELECT approval_json,phase,adopted_main FROM upgrade_bindings WHERE number=?',
                                     (number,)).fetchone()
                if release is None:
                    _require(binding is None, 'unpaired-upgrade-binding')
                    return None
                _require(release[-1] == 'control-upgrade' and binding is not None,
                         'ordinary-or-unpaired-release-is-not-upgrade')
                approval, text, digest = _canonical_approval(json.loads(binding[0]))
                _require(text == binding[0] and release[5] == 'control-upgrade:v1:' + digest,
                         'invalid-recovery-binding')
                return dict(approval=approval, phase=binding[1], merge_sha=release[6], adopted_main=binding[2])
            finally:
                db.rollback()

    def reserve_upgrade(self, approval):
        """Durably pair immutable rows once; exact repeats are recovery-only.

        Caller holds runner then merge lock and independently verifies actual
        authority. This method has no release/queue/readback/transport callable.
        A HOLD may represent an uncertain commit: never retry a send from it.
        """
        try:
            value, text, digest = _canonical_approval(approval)
            release = (value['number'], 'reserved', value['head'], value['base'], 'main',
                       'control-upgrade:v1:' + digest, None, 'control-upgrade')
            binding = (value['upgrade_id'], value['number'], digest, text, 'reserved', None)
            with self._transaction() as db:
                existing_release = db.execute('SELECT ' + ','.join(FIELDS) + ',mode FROM releases WHERE number=?',
                                              (value['number'],)).fetchone()
                existing_bindings = db.execute('SELECT upgrade_id,number,binding_sha256,approval_json,phase,adopted_main '
                                              'FROM upgrade_bindings WHERE upgrade_id=? OR number=? OR binding_sha256=?',
                                              (value['upgrade_id'], value['number'], digest)).fetchall()
                if existing_release is not None or existing_bindings:
                    same_release = (existing_release is not None
                                    and existing_release[1] in ('reserved', 'confirmed')
                                    and (existing_release[:1] + ('reserved',) + existing_release[2:6]
                                         + (None,) + existing_release[7:]) == release)
                    # _transaction already checked complete phase/merge pairing.
                    if same_release and len(existing_bindings) == 1 and existing_bindings[0][:4] == binding[:4]:
                        return dict(action='recovery-only', reason='exact-upgrade-reservation-already-consumed')
                    return dict(action='hold', reason='upgrade-PR-UUID-or-digest-conflict')
                ordinary_before = db.execute("SELECT * FROM releases WHERE mode='ordinary' ORDER BY number").fetchall()
                db.execute('INSERT INTO releases (' + ','.join(FIELDS) + ',mode) VALUES (?,?,?,?,?,?,?,?)', release)
                db.execute('INSERT INTO upgrade_bindings '
                           '(upgrade_id,number,binding_sha256,approval_json,phase,adopted_main) VALUES (?,?,?,?,?,?)', binding)
                _require(db.execute("SELECT * FROM releases WHERE mode='ordinary' ORDER BY number").fetchall()
                         == ordinary_before, 'upgrade-reservation-changed-ordinary-rows')
                # _transaction revalidates all correspondence before COMMIT,
                # then fsyncs file and parent before this success is returned.
            return dict(action='newly-reserved', reason='exact-paired-upgrade-reservation-durable-storage-only')
        except (OSError, ValueError, TypeError, sqlite3.Error):
            return dict(action='hold', reason='upgrade-reservation-refused-or-commit-unknown')

    def confirm_upgrade(self, approval, readback):
        """Confirm the exact paired reservation from independent readback data.

        No transport, send, queue cleanup, regrant or adoption occurs. Caller
        holds runner/merge locks and independently authenticates collector data.
        A HOLD can mean uncertain durable publication; it never permits resend.
        """
        try:
            value, text, digest = _canonical_approval(approval)
            proof = _exact_upgrade_readback(value, readback)
            reserved = (value['number'], 'reserved', value['head'], value['base'], 'main',
                        'control-upgrade:v1:' + digest, None, 'control-upgrade')
            confirmed = (value['number'], 'confirmed', value['head'], value['base'], 'main',
                         'control-upgrade:v1:' + digest, proof['merge_sha'], 'control-upgrade')
            binding_identity = (value['upgrade_id'], value['number'], digest, text)
            with self._transaction() as db:
                release = db.execute('SELECT ' + ','.join(FIELDS) + ',mode FROM releases WHERE number=?',
                                     (value['number'],)).fetchone()
                binding = db.execute('SELECT upgrade_id,number,binding_sha256,approval_json,phase,adopted_main '
                                     'FROM upgrade_bindings WHERE upgrade_id=?', (value['upgrade_id'],)).fetchone()
                if release == confirmed and binding in (binding_identity + ('confirmed', None),
                                                        binding_identity + ('adopted', proof['merge_sha'])):
                    return dict(action='already-confirmed', reason='same-exact-upgrade-confirmation-no-new-authority')
                _require(release == reserved and binding == binding_identity + ('reserved', None),
                         'upgrade-confirmation-does-not-match-complete-reservation')
                ordinary_before = db.execute("SELECT * FROM releases WHERE mode='ordinary' ORDER BY number").fetchall()
                changed = db.execute("""UPDATE releases SET status='confirmed',merge_sha=?
                    WHERE number=? AND status='reserved' AND head=? AND base=? AND target='main'
                      AND release_key=? AND merge_sha IS NULL AND mode='control-upgrade'""",
                    (proof['merge_sha'], value['number'], value['head'], value['base'], reserved[5]))
                _require(changed.rowcount == 1, 'upgrade-release-confirmation-CAS-failed')
                changed = db.execute("""UPDATE upgrade_bindings SET phase='confirmed'
                    WHERE upgrade_id=? AND number=? AND binding_sha256=? AND approval_json=?
                      AND phase='reserved' AND adopted_main IS NULL""", binding_identity)
                _require(changed.rowcount == 1, 'upgrade-binding-confirmation-CAS-failed')
                _require(db.execute("SELECT * FROM releases WHERE mode='ordinary' ORDER BY number").fetchall()
                         == ordinary_before, 'upgrade-confirmation-changed-ordinary-rows')
                # Full paired validation, COMMIT and fsync happen on context exit.
            return dict(action='confirmed', reason='exact-external-readback-paired-confirmation-durable')
        except (OSError, ValueError, TypeError, sqlite3.Error):
            return dict(action='hold', reason='upgrade-confirmation-refused-or-commit-unknown')

    def adopt_upgrade(self, approval, fresh_proof):
        """Retained private-successor storage seam; not used by public install."""
        return self._adopt_upgrade(approval, fresh_proof, public_successor=False)

    def adopt_public_upgrade(self, approval, fresh_proof):
        """Public-successor audit only; never accepts old private health."""
        return self._adopt_upgrade(approval, fresh_proof, public_successor=True)

    def _adopt_upgrade(self, approval, fresh_proof, *, public_successor):
        """Audit a collector-verified successor; never load/activate any source.

        Caller holds runner then merge lock and authenticates external evidence.
        Only binding.phase/adopted_main change. Unknown publication never grants
        send, re-reservation, source switching, approval or queue cleanup.
        """
        try:
            value, text, digest = _canonical_approval(approval)
            proof = _exact_adoption(value, fresh_proof, public_successor=public_successor)
            merge = proof['merge_sha']
            confirmed = (value['number'], 'confirmed', value['head'], value['base'], 'main',
                         'control-upgrade:v1:' + digest, merge, 'control-upgrade')
            identity = (value['upgrade_id'], value['number'], digest, text)
            with self._transaction() as db:
                release = db.execute('SELECT ' + ','.join(FIELDS) + ',mode FROM releases WHERE number=?',
                                     (value['number'],)).fetchone()
                binding = db.execute('SELECT upgrade_id,number,binding_sha256,approval_json,phase,adopted_main '
                                     'FROM upgrade_bindings WHERE upgrade_id=?', (value['upgrade_id'],)).fetchone()
                _require(release == confirmed, 'adoption-requires-exact-confirmed-release')
                if binding == identity + ('adopted', merge):
                    return dict(action='already-adopted', reason='same-exact-adoption-audit-no-new-authority')
                _require(binding == identity + ('confirmed', None), 'adoption-requires-complete-confirmed-binding')
                releases_before = db.execute('SELECT * FROM releases ORDER BY number').fetchall()
                changed = db.execute("""UPDATE upgrade_bindings SET phase='adopted',adopted_main=?
                    WHERE upgrade_id=? AND number=? AND binding_sha256=? AND approval_json=?
                      AND phase='confirmed' AND adopted_main IS NULL
                      AND EXISTS (SELECT 1 FROM releases WHERE number=? AND status='confirmed'
                          AND head=? AND base=? AND target='main' AND release_key=?
                          AND merge_sha=? AND mode='control-upgrade')""",
                    (merge, *identity, value['number'], value['head'], value['base'], confirmed[5], merge))
                _require(changed.rowcount == 1, 'adoption-binding-CAS-failed')
                _require(db.execute('SELECT * FROM releases ORDER BY number').fetchall() == releases_before,
                         'adoption-modified-release-records')
                # Exact adopted/confirmed correspondence is rechecked, committed
                # and fsynced by _transaction before success can be returned.
            return dict(action='adopted', reason='exact-successor-manifest-health-adoption-audit-durable')
        except (OSError, ValueError, TypeError, sqlite3.Error):
            return dict(action='hold', reason='upgrade-adoption-refused-or-commit-unknown')
