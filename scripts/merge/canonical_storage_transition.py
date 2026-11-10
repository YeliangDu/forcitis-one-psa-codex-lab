"""Explicit canonical V1 preparation over an independently selected scope port.

No CLI/default scope, source activation, queue admission, install or send exists.
This file does not authenticate the injected policy port. After any publication,
failures retain evidence; canonical DB/lock inodes are never copied back/replaced.
"""
from contextlib import closing, ExitStack
import copy
import fcntl
import hashlib
import os
from pathlib import Path
import re
import secrets
import sqlite3
import stat
import uuid

import mq_lab_journal_v2 as reviewed


LOCKS = ('control-upgrade.runner-lock', 'merge.queue.merge-lock',
         'merge.queue.lock', 'merge.queue.heads-lock')
FIELDS = {'version', 'kind', 'intent_id', 'repository', 'repository_id', 'common_dir',
          'common_identity', 'queue_directory_identity', 'journal_identity',
          'reviewed_v1_sha256', 'backup_path', 'expected_user_version',
          'expected_confirmed_rows', 'expected_fifo_sha256', 'expected_heads_sha256',
          'lock_identities', 'permitted_action'}
EMPTY = hashlib.sha256(b'').hexdigest()
LIMIT = 16 * 1024 * 1024
DIR_FLAGS = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
FILE_FLAGS = os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _result(action, phase):
    return dict(action=action, phase=phase, allows_new_write=False, installation_authority=False)


def _path(value):
    raw = os.fspath(value)
    _require(type(raw) is str and raw and '..' not in raw.split('/'), 'invalid-path')
    path = Path(raw)
    _require(path.is_absolute() and str(path) == raw, 'noncanonical-absolute-path')
    return path


def _id(info):
    return dict(device=info.st_dev, inode=info.st_ino, uid=info.st_uid)


def _pin(value):
    _require(type(value) is dict and set(value) == {'device', 'inode', 'uid'}
             and all(type(v) is int and v >= 0 for v in value.values())
             and value['inode'] > 0 and value['uid'] == os.geteuid(), 'invalid-identity-pin')


def _binding(scope, common, backup, digest):
    _require(scope is not None and type(scope) not in (dict, bool)
             and callable(getattr(scope, 'read_preparation_binding', None)), 'missing-reviewed-preparation-port')
    value = copy.deepcopy(scope.read_preparation_binding())
    _require(type(value) is dict and set(value) == FIELDS, 'invalid-preparation-binding-shape')
    _require(type(value['version']) is int and value['version'] == 1
             and value['kind'] == 'canonical-storage-preparation'
             and value['repository'] == 'YeliangDu/forcitis-one-psa-codex-lab'
             and type(value['repository_id']) is int and value['repository_id'] == 1449415666
             and value['permitted_action'] == 'prepare-storage-only', 'wrong-preparation-subject')
    intent = value['intent_id']
    _require(type(intent) is str and re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', intent), 'invalid-intent-id')
    parsed = uuid.UUID(intent)
    _require(str(parsed) == intent and parsed.variant == uuid.RFC_4122 and parsed.version in range(1, 9), 'invalid-intent-UUID')
    _require(value['common_dir'] == str(common) and value['backup_path'] == str(backup)
             and type(digest) is str and re.fullmatch('[0-9a-f]{64}', digest)
             and value['reviewed_v1_sha256'] == digest
             and type(value['expected_user_version']) is int and value['expected_user_version'] == 0
             and type(value['expected_confirmed_rows']) is int and value['expected_confirmed_rows'] == 5
             and value['expected_fifo_sha256'] == EMPTY and value['expected_heads_sha256'] == EMPTY,
             'preparation-expectation-mismatch')
    for key in ('common_identity', 'queue_directory_identity', 'journal_identity'):
        _pin(value[key])
    identities = value['lock_identities']
    _require(type(identities) is dict and set(identities) == set(LOCKS), 'missing-lock-identity-policy')
    for name in LOCKS:
        if name == LOCKS[0] and identities[name] is None:
            continue  # Explicitly pinned absence, not an omitted/unknown identity.
        _pin(identities[name])
    return value


def _walk(path, stack):
    """Open every component without symlink traversal, retaining the final FD."""
    fd = os.open('/', DIR_FLAGS)
    try:
        for part in path.parts[1:]:
            child = os.open(part, DIR_FLAGS, dir_fd=fd)
            os.close(fd)
            fd = child
    except BaseException:
        os.close(fd)
        raise
    stack.callback(os.close, fd)
    return fd


def _regular(info, mode=None):
    _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and info.st_nlink == 1
             and 0 <= info.st_size <= LIMIT, 'unsafe-regular-file')
    if mode is not None:
        _require(stat.S_IMODE(info.st_mode) == mode, 'unsafe-file-mode')
    else:
        _require(stat.S_IMODE(info.st_mode) in (0o600, 0o644), 'unsafe-lock-mode')


