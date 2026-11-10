"""Test-owned post-effect interruption/readback observer; hosted scratch only."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


class RecoveryProbe:
    def __init__(self, test, host_probe):
        self.test, self.host = test, host_probe
        self.ledger = test.root / 'test-owned-cas-ledger.jsonl'
        self.attempts = 0
        self.old_pid = None
        self.recovery = None
        self.applied = None

    def record(self, record):
        fd = os.open(self.ledger, os.O_WRONLY | os.O_APPEND | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'ab') as stream:
            stream.write((json.dumps(record, sort_keys=True) + '\n').encode())
            stream.flush()
            os.fsync(stream.fileno())

    def cas_then_interrupt_old(self, expected, merge):
        # Called by TEST-owned parent RPC receiver for actual old child request.
        # No counter/result submitted by driver is accepted as effect evidence.
        self.attempts += 1
        self.record(dict(event='cas-attempt', expected=expected, merge=merge, ordinal=self.attempts))
        self.test.assertEqual(1, self.attempts, 'RECOVERY_ATTEMPTED_SECOND_PROTECTED_CAS')
        self.host.cas(expected, merge)
        self.test.assertEqual(merge, self.test.git('rev-parse', 'refs/heads/main'))
        self.applied = merge
        self.record(dict(event='cas-applied', merge=merge))
        self.test.assertIn('old', self.host.children, 'REAL_OLD_CHILD_REQUIRED_FOR_INTERRUPTION')
        proc, _ = self.host.children['old']
        self.test.assertIsNone(proc.poll(), 'OLD_CHILD_ALREADY_EXITED_BEFORE_EFFECT_FAULT')
        self.old_pid = proc.pid
        proc.kill()
        proc.communicate(timeout=10)  # OS wait/reap, never just a JSON exit flag.
        self.test.assertIsNotNone(proc.returncode)
        self.host.reaped.add('old')
        self.record(dict(event='old-reaped-after-effect', pid=proc.pid, returncode=proc.returncode))
        # Parent RPC deliberately sends no success reply. No new send is allowed.

    def launch_recovery(self, source_root, entry_relative, request_file, commit, source_paths):
        self.test.assertIsNotNone(self.applied, 'RECOVERY_BEFORE_ACTUAL_CAS_EFFECT')
        self.test.assertIn('old', self.host.reaped, 'RECOVERY_BEFORE_OS_WAIT')
        root = self.host.inside(source_root)
        entry = self.host.inside(root / entry_relative)
        request = self.host.inside(request_file)
        for relative in source_paths:
            path = self.host.inside(root / relative)
            raw = subprocess.run(['git', '--no-replace-objects', '-C', str(self.test.root),
                                  'show', commit + ':' + relative], env=self.test.env,
                                 capture_output=True, timeout=10)
            self.test.assertEqual(0, raw.returncode, 'RECOVERY_SOURCE_MISSING_FROM_PINNED_COMMIT')
            self.test.assertEqual(hashlib.sha256(raw.stdout).hexdigest(), hashlib.sha256(path.read_bytes()).hexdigest())
        report = self.test.root / 'test-owned-recovery-reads.json'
        observer = Path(__file__).with_name('recovery_child_observer.py')
        proc = subprocess.Popen([sys.executable, '-B', str(observer), str(entry), str(request),
            str(self.test.dbpath), str(self.test.root), str(report)], cwd=root, env=self.test.env,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.test.assertNotEqual(self.old_pid, proc.pid, 'RECOVERY_MUST_BE_DIFFERENT_OS_PROCESS')
        self.recovery = proc, report
        self.test.addCleanup(self.cleanup)
        self.record(dict(event='recovery-started', pid=proc.pid, source_commit=commit))
        return proc.pid

    def cleanup(self):
        if self.recovery:
            proc, _ = self.recovery
            if proc.poll() is None:
                proc.kill()
            proc.communicate(timeout=10)

    def verify_recovery(self):
        self.test.assertIsNotNone(self.recovery, 'RECOVERY_LAUNCHER_MATERIAL_UNBOUND')
        proc, report = self.recovery
        stdout, stderr = proc.communicate(timeout=30)
        self.test.assertEqual(0, proc.returncode, 'RECOVERY_CHILD_ERROR: ' + stderr.decode(errors='replace')[-500:])
        observed = json.loads(report.read_bytes())
        self.test.assertEqual(proc.pid, observed['pid'])
        self.test.assertTrue(observed['opened_exact_v2'], 'RECOVERY_DID_NOT_OPEN_DISK_V2')
        sql = [query.lower() for query in observed['sql']]
        self.test.assertTrue(any('select' in q and 'releases' in q for q in sql), 'RECOVERY_DID_NOT_READ_RESERVATION')
        self.test.assertTrue(any('select' in q and 'upgrade_bindings' in q for q in sql), 'RECOVERY_DID_NOT_READ_UPGRADE_BINDING')
        reads = observed['git_reads']
        self.test.assertTrue(any(r['returncode'] == 0 and r['stdout'].strip() == self.applied for r in reads),
                             'RECOVERY_DID_NOT_READ_ACTUAL_MAIN_REF')
        self.test.assertEqual([], observed['mutating_git'], 'RECOVERY_MUST_NOT_SEND_OR_MERGE')
        self.test.assertEqual(1, self.attempts)
        self.test.assertEqual(1, self.host.cas_count)
        records = [json.loads(line) for line in self.ledger.read_text().splitlines()]
        self.test.assertEqual(1, sum(r['event'] == 'cas-attempt' for r in records))
        self.test.assertEqual(1, sum(r['event'] == 'cas-applied' for r in records))
        self.test.assertEqual(self.applied, self.test.git('rev-parse', 'refs/heads/main'))
        return observed
