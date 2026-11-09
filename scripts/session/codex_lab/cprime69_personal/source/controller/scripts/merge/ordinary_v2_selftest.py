"""Real ordinary operator over migrated scratch V2, fake proof/writer only."""
import copy
import fcntl
import importlib.util
from pathlib import Path
import sqlite3
import sys
import tempfile
import types
import unittest

HERE = Path(__file__).resolve().parent
HEAD, BASE, MERGE = (c * 40 for c in 'abc')


def target(name):
    path = HERE / (name + '.py')
    if not path.is_file():
        raise AssertionError('ORDINARY_V2_DEPENDENCY_NOT_PACKAGED: ' + name)
    spec = importlib.util.spec_from_file_location('independent_ordinary_' + name, path)
    module = importlib.util.module_from_spec(spec)
    before = sys.path[:]
    sys.path.insert(0, str(HERE))
    try:
        spec.loader.exec_module(module)
    finally:
        sys.path[:] = before
    return module


class OrdinaryV2AcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.operator = target('mq_lab_operator')
        self.storage = target('mq_lab_journal_v2')
        self.queue_type = target('mq_queue').Queue
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.common = Path(self.tmp.name).resolve() / 'forcitis-one-psa-codex-lab/.git'
        self.directory = self.common / 'merge-queue'
        self.directory.mkdir(parents=True, mode=0o700)
        self.directory.chmod(0o700)
        self.path = self.directory / 'releases.sqlite3'
        self.history = [(n, 'confirmed', str(n) * 40, str(n + 1) * 40, 'main',
                         'synthetic-ordinary-' + str(n), str(n + 2) * 40, 'ordinary') for n in range(1, 6)]
        with sqlite3.connect(self.path) as db:
            db.execute(self.storage.V1)
            db.execute(self.storage.ALTER)
            db.execute(self.storage.BINDINGS)
            db.execute('pragma user_version=2')
            db.executemany('insert into releases values (?,?,?,?,?,?,?,?)', self.history)
        self.path.chmod(0o600)
        # This initializes only the already migrated test schema, never drives
        # ordinary reserve/confirm directly through a helper.
        self.storage.ReleaseJournalV2(self.path)
        self.queue = self.queue_type(str(self.directory / 'merge.queue'))
        self.queue.enqueue(61)
        self.queue.set_head(61, HEAD)
        self.intent = dict(number=61, head=HEAD, base=BASE, target='main')
        self.approval = dict(self.intent, repo_id=1449415666, mode='normal')
        self.reason = None
        self.sends, self.factories = 0, 0
        self.events = []

    def load(self, number):
        self.assertEqual(61, number)
        self.events.append('load')
        return copy.deepcopy(self.approval)

    def verify(self, intent, approval):
        self.events.append('verify')
        if self.reason:
            return dict(action='hold', reason=self.reason)
        return dict(self.intent, action='eligible', reason='synthetic-current-normal-proof',
                    release_key='v2')

    def writer(self, authorize):
        self.factories += 1
        def send(intent):
            self.assertEqual(self.intent, intent)
            self.assertIs(authorize(intent), True)
            with (self.directory / 'merge.queue.merge-lock').open('r+b') as stream:
                with self.assertRaises(BlockingIOError, msg='ORDINARY_V2_SEND_WITHOUT_CANONICAL_LOCK'):
                    fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            with sqlite3.connect(self.path) as db:
                self.assertEqual(('reserved', 'ordinary', None), db.execute(
                    'select status,mode,merge_sha from releases where number=61').fetchone(),
                    'ORDINARY_V2_RESERVATION_NOT_COMMITTED_BEFORE_SEND')
            self.sends += 1
        def readback(intent):
            self.assertEqual(self.intent, intent)
            return dict(self.intent, merged=True, target_contains_merge=True, merge_sha=MERGE)
        return types.SimpleNamespace(send=send, readback=readback)

    def run_once(self):
        # Required actual public ordinary entry; no monkeypatch of _LazyJournal,
        # ReleaseJournal, run_once or journal/load/reserve/confirm behavior.
        return self.operator.run_command('once', 61, self.common, self)

    def assert_history(self):
        with sqlite3.connect(self.path) as db:
            self.assertEqual(self.history, db.execute('select * from releases where number<=5 order by number').fetchall())
            self.assertEqual((2,), db.execute('pragma user_version').fetchone())
            self.assertEqual([], db.execute('pragma foreign_key_check').fetchall())

    def test_actual_operator_reserves_confirms_ordinary_v2_preserving_five_rows(self):
        result = self.run_once()
        self.assertEqual('confirmed', result['action'],
                         'ORDINARY_OPERATOR_V2_JOURNAL_UNBOUND: ' + repr(result))
        self.assertEqual(1, self.sends)
        with sqlite3.connect(self.path) as db:
            self.assertEqual(('confirmed', 'ordinary', MERGE), db.execute(
                'select status,mode,merge_sha from releases where number=61').fetchone())
            self.assertEqual([], db.execute('select * from upgrade_bindings').fetchall())
        self.assert_history()
        self.assertEqual([], self.queue.numbers())
        self.assertIsNone(self.queue.head_of(61))
        self.run_once()
        self.assertEqual(1, self.sends)

    def test_old_source_or_missing_latest_main_health_has_zero_new_send(self):
        for reason in ('untrusted-gate-source', 'current-main-health-required'):
            with self.subTest(reason=reason):
                self.reason = reason
                self.assertEqual('hold', self.run_once()['action'])
                self.assertEqual(0, self.sends)
                self.assertEqual(0, self.factories)
                with sqlite3.connect(self.path) as db:
                    self.assertIsNone(db.execute('select number from releases where number=61').fetchone())
                self.assertEqual([61], self.queue.numbers())
                self.assert_history()

    def test_unadopted_protected_upgrade_blocks_new_ordinary_send(self):
        approval = dict(version=1, kind='control-upgrade',
            upgrade_id='11111111-1111-4111-8111-111111111111',
            repository='YeliangDu/forcitis-one-psa-codex-lab', repository_id=1449415666,
            number=42, base='d' * 40, head='e' * 40, target='main', old_manifest='1' * 64,
            new_manifest='2' * 64, author='one-platform',
            proof=dict(source_commit='f' * 40, workflow_path='.github/workflows/independent-control-proof.yml',
                       workflow_blob='a' * 40, run_id=100, attempt=2, artifact_id=701, sha256='3' * 64),
            reviews={'首轮': dict(id=801, reviewer='codex-首轮-one-design', head='e' * 40, verdict='pass'),
                     '终审': dict(id=802, reviewer='codex-终审-one-test', head='e' * 40, verdict='pass')})
        with self.queue.merge_lock():
            result = self.storage.ReleaseJournalV2(self.path).reserve_upgrade(approval)
        self.assertEqual('newly-reserved', result['action'], 'PENDING_UPGRADE_FIXTURE_NOT_RESERVED')
        self.assertEqual('hold', self.run_once()['action'])
        self.assertEqual(0, self.sends)
        self.assertEqual(0, self.factories)
        with sqlite3.connect(self.path) as db:
            self.assertIsNone(db.execute('select number from releases where number=61').fetchone())
            self.assertEqual([('reserved',)], db.execute('select phase from upgrade_bindings').fetchall())
        self.assert_history()


if __name__ == '__main__':
    unittest.main(verbosity=2)