def _open_file(directory, name, stack, *, writable=False):
    fd = os.open(name, (os.O_RDWR if writable else os.O_RDONLY) | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
    stack.callback(os.close, fd)
    return fd


def _bytes(fd):
    info = os.fstat(fd)
    _require(info.st_size <= LIMIT, 'oversize-file')
    data = os.pread(fd, LIMIT + 1, 0)
    _require(len(data) == info.st_size and len(data) <= LIMIT, 'short-or-moving-file')
    return data


def _named(directory, name, fd, *, mode=None):
    current = os.stat(name, dir_fd=directory, follow_symlinks=False)
    opened = os.fstat(fd)
    _regular(current, mode)
    _regular(opened, mode)
    _require(_id(current) == _id(opened), 'file-name-or-owner-moved')


def _no_sidecars(directory, name='releases.sqlite3'):
    for suffix in ('-journal', '-wal', '-shm'):
        try:
            os.stat(name + suffix, dir_fd=directory, follow_symlinks=False)
        except FileNotFoundError:
            continue
        raise ValueError('sidecar-or-outstanding-transaction')


def _read_db(path, version):
    with closing(sqlite3.connect(path.as_uri() + '?mode=ro&immutable=1', uri=True)) as db:
        db.enable_load_extension(False)
        db.execute('PRAGMA trusted_schema=OFF')
        rows = reviewed._schema(db, version)
        _require(len(rows) == 5 and all(row[1] == 'confirmed' and not row[5].startswith('control-upgrade:') for row in rows),
                 'not-five-confirmed-ordinary-rows')
        if version == 2:
            _require(db.execute("SELECT mode FROM releases").fetchall() == [('ordinary',)] * 5
                     and db.execute('SELECT * FROM upgrade_bindings').fetchall() == [], 'nonordinary-or-upgrade-state')
        return rows


def _publish_backup(directory, name, data):
    """Separate inode, raw bytes, exclusive atomic name publication; no overwrite."""
    temporary = '.reviewed-v1-' + secrets.token_hex(16)
    fd = os.open(temporary, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=directory)
    try:
        offset = 0
        while offset < len(data):
            written = os.write(fd, data[offset:])
            _require(written > 0, 'short-backup-write')
            offset += written
        _regular(os.fstat(fd), 0o600)
        _require(_bytes(fd) == data, 'backup-write-mismatch')
        os.fsync(fd)
        os.link(temporary, name, src_dir_fd=directory, dst_dir_fd=directory, follow_symlinks=False)
    finally:
        os.close(fd)
        # Only our exclusive staging name is removed; never the published backup.
        os.unlink(temporary, dir_fd=directory)
    os.fsync(directory)


def prepare_storage(common_dir, backup_path, reviewed_v1_sha256, verified_scope):
    """Prepare existing V1 in place; never grants runtime/installation authority.

    An absent runner lock requires lock_identities[runner] == None in a complete
    reviewed intent. Creation is AFTER full exact preflight; any later conflict
    retains that new empty lock. A retry needs independently refreshed inode pins.
    """
    phase = 'preflight'
    try:
        common, backup = _path(common_dir), _path(backup_path)
        _require(common.name == '.git' and common.parent.name == 'forcitis-one-psa-codex-lab', 'not-physical-Lab-common-dir')
        directory_path = common / 'merge-queue'
        _require(backup.parent == directory_path and backup.name not in
                 ('releases.sqlite3', 'releases.sqlite3-journal', 'releases.sqlite3-wal', 'releases.sqlite3-shm',
                  'merge.queue', 'merge.queue.heads', *LOCKS)
                 and not backup.name.startswith('.'), 'unsafe-backup-destination')
        binding = _binding(verified_scope, common, backup, reviewed_v1_sha256)
        with ExitStack() as stack:
            common_fd = _walk(common, stack)
            _require(_id(os.fstat(common_fd)) == binding['common_identity'], 'wrong-common-inode')
            directory = os.open('merge-queue', DIR_FLAGS, dir_fd=common_fd)
            stack.callback(os.close, directory)
            parent_info = os.fstat(directory)
            _require(_id(parent_info) == binding['queue_directory_identity'] and stat.S_ISDIR(parent_info.st_mode)
                     and stat.S_IMODE(parent_info.st_mode) in (0o755, 0o700), 'unsafe-canonical-directory')
            private = stat.S_IMODE(parent_info.st_mode) == 0o700
            journal_fd = _open_file(directory, 'releases.sqlite3', stack)
            _regular(os.fstat(journal_fd), 0o600)
            _require(_id(os.fstat(journal_fd)) == binding['journal_identity'], 'wrong-journal-inode')
            empty_fds = {name: _open_file(directory, name, stack) for name in ('merge.queue', 'merge.queue.heads')}
            lock_fds, lock_bytes = {}, {}
            for name in LOCKS:
                expected = binding['lock_identities'][name]
                if expected is None:
                    try:
                        os.stat(name, dir_fd=directory, follow_symlinks=False)
                    except FileNotFoundError:
                        continue
                    raise ValueError('expected-runner-absence-is-not-absence')
                fd = _open_file(directory, name, stack, writable=True)
                _regular(os.fstat(fd))
                _require(_id(os.fstat(fd)) == expected, 'wrong-lock-inode')
                lock_fds[name], lock_bytes[name] = fd, _bytes(fd)

            def stable():
                # Rewalk, not just resolve(), to reject replacement/alias components.
                with ExitStack() as check:
                    current = _walk(common, check)
                    _require(_id(os.fstat(current)) == binding['common_identity'], 'common-path-moved')
                    child = os.open('merge-queue', DIR_FLAGS, dir_fd=current)
                    check.callback(os.close, child)
                    _require(_id(os.fstat(child)) == binding['queue_directory_identity'], 'directory-path-moved')
                    mode = stat.S_IMODE(os.fstat(child).st_mode)
                    _require(mode == 0o700 if private else mode in (0o755, 0o700), 'directory-privacy-or-mode-moved')
                _named(directory, 'releases.sqlite3', journal_fd, mode=0o600)
                for name, fd in empty_fds.items():
                    _named(directory, name, fd)
                    _require(_bytes(fd) == b'', 'nonempty-canonical-FIFO-or-heads')
                for name, fd in lock_fds.items():
                    _named(directory, name, fd)
                    _require(_bytes(fd) == lock_bytes[name], 'lock-bytes-moved')
                _no_sidecars(directory)

            def state():
                stable()
                raw = _bytes(journal_fd)
                with closing(sqlite3.connect((directory_path / 'releases.sqlite3').as_uri() + '?mode=ro&immutable=1', uri=True)) as db:
                    version = db.execute('PRAGMA user_version').fetchone()[0]
                _require(version in (0, 2), 'unknown-canonical-version')
                rows = _read_db(directory_path / 'releases.sqlite3', version)
                _no_sidecars(directory, backup.name)
                try:
                    backup_fd = os.open(backup.name, FILE_FLAGS, dir_fd=directory)
                except FileNotFoundError:
                    backup_fd = None
                if backup_fd is not None:
                    try:
                        _no_sidecars(directory, backup.name)
                        _named(directory, backup.name, backup_fd, mode=0o600)
                        _require(_id(os.fstat(backup_fd)) != binding['journal_identity'], 'backup-is-canonical-inode')
                        old_raw = _bytes(backup_fd)
                        _require(hashlib.sha256(old_raw).hexdigest() == reviewed_v1_sha256
                                 and _read_db(backup, 0) == rows, 'conflicting-or-unreviewed-backup')
                    finally:
                        os.close(backup_fd)
                    _require(stat.S_IMODE(os.fstat(directory).st_mode) == 0o700, 'backup-before-private-preparation')
                else:
                    _require(version == 0, 'V2-has-no-reviewed-backup')
                if version == 0:
                    _require(hashlib.sha256(raw).hexdigest() == reviewed_v1_sha256, 'reviewed-V1-bytes-mismatch')
                stable()
                _require(_bytes(journal_fd) == raw, 'preflight-journal-motion')
                return version, rows, raw, backup_fd is not None

            # Full read-only preflight precedes even absent-runner bootstrap.
            state()
            _require(_binding(verified_scope, common, backup, reviewed_v1_sha256) == binding, 'preparation-policy-moved')
            if binding['lock_identities'][LOCKS[0]] is None:
                fd = os.open(LOCKS[0], os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=directory)
                phase = 'prepared-incomplete'  # Publication is never erased on a later conflict.
                stack.callback(os.close, fd)
                _regular(os.fstat(fd), 0o600)
                _require(_bytes(fd) == b'', 'new-runner-lock-not-empty')
                lock_fds[LOCKS[0]], lock_bytes[LOCKS[0]] = fd, b''
                _named(directory, LOCKS[0], fd, mode=0o600)
                os.fsync(fd)
                os.fsync(directory)
            for name in LOCKS:
                fcntl.flock(lock_fds[name], fcntl.LOCK_EX | fcntl.LOCK_NB)
                stack.callback(fcntl.flock, lock_fds[name], fcntl.LOCK_UN)
            version, rows, raw, has_backup = state()
            _require(_binding(verified_scope, common, backup, reviewed_v1_sha256) == binding, 'preparation-policy-moved-under-lock')
            if version == 2:
                phase = 'commit-unknown'
                os.fsync(journal_fd)
                os.fsync(directory)
                stable()
                return _result('prepared', 'durable-v2')
            if stat.S_IMODE(os.fstat(directory).st_mode) != 0o700:
                try:
                    os.fchmod(directory, 0o700)  # All four original-inode locks are held.
                except BaseException:
                    if stat.S_IMODE(os.fstat(directory).st_mode) == 0o700:
                        phase = 'prepared-incomplete'
                    raise
            private = True
            phase = 'prepared-incomplete'
            _require(stat.S_IMODE(os.fstat(directory).st_mode) == 0o700, 'privacy-tightening-not-applied')
            os.fsync(directory)
            if not has_backup:
                _publish_backup(directory, backup.name, raw)
            version, rows_after, raw_after, has_backup = state()
            _require(version == 0 and rows_after == rows and raw_after == raw and has_backup, 'state-moved-before-migration')
            _require(_binding(verified_scope, common, backup, reviewed_v1_sha256) == binding, 'preparation-policy-moved-before-migration')
            storage = reviewed._Storage(directory_path / 'releases.sqlite3')
            _require(storage.file == reviewed._identity(os.fstat(journal_fd)), 'SQLite-storage-not-pinned-journal')
            with storage.connection() as db:
                db.execute('BEGIN IMMEDIATE')
                try:
                    _require(reviewed._schema(db, 0) == rows and _bytes(journal_fd) == raw, 'SQLite-entry-not-reviewed-V1')
                    db.execute(reviewed.ALTER)
                    db.execute(reviewed.BINDINGS)
                    db.execute('PRAGMA user_version=2')
                    _require(reviewed._schema(db, 2) == rows
                             and db.execute('SELECT * FROM upgrade_bindings').fetchall() == [], 'migration-row-or-binding-mismatch')
                    phase = 'commit-unknown'  # Set before COMMIT: its outcome may be unknown.
                    db.commit()
                except BaseException:
                    db.rollback()
                    raise
            storage.sync()
            version, final_rows, _, final_backup = state()
            _require(version == 2 and final_rows == rows and final_backup, 'postcommit-readback-mismatch')
            return _result('prepared', 'durable-v2')
    except Exception:
        return _result('hold', phase)
