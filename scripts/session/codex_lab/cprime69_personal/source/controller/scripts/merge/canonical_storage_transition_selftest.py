"""Synthetic canonical V1→V2 preparation. Never selects a real repository."""
import copy
import fcntl
import hashlib
import importlib.util
import os
from pathlib import Path
import sqlite3
import stat
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
V1 = """CREATE TABLE releases (
                number INTEGER PRIMARY KEY CHECK(number > 0),
                status TEXT NOT NULL CHECK(status IN ('reserved', 'confirmed')),
                head TEXT NOT NULL, base TEXT NOT NULL, target TEXT NOT NULL CHECK(target = 'main'),
                release_key TEXT NOT NULL, merge_sha TEXT,
                CHECK((status = 'reserved' AND merge_sha IS NULL) OR
                      (status = 'confirmed' AND merge_sha IS NOT NULL)))"""
LOCKS = ['control-upgrade.runner-lock', 'merge.queue.merge-lock', 'merge.queue.lock', 'merge.queue.heads-lock']


class SyntheticReviewedScope:
    """Test-only policy injection, not a boolean or deploy/install authority."""
    def __init__(self, binding):
        self.binding, self.reads = copy.deepcopy(binding), 0

    def read_preparation_binding(self):
        self.reads += 1
        return copy.deepcopy(self.binding)


def identity(path):
    info = path.lstat()
    return dict(device=info.st_dev, inode=info.st_ino, uid=info.st_uid)


