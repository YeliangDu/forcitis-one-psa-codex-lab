"""Single-candidate hosted-only cutover acceptance entry; no real repo ports."""
import fcntl
import hashlib
import importlib.util
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
V1 = """CREATE TABLE releases (
                number INTEGER PRIMARY KEY CHECK(number > 0),
                status TEXT NOT NULL CHECK(status IN ('reserved', 'confirmed')),
                head TEXT NOT NULL, base TEXT NOT NULL, target TEXT NOT NULL CHECK(target = 'main'),
                release_key TEXT NOT NULL, merge_sha TEXT,
                CHECK((status = 'reserved' AND merge_sha IS NULL) OR
                      (status = 'confirmed' AND merge_sha IS NOT NULL)))"""


class CutoverEndToEndTest(unittest.TestCase):
    def setUp(self):
        candidate = HERE / 'cutover_e2e_driver.py'
        self.driver = None
        if candidate.is_file():
            spec = importlib.util.spec_from_file_location('isolated_cutover_driver', candidate)
            self.driver = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(self.driver)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve() / 'forcitis-one-psa-codex-lab'
        self.root.mkdir()
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL='/dev/null',
                        GIT_AUTHOR_NAME='Synthetic', GIT_AUTHOR_EMAIL='synthetic@example.invalid',
                        GIT_COMMITTER_NAME='Synthetic', GIT_COMMITTER_EMAIL='synthetic@example.invalid')
        self.git('init', '--initial-branch=main')
        self.git('config', 'protocol.file.allow', 'always')
        (self.root / 'business.txt').write_text('synthetic initial business\n')
        from locked_phase_setup import old_before_commit
        old_before_commit(self)
        self.git('add', 'business.txt')
        self.git('commit', '-m', 'synthetic old main')
        self.old = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-b', 'synthetic-protected')
        (self.root / 'control.txt').write_text('synthetic reviewed new control\n')
        from locked_phase_setup import new_before_commit
        new_before_commit(self)
        self.git('add', 'control.txt')
        self.git('commit', '-m', 'synthetic protected candidate')
        self.candidate = self.git('rev-parse', 'HEAD')
        self.git('checkout', 'main')
        self.common = self.root / '.git'
        self.directory = self.common / 'merge-queue'
        self.directory.mkdir(mode=0o755)
        self.directory.chmod(0o755)
        self.dbpath = self.directory / 'releases.sqlite3'
        self.history = [(n, 'confirmed', str(n) * 40, str(n + 1) * 40, 'main',
                         'synthetic-history-' + str(n), str(n + 2) * 40) for n in range(1, 6)]
        with sqlite3.connect(self.dbpath) as db:
            db.execute(V1)
            db.executemany('insert into releases values (?,?,?,?,?,?,?)', self.history)
        self.dbpath.chmod(0o600)
        for name in ('merge.queue', 'merge.queue.heads', 'merge.queue.merge-lock',
                     'merge.queue.lock', 'merge.queue.heads-lock'):
            path = self.directory / name
            path.write_bytes(b'')
            path.chmod(0o600)
        self.assertFalse((self.directory / 'control-upgrade.runner-lock').exists())
        self.digest = hashlib.sha256(self.dbpath.read_bytes()).hexdigest()

    def git(self, *args):
        result = subprocess.run(['git', '--no-replace-objects', '-C', str(self.root), *args],
            env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=20)
        self.assertEqual(0, result.returncode, 'SCRATCH_GIT_SETUP_OR_READ_FAILED: ' + result.stderr)
        return result.stdout.strip()

    def run_scenario(self, fault):
        run = getattr(self.driver, 'run_cutover_scenario', None)
        self.assertTrue(callable(run), 'CUTOVER_E2E_DRIVER_UNBOUND')
        # Driver is a harness invoking candidate's real old/new launchers and
        # state code. It must not implement substitute orchestration for them.
        return run(dict(root=self.root, common_dir=self.common, old_base=self.old,
                        candidate_head=self.candidate, reviewed_v1_sha256=self.digest,
                        history=list(self.history), fault=fault))

    def assert_history(self):
        with sqlite3.connect(self.dbpath) as db:
            self.assertEqual(self.history, db.execute(
                'select number,status,head,base,target,release_key,merge_sha from releases where number<=5 order by number').fetchall())

    def test_v1_to_one_protected_cutover_to_healthy_successor_to_next_ordinary_card(self):
        result = self.run_scenario(None)
        self.assertEqual('ordinary-next-confirmed', result.get('action'))
        self.assertEqual(1, result.get('protected_send_count'))
        self.assertEqual(1, result.get('ordinary_send_count'))
        self.assertEqual([], self.git('remote').splitlines())
        successor, ordinary_merge = result['successor'], result['ordinary_merge']
        self.assertEqual([self.old, self.candidate], self.git('show', '-s', '--format=%P', successor).split())
        self.assertEqual(successor, self.git('rev-parse', ordinary_merge + '^1'))
        self.assertEqual(ordinary_merge, self.git('rev-parse', 'refs/heads/main'))
        self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
        lock = self.directory / 'control-upgrade.runner-lock'
        self.assertTrue(lock.is_file())
        self.assertEqual(0o600, lock.stat().st_mode & 0o777)
        with sqlite3.connect(self.dbpath) as db:
            self.assertEqual((2,), db.execute('pragma user_version').fetchone())
            self.assertEqual([('adopted', successor)], db.execute(
                'select phase,adopted_main from upgrade_bindings').fetchall())
        self.assert_history()
        # These are artifact references to independently checkable child/native
        # traces, not a caller boolean saying source was switched.
        self.assertTrue((self.root / result['new_loader_trace']).is_file())
        self.assertTrue((self.root / result['lock_trace']).is_file())
        self.assertTrue((self.root / result['proof_trace']).is_file())
        self.assertEqual(b'', (self.directory / 'merge.queue').read_bytes())

    def test_invalid_pre_send_evidence_cannot_move_main_or_send(self):
        for fault in ('old-head', 'old-base', 'fake-green-ci', 'shared-review-resident'):
            with self.subTest(fault=fault):
                self.setUp()
                result = self.run_scenario(fault)
                self.assertEqual('hold', result.get('action'))
                self.assertEqual(0, result.get('protected_send_count'))
                self.assertEqual(0, result.get('ordinary_send_count'))
                self.assertEqual(self.old, self.git('rev-parse', 'refs/heads/main'))
                self.assert_history()

    def test_lost_send_response_restart_and_duplicate_dispatch_never_send_twice(self):
        for fault in ('lost-response-restart', 'crash-after-send', 'duplicate-dispatch'):
            with self.subTest(fault=fault):
                self.setUp()
                result = self.run_scenario(fault)
                self.assertEqual(1, result.get('protected_send_count'))
                self.assertGreaterEqual(result.get('readback_count', 0), 1)
                self.assertTrue((self.root / result['restart_trace']).is_file())
                self.assert_history()

    def test_unhealthy_successor_cannot_adopt_or_process_next_ordinary_card(self):
        result = self.run_scenario('successor-health-failed')
        self.assertEqual('hold', result.get('action'))
        self.assertEqual(1, result.get('protected_send_count'))
        self.assertEqual(0, result.get('ordinary_send_count'))
        with sqlite3.connect(self.dbpath) as db:
            self.assertEqual([('confirmed', None)], db.execute(
                'select phase,adopted_main from upgrade_bindings').fetchall())
        self.assert_history()

    def test_real_existing_merge_lock_conflict_prevents_preparation_and_any_send(self):
        before = self.dbpath.read_bytes()
        with (self.directory / 'merge.queue.merge-lock').open('r+b') as stream:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
            result = self.run_scenario('lock-conflict')
        self.assertEqual('hold', result.get('action'))
        self.assertEqual(0, result.get('protected_send_count'))
        self.assertEqual(before, self.dbpath.read_bytes())
        self.assertEqual(0o755, self.directory.stat().st_mode & 0o777)


    def observed_scenario(self, fault=None):
        from cutover_host_probe import HostProbe
        run = getattr(self.driver, 'run_cutover_scenario', None)
        self.assertTrue(callable(run), 'CUTOVER_E2E_DRIVER_UNBOUND')
        probe = HostProbe(self)
        result = run(dict(root=self.root, common_dir=self.common, old_base=self.old,
            candidate_head=self.candidate, reviewed_v1_sha256=self.digest,
            history=list(self.history), fault=fault, host_probe=probe))
        return result, probe

    def test_test_owned_cas_probe_observes_child_lock_contention_and_committed_pair(self):
        result, probe = self.observed_scenario()
        self.assertEqual(1, probe.cas_count, 'REAL_CAS_PROBE_NOT_USED')
        self.assertEqual(['committed-reservation-and-locks-before-cas', 'protected-cas'],
            [event for event in probe.events if event in ('committed-reservation-and-locks-before-cas', 'protected-cas')])
        self.assertEqual(result['ordinary_merge'], self.git('rev-parse', 'refs/heads/main'))

    def test_test_owned_child_supervisor_proves_exit_successor_bytes_and_ordinary_v2(self):
        result, probe = self.observed_scenario()
        self.assertEqual({'old', 'successor'}, set(probe.children), 'REAL_OLD_AND_SUCCESSOR_CHILDREN_REQUIRED')
        probe.verify_successor()
        self.assertLess(probe.events.index('reaped-old'), probe.events.index('launched-successor'))
        self.assertEqual('ordinary-next-confirmed', result['action'])

    def test_proof_trace_uses_independently_frozen_pins_and_rejects_forged_green(self):
        import copy
        import json
        from cutover_proof_trace import verify
        # Pins are independently frozen by test owner/lead before candidate
        # execution, not read from driver result, trace or candidate artifacts.
        from cutover_trace_fixture import trusted_pins
        pins = trusted_pins(self)
        result, probe = self.observed_scenario()
        trace = (self.root / result['proof_trace']).resolve()
        self.assertIn(self.root, trace.parents)
        raw = trace.read_bytes()
        verify(raw, pins)
        for field, replacement in (('attempt', 99), ('source_commit', self.candidate),
                                    ('new_manifest', '0' * 64), ('reviews', [])):
            with self.subTest(field=field):
                forged = json.loads(raw)
                forged[field] = replacement
                with self.assertRaises(AssertionError):
                    verify(json.dumps(forged).encode(), pins)
        self.setUp()  # Fresh actual old ref: no stale-state HOLD may mask fake green.
        bad, bad_probe = self.observed_scenario('fake-green-ci')
        self.assertEqual('hold', bad['action'])
        self.assertEqual(0, bad_probe.cas_count, 'FORGED_GREEN_REACHED_ACTUAL_CAS')


if __name__ == '__main__':
    unittest.main()
