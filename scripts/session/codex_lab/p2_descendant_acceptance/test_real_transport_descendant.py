"""Hosted Linux only; run from an independently exported exact-B source tree.

This exercises the production argv method, runtime, Queue and V1 durable journal.
It does not instantiate the live operator constructor or grant main authority.
"""
import fcntl
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest


def wait_for(predicate, label):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.02)
    raise RuntimeError('CAPABILITY_HOLD: missing barrier: ' + label)


def modules(root):
    sys.path.insert(0, str(root / 'scripts/merge'))
    from mq_lab_operator import ProductionPorts
    from mq_github import sh
    from mq_lab_runtime import run_iteration
    from mq_lab_journal import ReleaseJournal
    from mq_queue import Queue
    return ProductionPorts, sh, run_iteration, ReleaseJournal, Queue


def adapter(root):
    ProductionPorts, sh, _, _, _ = modules(root)
    port = object.__new__(ProductionPorts)
    port.sh = sh
    return port


def owner(root, work):
    os.chdir(work / 'sender')
    port = adapter(root)
    _, _, run, Journal, Queue = modules(root)
    intent = json.loads((work / 'intent.json').read_text())

    class Writer:
        def send(self, value):
            with (work / 'sends').open('a') as stream:
                stream.write('send\n'); stream.flush(); os.fsync(stream.fileno())
            # Actual production argv adapter, real Git and controlled SSH helper.
            return port.git(['push', '--porcelain',
                             '--force-with-lease=refs/heads/main:' + value['base'],
                             'ssh://fixture.invalid/receiver',
                             (work / 'merge').read_text() + ':refs/heads/main'])

        def readback(self, value):
            return None

    q = Queue(str(work / 'queue'))
    run(q, Journal(str(work / 'journal.sqlite3')),
        lambda n, h: dict(intent, action='eligible', reason='disposable fixture', release_key='fixture'),
        Writer(), allow_write=True)


HELPER = r"""import os, pathlib, shlex, sys, time
w=pathlib.Path(os.environ['FIXTURE_ROOT'])
parts=shlex.split(sys.argv[-1])
if parts != ['git-receive-pack', '/receiver']:
    raise SystemExit('unexpected transport argv')
(w/'helper.pid').write_text(str(os.getpid()))
(w/'ready').write_text('ready')
limit=time.monotonic()+30
while not (w/'release').exists():
    if time.monotonic()>limit: raise SystemExit('barrier timeout')
    time.sleep(.02)
os.execv(os.environ['FIXTURE_GIT'], [os.environ['FIXTURE_GIT'], 'receive-pack', str(w/'receiver.git')])
"""