class CanonicalStorageTransitionTest(unittest.TestCase):
    def setUp(self):
        candidate = HERE / 'canonical_storage_transition.py'
        self.target = None
        if candidate.is_file():
            spec = importlib.util.spec_from_file_location('isolated_storage_transition', candidate)
            self.target = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(self.target)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.common = self.root / 'forcitis-one-psa-codex-lab/.git'
        self.directory = self.common / 'merge-queue'
        self.directory.mkdir(parents=True)
        self.directory.chmod(0o755)
        self.path = self.directory / 'releases.sqlite3'
        self.rows = [(n, 'confirmed', str(n) * 40, str(n + 1) * 40, 'main',
                      'synthetic-ordinary-' + str(n), str(n + 2) * 40) for n in range(1, 6)]
        with sqlite3.connect(self.path) as db:
            db.execute(V1)
            db.executemany('insert into releases values (?,?,?,?,?,?,?)', self.rows)
        self.path.chmod(0o600)
        for name in ['merge.queue', 'merge.queue.heads'] + LOCKS:
            file = self.directory / name
            file.write_bytes(b'')
            file.chmod(0o600)
        self.backup = self.directory / 'synthetic-reviewed-v1.backup.sqlite3'
        self.digest = hashlib.sha256(self.path.read_bytes()).hexdigest()
        self.binding = dict(version=1, kind='canonical-storage-preparation',
            intent_id='11111111-1111-4111-8111-111111111111',
            repository='YeliangDu/forcitis-one-psa-codex-lab', repository_id=1449415666,
            common_dir=str(self.common), common_identity=identity(self.common),
            queue_directory_identity=identity(self.directory), journal_identity=identity(self.path),
            reviewed_v1_sha256=self.digest, backup_path=str(self.backup),
            expected_user_version=0, expected_confirmed_rows=5,
            expected_fifo_sha256=hashlib.sha256(b'').hexdigest(),
            expected_heads_sha256=hashlib.sha256(b'').hexdigest(),
            lock_identities={name: identity(self.directory / name) for name in LOCKS},
            permitted_action='prepare-storage-only')
        self.scope = SyntheticReviewedScope(self.binding)

    def snapshot(self):
        result = {}
        for path in [self.directory] + sorted(self.directory.iterdir()):
            info = path.lstat()
            content = (os.readlink(path) if stat.S_ISLNK(info.st_mode)
                       else path.read_bytes() if stat.S_ISREG(info.st_mode) else None)
            result[path.name] = (info.st_dev, info.st_ino, info.st_uid, info.st_mode, info.st_nlink, content)
        return result

    def prepare(self, scope='default', digest=None, common=None):
        entry = getattr(self.target, 'prepare_storage', None)
        self.assertTrue(callable(entry), 'CANONICAL_STORAGE_PREPARATION_UNBOUND')
        result = entry(common or self.common, self.backup, self.digest if digest is None else digest,
                       self.scope if scope == 'default' else scope)
        self.assertIs(result.get('allows_new_write'), False)
        self.assertIs(result.get('installation_authority'), False)
        return result

    def refusal_unchanged(self, **kwargs):
        before = self.snapshot()
        result = self.prepare(**kwargs)
        self.assertEqual('hold', result.get('action'))
        self.assertEqual('preflight', result.get('phase'), 'REFUSAL_PHASE_MUST_BE_EXPLICIT')
        self.assertEqual(before, self.snapshot(), 'PREFLIGHT_REFUSAL_CHANGED_STORAGE')
        self.assertFalse(self.backup.exists())

    def test_reviewed_exact_v1_prepares_in_place_backup_and_five_ordinary_rows(self):
        before = self.snapshot()
        result = self.prepare()
        self.assertEqual('prepared', result.get('action'))
        self.assertEqual('durable-v2', result.get('phase'))
        self.assertEqual(0o700, stat.S_IMODE(self.directory.stat().st_mode))
        self.assertEqual(before[self.directory.name][:3], self.snapshot()[self.directory.name][:3])
        self.assertEqual(before[self.path.name][:3], self.snapshot()[self.path.name][:3])
        for name in LOCKS:
            self.assertEqual(before[name], self.snapshot()[name], 'LOCK_INODE_OR_BYTES_REPLACED')
        self.assertEqual(self.digest, hashlib.sha256(self.backup.read_bytes()).hexdigest())
        with sqlite3.connect(self.backup) as db:
            self.assertEqual((0,), db.execute('pragma user_version').fetchone())
            self.assertEqual(self.rows, db.execute('select * from releases order by number').fetchall())
        with sqlite3.connect(self.path) as db:
            self.assertEqual((2,), db.execute('pragma user_version').fetchone())
            self.assertEqual([row + ('ordinary',) for row in self.rows],
                             db.execute('select * from releases order by number').fetchall())
            self.assertEqual([], db.execute('select * from upgrade_bindings').fetchall())
            self.assertEqual([('ok',)], db.execute('pragma integrity_check').fetchall())
        self.assertEqual(b'', (self.directory / 'merge.queue').read_bytes())
        self.assertEqual(b'', (self.directory / 'merge.queue.heads').read_bytes())

    def test_missing_or_arbitrary_scope_never_authorizes_preparation(self):
        for scope in (None, True, {}, dict(self.binding)):
            with self.subTest(scope=scope):
                self.refusal_unchanged(scope=scope)

    def test_wrong_repo_physical_identity_or_digest_refuses_unchanged(self):
        for mutation in ('repo-id', 'slug', 'directory-inode', 'journal-inode', 'digest', 'alias'):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation == 'repo-id': self.scope.binding['repository_id'] = 123
                elif mutation == 'slug': self.scope.binding['repository'] = 'synthetic/foreign'
                elif mutation == 'directory-inode': self.scope.binding['queue_directory_identity']['inode'] += 1
                elif mutation == 'journal-inode': self.scope.binding['journal_identity']['inode'] += 1
                elif mutation == 'digest':
                    self.refusal_unchanged(digest='0' * 64)
                    continue
                else:
                    alias = self.root / 'alias'
                    alias.symlink_to(self.common, target_is_directory=True)
                    self.refusal_unchanged(common=alias)
                    continue
                self.refusal_unchanged()

    def test_unknown_version_schema_reserved_or_nonempty_fifo_heads_refuse_unchanged(self):
        for mutation in ('version', 'ddl', 'reserved', 'fifo', 'heads'):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation in ('version', 'ddl', 'reserved'):
                    with sqlite3.connect(self.path) as db:
                        if mutation == 'version': db.execute('pragma user_version=9')
                        elif mutation == 'ddl': db.execute('create table unexpected(id integer)')
                        else: db.execute("update releases set status='reserved',merge_sha=null where number=1")
                    # Even independently reviewed matching digest cannot bless wrong shape.
                    self.digest = hashlib.sha256(self.path.read_bytes()).hexdigest()
                    self.scope.binding['reviewed_v1_sha256'] = self.digest
                elif mutation == 'fifo': (self.directory / 'merge.queue').write_text('42\n')
                else: (self.directory / 'merge.queue.heads').write_text('42 ' + 'a' * 40 + '\n')
                self.refusal_unchanged()

    def test_unsafe_modes_hardlinks_and_symlinks_refuse_without_repair(self):
        for mutation in ('world-write-dir', 'readable-db', 'db-hardlink', 'db-symlink', 'lock-symlink'):
            with self.subTest(mutation=mutation):
                self.setUp()
                if mutation == 'world-write-dir': self.directory.chmod(0o777)
                elif mutation == 'readable-db': self.path.chmod(0o644)
                elif mutation == 'db-hardlink': os.link(self.path, self.root / 'db-extra-link')
                else:
                    path = self.path if mutation == 'db-symlink' else self.directory / LOCKS[1]
                    target = self.root / 'synthetic-saved'
                    path.rename(target)
                    path.symlink_to(target)
                self.refusal_unchanged()

    def test_actual_shared_lock_conflicts_refuse_without_replacing_lock_inode(self):
        for name in LOCKS:
            with self.subTest(lock=name):
                with (self.directory / name).open('r+b') as stream:
                    fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    self.refusal_unchanged()

    def test_in_place_fchmod_occurs_only_while_all_original_locks_are_held(self):
        real_fchmod = os.fchmod
        observed = []
        def check_then_chmod(fd, mode):
            info = os.fstat(fd)
            expected = self.binding['queue_directory_identity']
            self.assertEqual((expected['device'], expected['inode']), (info.st_dev, info.st_ino))
            self.assertEqual(0o700, mode)
            for name in LOCKS:
                self.assertEqual(self.binding['lock_identities'][name], identity(self.directory / name))
                with (self.directory / name).open('r+b') as contender:
                    with self.assertRaises(BlockingIOError, msg='MIGRATION_MUST_EXCLUDE_ORDINARY_QUEUE_WRITER'):
                        fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)
            observed.append((info.st_dev, info.st_ino, mode))
            return real_fchmod(fd, mode)
        with patch.object(os, 'fchmod', side_effect=check_then_chmod):
            result = self.prepare()
        self.assertEqual('prepared', result.get('action'))
        self.assertEqual(1, len(observed), 'DIRECTORY_MUST_BE_TIGHTENED_BY_PINNED_DESCRIPTOR')

    def test_permission_tightening_failure_cannot_start_migration(self):
        before = self.snapshot()
        with patch.object(os, 'fchmod', side_effect=PermissionError('synthetic denial')):
            result = self.prepare()
        self.assertEqual('hold', result.get('action'))
        self.assertEqual(before, self.snapshot())
        self.assertFalse(self.backup.exists())

    def test_durability_failure_is_hold_not_reset_or_install_authority(self):
        with patch.object(os, 'fsync', side_effect=OSError('synthetic fsync failure')):
            result = self.prepare()
        self.assertEqual('hold', result.get('action'))
        self.assertIn(result.get('phase'), ('preflight', 'prepared-incomplete', 'commit-unknown'))
        with sqlite3.connect(self.path) as db:
            self.assertEqual(self.rows, db.execute(
                'select number,status,head,base,target,release_key,merge_sha from releases order by number').fetchall())
            self.assertIn(db.execute('pragma user_version').fetchone()[0], (0, 2))
        for name, original in self.binding['lock_identities'].items():
            self.assertEqual(original, identity(self.directory / name))


if __name__ == '__main__':
    unittest.main()
