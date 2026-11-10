"""Test-owned observation/CAS port. All processes/files are hosted scratch only."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys

LOCKS = ('control-upgrade.runner-lock', 'merge.queue.merge-lock', 'merge.queue.lock', 'merge.queue.heads-lock')


class HostProbe:
    def __init__(self, test):
        self.test = test
        self.events, self.children, self.cas_count = [], {}, 0
        self.reaped = set()
        self.directory = test.dbpath.parent
        self.lock_ids = {name: ((self.directory / name).stat().st_dev, (self.directory / name).stat().st_ino)
                         for name in LOCKS if (self.directory / name).exists()}
        self.material = {}

    def inside(self, value):
        path = Path(value).resolve(strict=True)
        self.test.assertIn(self.test.root, path.parents, 'PROBE_PATH_OUTSIDE_SCRATCH')
        return path

    def cas(self, expected, new):
        # Probe owns actual ref effect: no success can be forged by trace text.
        self.test.assertEqual(self.test.old, expected)
        with sqlite3.connect(self.test.dbpath) as db:
            release = db.execute("select number,head,base,status from releases where mode='control-upgrade'").fetchall()
            binding = db.execute('select number,phase,approval_json from upgrade_bindings').fetchall()
            self.test.assertEqual(1, len(release), 'DURABLE_RELEASE_NOT_VISIBLE_BEFORE_CAS')
            self.test.assertEqual(1, len(binding), 'DURABLE_BINDING_NOT_VISIBLE_BEFORE_CAS')
            self.test.assertEqual(('reserved', 'reserved'), (release[0][3], binding[0][1]))
            self.test.assertEqual(release[0][0], binding[0][0])
            approval = json.loads(binding[0][2])
            self.test.assertEqual((self.test.candidate, expected), (approval['head'], approval['base']))
        # A separate process, not thread or claimed trace, probes every OS lock.
        script = '''import fcntl,json,sys
from pathlib import Path
out=[]
for name in json.loads(sys.argv[2]):
 p=Path(sys.argv[1])/name
 with p.open('r+b') as f:
  try: fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
  except BlockingIOError: out.append(name)
print(json.dumps(out))
'''
        check = subprocess.run([sys.executable, '-B', '-c', script, str(self.directory), json.dumps(LOCKS)],
                               capture_output=True, text=True, timeout=10)
        self.test.assertEqual(0, check.returncode)
        self.test.assertEqual(list(LOCKS), json.loads(check.stdout), 'CAS_OS_LOCKS_NOT_HELD')
        for name in LOCKS:
            info = (self.directory / name).stat()
            current = (info.st_dev, info.st_ino)
            if name in self.lock_ids:
                self.test.assertEqual(self.lock_ids[name], current, 'CANONICAL_LOCK_INODE_CHANGED')
            self.lock_ids[name] = current
        self.test.assertEqual([expected, self.test.candidate], self.test.git('show', '-s', '--format=%P', new).split())
        self.cas_count += 1
        self.test.assertEqual(1, self.cas_count, 'SECOND_PROTECTED_CAS_FORBIDDEN')
        self.events.append('committed-reservation-and-locks-before-cas')
        self.test.git('update-ref', 'refs/heads/main', new, expected)
        self.events.append('protected-cas')

    def launch(self, role, source_root, entry, request_file, commit, source_paths):
        self.test.assertIn(role, ('old', 'successor'))
        self.test.assertNotIn(role, self.children)
        root = self.inside(source_root)
        program, request = self.inside(root / entry), self.inside(request_file)
        expected = {}
        for relative in source_paths:
            path = self.inside(root / relative)
            result = subprocess.run(['git', '--no-replace-objects', '-C', str(self.test.root),
                'show', commit + ':' + relative], env=self.test.env, capture_output=True, timeout=10)
            self.test.assertEqual(0, result.returncode, 'LOADED_SOURCE_NOT_IN_EXACT_COMMIT')
            digest = hashlib.sha256(result.stdout).hexdigest()
            self.test.assertEqual(digest, hashlib.sha256(path.read_bytes()).hexdigest())
            expected[str(path)] = digest
        if role == 'successor':
            self.test.assertIn('old', self.reaped, 'OLD_PROCESS_NOT_WAITED_BEFORE_NEW_LOADER')
            self.test.assertEqual(commit, self.test.git('rev-parse', 'refs/heads/main'))
        report = self.test.root / ('test-owned-' + role + '-profile.json')
        child = Path(__file__).with_name('cutover_profile_child.py')
        # Child observer code is test-owned; program is actual pinned launcher adapter.
        proc = subprocess.Popen([sys.executable, '-B', str(child), str(program), str(request),
                                 str(report), json.dumps(expected)],
                                cwd=root, env=self.test.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.children[role] = (proc, report)
        self.material[role] = expected
        self.test.addCleanup(self.cleanup_child, role)
        self.events.append('launched-' + role)
        return proc.pid

    def cleanup_child(self, role):
        proc, _ = self.children[role]
        if proc.poll() is None:
            proc.terminate()
        proc.communicate(timeout=10)

    def wait(self, role):
        proc, report = self.children[role]
        stdout, stderr = proc.communicate(timeout=30)
        self.test.assertEqual(0, proc.returncode, 'REAL_CHILD_FAILED: ' + stderr.decode(errors='replace')[-1000:])
        self.reaped.add(role)
        self.events.append('reaped-' + role)
        return json.loads(report.read_bytes())

    def verify_successor(self):
        self.test.assertIn('old', self.reaped)
        self.test.assertIn('successor', self.reaped)
        old, successor = self.children['old'][0], self.children['successor'][0]
        self.test.assertNotEqual(old.pid, successor.pid)
        report = json.loads(self.children['successor'][1].read_bytes())
        calls = report['calls']
        operator = [c for c in calls if c['function'] == 'run_command' and c['file'].endswith('/scripts/merge/mq_lab_operator.py')]
        v2 = [c for c in calls if c['file'].endswith('/scripts/merge/mq_lab_journal_v2.py')]
        self.test.assertTrue(operator, 'SUCCESSOR_DID_NOT_CALL_REAL_ORDINARY_OPERATOR')
        self.test.assertTrue(v2, 'SUCCESSOR_ORDINARY_DID_NOT_USE_REAL_V2')
        for call in operator + v2:
            self.test.assertIn(call['file'], self.material['successor'])
            self.test.assertEqual(self.material['successor'][call['file']], call['sha256'])
        with sqlite3.connect(self.test.dbpath) as db:
            rows = db.execute("select number,status,merge_sha from releases where mode='ordinary' and number>5").fetchall()
            self.test.assertEqual(1, len(rows))
            self.test.assertEqual('confirmed', rows[0][1])
            self.test.assertEqual(rows[0][2], self.test.git('rev-parse', 'refs/heads/main'))
