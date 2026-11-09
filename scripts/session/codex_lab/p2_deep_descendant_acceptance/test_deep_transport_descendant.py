"""Hosted Linux only: additional Git-process-loss comparison against exact B.

This exercises the production argv method, runtime, Queue and V1 durable journal.
The surviving controlled SSH helper performs a late local CAS effect after Git dies.
This is not real SSH or completion of the original push protocol.
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


def process_identity(pid):
    try:
        raw = Path('/proc/' + str(pid) + '/stat').read_text()
        fields = raw[raw.rfind(')') + 2:].split()
        return dict(pid=pid, state=fields[0], ppid=int(fields[1]),
                    pgid=int(fields[2]), session=int(fields[3]), start=fields[19])
    except FileNotFoundError:
        return None


def same_live(identity):
    now = process_identity(identity['pid'])
    return now is not None and now['start'] == identity['start'] and now['state'] not in ('Z', 'X')


def live_group(group):
    result = []
    for item in Path('/proc').iterdir():
        if item.name.isdigit():
            record = process_identity(int(item.name))
            if record and record['pgid'] == group and record['state'] not in ('Z', 'X'):
                result.append(record)
    return result


HELPER = r"""import json, os, pathlib, shlex, subprocess, sys, tempfile, time
w=pathlib.Path(os.environ['FIXTURE_ROOT'])

def publish(name, text):
    temporary=w/(name+'.pending')
    with temporary.open('w') as stream:
        stream.write(text); stream.flush(); os.fsync(stream.fileno())
    os.replace(temporary, w/name)

def main():
    parts=shlex.split(sys.argv[-1])
    if parts != ['git-receive-pack', '/receiver']:
        raise RuntimeError('unexpected transport argv')
    publish('helper.pid', str(os.getpid()))
    publish('ready', 'ready')
    limit=time.monotonic()+30
    while not (w/'release').exists():
        if time.monotonic()>limit: raise RuntimeError('barrier timeout')
        time.sleep(.02)
    # Controlled late-effect model: not completion of the broken push protocol.
    intent=json.loads((w/'intent.json').read_text())
    merge=(w/'merge').read_text()
    with tempfile.TemporaryFile() as errors:
        result=subprocess.run([os.environ['FIXTURE_GIT'], '--git-dir='+str(w/'receiver.git'),
                               'update-ref', 'refs/heads/main', merge, intent['base']],
                              stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                              stderr=errors, timeout=10)
        errors.seek(0)
        diagnostic=errors.read(2049)
    detail=dict(update_ref_exit_code=result.returncode,
                stderr=diagnostic[:2048].decode('utf-8', errors='replace'),
                stderr_truncated=len(diagnostic)>2048)
    if result.returncode:
        return result.returncode, detail
    publish('late-effect', merge)
    return 0, detail

try:
    code, detail=main()
except BaseException as error:
    code=125
    detail=dict(error_type=type(error).__name__, stderr=str(error)[:2048])