class RealTransportDescendantTest(unittest.TestCase):
    def test_parent_death_preserves_lifetime_exclusion_and_readonly_recovery(self):
        if sys.platform != 'linux':
            raise RuntimeError('CAPABILITY_HOLD: hosted Linux required')
        root = Path(os.environ['EXACT_B_SOURCE_ROOT']).resolve(strict=True)
        git = shutil.which('git')
        if not git:
            raise RuntimeError('CAPABILITY_HOLD: real Git missing')
        with tempfile.TemporaryDirectory(prefix='p2-descendant-') as temporary:
            w = Path(temporary).resolve()
            home = w / 'home'; home.mkdir()
            env = {'PATH': str(Path(git).parent) + ':/usr/bin:/bin', 'HOME': str(home),
                   'LANG': 'C.UTF-8', 'GIT_CONFIG_NOSYSTEM': '1',
                   'GIT_CONFIG_GLOBAL': '/dev/null', 'GIT_TERMINAL_PROMPT': '0',
                   'GIT_AUTHOR_NAME': 'Fixture', 'GIT_AUTHOR_EMAIL': 'fixture@invalid',
                   'GIT_COMMITTER_NAME': 'Fixture', 'GIT_COMMITTER_EMAIL': 'fixture@invalid',
                   'GIT_ALLOW_PROTOCOL': 'file:ssh', 'GIT_SSH_VARIANT': 'ssh',
                   'FIXTURE_ROOT': str(w), 'FIXTURE_GIT': git, 'PYTHONDONTWRITEBYTECODE': '1'}
            helper = w / 'ssh_helper.py'; helper.write_text(HELPER)
            import shlex
            env['GIT_SSH_COMMAND'] = shlex.join([sys.executable, '-B', str(helper)])
            def command(*args, cwd=None):
                return subprocess.check_output([git, '-c', 'core.hooksPath=/dev/null', *args],
                                               cwd=cwd, env=env, text=True, input='').strip()
            command('init', '--bare', str(w / 'receiver.git'))
            command('init', str(w / 'sender'))
            sender = w / 'sender'
            tree = command('mktree', cwd=sender)
            base = command('commit-tree', tree, '-m', 'base', cwd=sender)
            head = command('commit-tree', tree, '-p', base, '-m', 'head', cwd=sender)
            merge = command('commit-tree', tree, '-p', base, '-p', head, '-m', 'merge', cwd=sender)
            command('push', str(w / 'receiver.git'), base + ':refs/heads/main', cwd=sender)
            (w / 'merge').write_text(merge)
            intent = dict(number=1, base=base, head=head, target='main')
            (w / 'intent.json').write_text(json.dumps(intent))
            _, _, run, Journal, Queue = modules(root)
            q = Queue(str(w / 'queue')); q.set_head(1, head); q.enqueue(1)
            Journal(str(w / 'journal.sqlite3'))
            p = subprocess.Popen([sys.executable, '-B', str(Path(__file__).resolve()),
                                  '--owner', str(root), str(w)], env=env,
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                 start_new_session=True)
            try:
                wait_for(lambda: (w / 'ready').exists(), 'real SSH helper ready')
                self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'reserved')
                pid = int((w / 'helper.pid').read_text())
                os.kill(p.pid, signal.SIGKILL); p.wait(timeout=5)
                os.kill(pid, 0)
                with open(str(w / 'queue') + '.merge-lock', 'a') as lock:
                    try:
                        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    except BlockingIOError:
                        lock_available = False
                    else:
                        lock_available = True
                        fcntl.flock(lock, fcntl.LOCK_UN)
                class Recovery:
                    def send(self, value):
                        raise AssertionError('recovery must never resend')
                    def readback(self, value):
                        tip = command('--git-dir=' + str(w / 'receiver.git'), 'rev-parse', 'refs/heads/main')
                        if tip != merge:
                            return None
                        parents = command('--git-dir=' + str(w / 'receiver.git'), 'show', '-s', '--format=%P', tip)
                        actual_tree = command('--git-dir=' + str(w / 'receiver.git'), 'show', '-s', '--format=%T', tip)
                        if parents.split() != [base, head] or actual_tree != tree:
                            return None
                        return dict(value, merged=True, target_contains_merge=True, merge_sha=tip)
                def recover():
                    return run(Queue(str(w / 'queue')), Journal(str(w / 'journal.sqlite3')),
                               lambda *a: (_ for _ in ()).throw(AssertionError('no new authority')),
                               Recovery(), allow_write=False)
                # Parent-only lock release is measured separately from send authority.
                # On exact B it permits recovery entry, but reservation still blocks resend.
                if lock_available:
                    self.assertEqual(recover()['action'], 'hold')
                    self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'reserved')
                self.assertEqual((w / 'sends').read_text().splitlines(), ['send'])
                (w / 'release').write_text('release')
                wait_for(lambda: command('--git-dir=' + str(w / 'receiver.git'), 'rev-parse',
                                         'refs/heads/main') == merge, 'old transport completes')
                wait_for(lambda: not Path('/proc/' + str(pid)).exists()
                         or Path('/proc/' + str(pid) + '/stat').read_text().split()[2] == 'Z',
                         'transport no longer send-capable')
                self.assertEqual(recover()['action'], 'confirmed')
                self.assertEqual(recover()['action'], 'idle')
                self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'confirmed')
                self.assertEqual((w / 'sends').read_text().splitlines(), ['send'])
                self.assertFalse(lock_available,
                                 'production merge flock became available while actual SSH descendant lived; '
                                 'this proves lock-lifetime loss, not a second send')
            finally:
                if p.poll() is None:
                    p.kill(); p.wait(timeout=5)
                try:
                    os.killpg(p.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == '--owner':
        owner(Path(sys.argv[2]), Path(sys.argv[3]))
    else:
        unittest.main()