publish('helper-exit.json', json.dumps(dict(helper_exit_code=code, **detail), sort_keys=True))
raise SystemExit(code)
"""


class DeepDescendantTest(unittest.TestCase):
    def test_git_and_owner_death_keep_lock_until_capable_helper_exits(self):
        if sys.platform != 'linux':
            raise RuntimeError('CAPABILITY_HOLD: hosted Linux required')
        if not hasattr(os, 'pidfd_open') or not hasattr(signal, 'pidfd_send_signal'):
            raise RuntimeError('CAPABILITY_HOLD: Linux pidfd identity-bound signals required')
        root = Path(os.environ['EXACT_B_SOURCE_ROOT']).resolve(strict=True)
        git = shutil.which('git')
        if not git:
            raise RuntimeError('CAPABILITY_HOLD: real Git missing')
        with tempfile.TemporaryDirectory(prefix='p2-deep-descendant-') as temporary:
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
            # Object staging is separate from the deferred main CAS.
            command('push', str(w / 'receiver.git'), merge + ':refs/fixture/prestaged-merge', cwd=sender)
            receiver_arg = '--git-dir=' + str(w / 'receiver.git')
            for object_id in (base, head, merge):
                if command(receiver_arg, 'cat-file', '-t', object_id) != 'commit':
                    raise RuntimeError('CAPABILITY_HOLD: missing prestaged commit')
            if command(receiver_arg, 'rev-parse', 'refs/fixture/prestaged-merge') != merge:
                raise RuntimeError('CAPABILITY_HOLD: wrong prestaged merge ref')
            if command(receiver_arg, 'show', '-s', '--format=%P', merge).split() != [base, head]:
                raise RuntimeError('CAPABILITY_HOLD: prestaged merge parents differ')
            if command(receiver_arg, 'rev-parse', 'refs/heads/main') != base:
                raise RuntimeError('CAPABILITY_HOLD: main moved during object staging')
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
                helper_identity = process_identity(pid)
                self.assertIsNotNone(helper_identity)
                self.assertTrue(same_live(helper_identity))
                chain = [helper_identity]
                while chain[-1]['ppid'] != p.pid:
                    parent = process_identity(chain[-1]['ppid'])
                    if parent is None or parent['pid'] <= 1 or len(chain) > 8:
                        raise RuntimeError('CAPABILITY_HOLD: unverified actual ancestry')
                    chain.append(parent)
                git_candidates = [row for row in chain[1:]
                                  if Path('/proc/' + str(row['pid']) + '/exe').resolve() == Path(git).resolve()
                                  and b'push' in Path('/proc/' + str(row['pid']) + '/cmdline').read_bytes().split(b'\0')]
                self.assertEqual(len(git_candidates), 1, 'exactly one real push Git ancestor required')
                git_identity = git_candidates[0]
                owner_identity = process_identity(p.pid)
                self.assertIsNotNone(owner_identity)
                for row in chain + [owner_identity]:
                    self.assertEqual(row['pgid'], p.pid)
                    self.assertEqual(row['session'], p.pid)
                # Open identity-bound handles before signaling; never kill a reused PID.
                handles = [os.pidfd_open(row['pid']) for row in (git_identity, helper_identity, owner_identity)]
                try:
                    self.assertTrue(all(same_live(row) for row in (git_identity, helper_identity, owner_identity)))
                    signal.pidfd_send_signal(handles[0], signal.SIGKILL)
                    wait_for(lambda: not same_live(git_identity), 'real push Git dead')
                    self.assertTrue(same_live(helper_identity), 'helper must survive Git death')
                    if p.poll() is None:
                        try:
                            signal.pidfd_send_signal(handles[2], signal.SIGKILL)
                        except ProcessLookupError:
                            pass  # Git failure may already have ended the owner.
                    p.wait(timeout=5)
                    self.assertTrue(same_live(helper_identity), 'helper must survive owner death')
                finally:
                    for fd in handles:
                        os.close(fd)
                print(json.dumps(dict(event='verified-ancestry-and-Git-loss', chain=chain,
                                      owner=owner_identity, helper_survived=True)), flush=True)
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
                self.assertTrue(same_live(helper_identity))
                self.assertFalse((w / 'late-effect').exists())
                (w / 'release').write_text('release')
                def late_effect_ready():
                    diagnostic = w / 'helper-exit.json'
                    if diagnostic.exists():
                        result = json.loads(diagnostic.read_text())
                        if result.get('helper_exit_code') != 0:
                            raise RuntimeError('CAPABILITY_HOLD: helper failure: ' + json.dumps(result)[:3072])
                    return (w / 'late-effect').exists()
                wait_for(late_effect_ready, 'capable helper late effect')
                self.assertEqual((w / 'late-effect').read_text(), merge)
                wait_for(lambda: command('--git-dir=' + str(w / 'receiver.git'), 'rev-parse',
                                         'refs/heads/main') == merge, 'old transport completes')
                wait_for(lambda: not same_live(helper_identity), 'final capable helper exited')
                self.assertEqual(recover()['action'], 'confirmed')
                self.assertEqual(recover()['action'], 'idle')
                self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'confirmed')
                self.assertEqual((w / 'sends').read_text().splitlines(), ['send'])
                self.assertFalse(lock_available,
                                 'production merge flock became available after Git and owner death while capable helper lived; '
                                 'this proves lock-lifetime loss, not a second send')
            finally:
                if p.poll() is None:
                    p.kill(); p.wait(timeout=5)
                try:
                    os.killpg(p.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                wait_for(lambda: not live_group(p.pid), 'all disposable group members stopped')
                diagnostic = w / 'helper-exit.json'
                if diagnostic.exists():
                    print('helper-diagnostic: ' + diagnostic.read_text()[:4096], flush=True)
                else:
                    print('helper-diagnostic: unavailable (capability evidence incomplete)', flush=True)


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == '--owner':
        owner(Path(sys.argv[2]), Path(sys.argv[3]))
    else:
        unittest.main()
