"""Hosted real OpenSSH + V2 integration; independently selected exact source.

This exercises the production argv method, runtime, Queue and migrated V2 durable journal via actual run_once.
Real OpenSSH reaches disposable sshd/receive-pack in the private namespace.
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
    from mq_lab_active import run_once
    from mq_lab_journal_v2 import ReleaseJournalV2
    def run_iteration(queue, journal, evidence, writer, *, allow_write=False):
        return run_once(queue, journal,
                        lambda expected: evidence(expected['number'], expected['head']),
                        writer, allow_write=allow_write)
    from mq_queue import Queue
    return ProductionPorts, sh, run_iteration, ReleaseJournalV2, Queue


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
                             'ssh://127.0.0.1:40363/receiver',
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



def ancestors(pid):
    result = []
    while pid > 1 and len(result) < 32:
        row = process_identity(pid)
        if row is None:
            break
        result.append(row)
        pid = row['ppid']
    return result


def executable(row, name):
    try:
        return Path('/proc/' + str(row['pid']) + '/exe').resolve(strict=True) == Path(name).resolve(strict=True)
    except (FileNotFoundError, PermissionError):
        return False


def stop_exact(row):
    fd = os.pidfd_open(row['pid'])
    try:
        if same_live(row):
            try:
                signal.pidfd_send_signal(fd, signal.SIGKILL)
            except ProcessLookupError:
                pass
    finally:
        os.close(fd)


class OpenSshV2IntegratedTest(unittest.TestCase):
    def test_local_lifetime_then_remote_unknown_v2_reconciliation(self):
        if sys.platform != 'linux' or os.geteuid() == 0:
            raise RuntimeError('CAPABILITY_HOLD: non-root Linux fixture required')
        if 'NoNewPrivs:\t1' not in Path('/proc/self/status').read_text():
            raise RuntimeError('CAPABILITY_HOLD: no-new-privileges required')
        root = Path(os.environ['EXACT_B_SOURCE_ROOT']).resolve(strict=True)
        w = Path(os.environ['WIRE_WORK']).resolve(strict=True)
        if not hasattr(os, 'pidfd_open') or not hasattr(signal, 'pidfd_send_signal'):
            raise RuntimeError('CAPABILITY_HOLD: pidfd unavailable')
        env = {'PATH': '/usr/bin:/bin', 'HOME': str(w / 'home'), 'LANG': 'C.UTF-8',
               'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': '/dev/null',
               'GIT_TERMINAL_PROMPT': '0', 'GIT_ALLOW_PROTOCOL': 'file:ssh',
               'GIT_SSH_VARIANT': 'ssh', 'GIT_SSH_COMMAND': '/usr/bin/ssh -F ' + str(w / 'client.conf'),
               'GIT_AUTHOR_NAME': 'Fixture', 'GIT_AUTHOR_EMAIL': 'fixture@invalid',
               'GIT_COMMITTER_NAME': 'Fixture', 'GIT_COMMITTER_EMAIL': 'fixture@invalid',
               'PYTHONDONTWRITEBYTECODE': '1'}
        target = 'ssh://127.0.0.1:40363/receiver'
        def git(*args, at=None):
            return subprocess.check_output(['/usr/bin/git', *args], cwd=at, env=env,
                                           text=True, input='', timeout=10).strip()
        receiver = w / 'receiver.git'; sender = w / 'sender'
        git('init', '--bare', str(receiver)); git('init', str(sender))
        tree = git('mktree', at=sender)
        base = git('commit-tree', tree, '-m', 'base', at=sender)
        head = git('commit-tree', tree, '-p', base, '-m', 'head', at=sender)
        merge = git('commit-tree', tree, '-p', base, '-p', head, '-m', 'merge', at=sender)
        git('push', str(receiver), base + ':refs/heads/main', at=sender)
        self.assertEqual(git('ls-remote', target, 'refs/heads/main', at=sender).split(), [base, 'refs/heads/main'])
        # Wrong-key test must not inherit the approved IdentityFile from client.conf.
        negative = subprocess.run(['/usr/bin/ssh', '-F', '/dev/null', '-p', '40363',
                                   '-l', __import__('pwd').getpwuid(os.geteuid()).pw_name,
                                   '-i', str(w / 'wrong-client'), '-o', 'IdentitiesOnly=yes',
                                   '-o', 'IdentityAgent=none', '-o', 'BatchMode=yes',
                                   '-o', 'StrictHostKeyChecking=yes', '-o', 'GlobalKnownHostsFile=/dev/null',
                                   '-o', 'UserKnownHostsFile=' + str(w / 'known_hosts'),
                                   '127.0.0.1', 'not-git'], env=env, capture_output=True, timeout=8)
        self.assertEqual(negative.returncode, 255, 'wrong key must fail authentication')
        denied = subprocess.run(['/usr/bin/ssh', '-F', str(w / 'client.conf'), '-p', '40363',
                                 '127.0.0.1', 'not-git'], env=env, capture_output=True, timeout=8)
        self.assertEqual(denied.returncode, 77)
        intent = dict(number=1, base=base, head=head, target='main')
        (w / 'intent.json').write_text(json.dumps(intent)); (w / 'merge').write_text(merge)
        import shlex
        hook = receiver / 'hooks/pre-receive'
        hook.write_text('#!/bin/bash\nset -eu\nread -r old new ref\n'
                        + 'test "$old" = ' + shlex.quote(base) + '\n'
                        + 'test "$new" = ' + shlex.quote(merge) + '\n'
                        + 'test "$ref" = refs/heads/main\n'
                        + 'test -n "${GIT_QUARANTINE_PATH:-}"\n'
                        + 'test -d "$GIT_QUARANTINE_PATH"\n'
                        + 'test "$(/usr/bin/git cat-file -t "$new")" = commit\n'
                        + 'test "$(/usr/bin/git show -s --format=%P "$new")" = ' + shlex.quote(base + ' ' + head) + '\n'
                        + 'test "$(/usr/bin/git show -s --format=%T "$new")" = ' + shlex.quote(tree) + '\n'
                        + '/usr/bin/git cat-file -e ' + shlex.quote(head + '^{commit}') + '\n'
                        + 'printf "%s %s %s %s\n" "$BASHPID" "$old" "$new" "$ref" > '
                        + shlex.quote(str(w / 'pre-receive.pending')) + '\n'
                        + 'mv ' + shlex.quote(str(w / 'pre-receive.pending')) + ' ' + shlex.quote(str(w / 'pre-receive')) + '\n'
                        + 'for i in $(seq 1 600); do test ! -e ' + shlex.quote(str(w / 'release'))
                        + ' || exit 0; sleep .05; done\nexit 89\n')
        hook.chmod(0o700)
        _, _, run, Journal, Queue = modules(root)
        q = Queue(str(w / 'queue')); q.set_head(1, head); q.enqueue(1)
        from mq_lab_journal import ReleaseJournal
        from mq_lab_journal_v2 import migrate_v1
        import hashlib
        with (w / 'fixture.runner-lock').open('a') as runner:
            fcntl.flock(runner, fcntl.LOCK_EX)
            with q.merge_lock():
                ReleaseJournal(str(w / 'journal.sqlite3'))
                digest = hashlib.sha256((w / 'journal.sqlite3').read_bytes()).hexdigest()
                result = migrate_v1(str(w / 'journal.sqlite3'), str(w / 'backup.sqlite3'), digest)
                if result.get('action') != 'migrated':
                    raise RuntimeError('CAPABILITY_HOLD: V2 migration: ' + repr(result))
                Journal(str(w / 'journal.sqlite3'))
        p = subprocess.Popen([sys.executable, '-B', str(Path(__file__).resolve()), '--owner', str(root), str(w)],
                             env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                             start_new_session=True)
        remote_chain = []
        try:
            wait_for(lambda: (w / 'pre-receive').exists(), 'real remote pre-receive after pack')
            remote_pid, old, new, ref = (w / 'pre-receive').read_text().split()
            self.assertEqual([old, new, ref], [base, merge, 'refs/heads/main'])
            print(json.dumps(dict(event='quarantine-hook-verified', parents=[base, head], tree=tree, merge=merge)), flush=True)
            self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'reserved')
            remote_chain = ancestors(int(remote_pid))
            def remote_receive(row):
                try:
                    return b'receive-pack' in Path('/proc/' + str(row['pid']) + '/cmdline').read_bytes()
                except FileNotFoundError:
                    return False
            receivers = [row for row in remote_chain[1:] if remote_receive(row)]
            listener = json.loads((w / 'listener.json').read_text())
            if not receivers or not any(row['pid'] == listener['pid'] and row['start'] == listener['start'] for row in remote_chain):
                raise RuntimeError('CAPABILITY_HOLD: independent sshd/receive-pack ancestry missing')
            self.assertEqual(listener['exe'], '/usr/sbin/sshd')
            self.assertNotIn(p.pid, [row['pid'] for row in remote_chain])
            receive_identity = receivers[0]
            hook_identity = remote_chain[0]
            owner_identity = process_identity(p.pid)
            if owner_identity is None or not same_live(owner_identity):
                raise RuntimeError('CAPABILITY_HOLD: expected owner is not live')
            def local_chain():
                for proc in Path('/proc').iterdir():
                    if not proc.name.isdigit():
                        continue
                    row = process_identity(int(proc.name))
                    if not row or not executable(row, '/usr/bin/ssh'):
                        continue
                    chain = ancestors(row['pid'])
                    pids = [item['pid'] for item in chain]
                    if p.pid in pids:
                        chain = chain[:pids.index(p.pid) + 1]
                        if chain[-1]['start'] != owner_identity['start'] or not same_live(owner_identity):
                            raise RuntimeError('CAPABILITY_HOLD: expected owner identity moved')
                        if any(child['ppid'] != parent['pid'] for child, parent in zip(chain, chain[1:])):
                            raise RuntimeError('CAPABILITY_HOLD: local ancestor links moved')
                        return chain
            chain = wait_for_value(local_chain, 'actual SSH/shell/Git/owner chain')
            clients = [row for row in chain if executable(row, '/usr/bin/ssh')]
            gits = [row for row in chain if executable(row, '/usr/bin/git')]
            if len(clients) != 1 or len(gits) != 1:
                raise RuntimeError('CAPABILITY_HOLD: ambiguous local Git/SSH ancestry')
            if (chain[0] != clients[0] or chain[-1]['pid'] != p.pid
                    or not 0 < chain.index(gits[0]) < len(chain) - 1
                    or not all(same_live(row) for row in chain)):
                raise RuntimeError('CAPABILITY_HOLD: local SSH/Git/owner order or identity differs')
            for row in chain:
                self.assertEqual(row['pgid'], p.pid)
                self.assertEqual(row['session'], p.pid)
            receiver_arg = '--git-dir=' + str(receiver)
            self.assertEqual(git(receiver_arg, 'rev-parse', 'refs/heads/main'), base)
            lock_path = Path(str(w / 'queue') + '.merge-lock')
            lock_identity = (lock_path.stat().st_dev, lock_path.stat().st_ino)
            stop_exact(process_identity(p.pid)); p.wait(timeout=5)
            if not all(same_live(row) for row in clients + gits):
                raise RuntimeError('CAPABILITY_HOLD: local transport exited before lifetime sample')
            with lock_path.open('a') as lock:
                self.assertEqual((os.fstat(lock.fileno()).st_dev, os.fstat(lock.fileno()).st_ino), lock_identity)
                try:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    local_lock_available = False
                else:
                    local_lock_available = True
                    fcntl.flock(lock, fcntl.LOCK_UN)
            if not all(same_live(row) for row in clients + gits):
                raise RuntimeError('CAPABILITY_HOLD: local capability lost during lock sample')
            self.assertEqual((lock_path.stat().st_dev, lock_path.stat().st_ino), lock_identity)
            print(json.dumps(dict(event='local-live-lifetime', available=local_lock_available,
                                  client_chain=chain, remote_chain=remote_chain)), flush=True)
            # All client-side senders die; remote lifetime is now a separate gate.
            for row in live_group(p.pid):
                stop_exact(row)
            wait_for(lambda: not live_group(p.pid), 'all local senders gone')
            if not same_live(hook_identity) or not same_live(receive_identity):
                raise RuntimeError('CAPABILITY_HOLD: remote could not survive client loss')
            self.assertEqual(git(receiver_arg, 'rev-parse', 'refs/heads/main'), base)
            class Recovery:
                wrong = False
                sends = 0
                def send(self, value):
                    self.sends += 1
                    raise AssertionError('V2 recovery must never resend')
                def readback(self, value):
                    tip = git(receiver_arg, 'rev-parse', 'refs/heads/main')
                    if self.wrong:
                        return dict(value, head='0' * 40, merged=True, target_contains_merge=True, merge_sha=merge)
                    if tip != merge:
                        return None
                    if git(receiver_arg, 'show', '-s', '--format=%P', tip).split() != [base, head]:
                        return None
                    if git(receiver_arg, 'show', '-s', '--format=%T', tip) != tree:
                        return None
                    git(receiver_arg, 'merge-base', '--is-ancestor', base, tip)
                    return dict(value, merged=True, target_contains_merge=True, merge_sha=tip)
            observer = Recovery()
            def recover():
                return run(Queue(str(w / 'queue')), Journal(str(w / 'journal.sqlite3')),
                           lambda *a: (_ for _ in ()).throw(AssertionError('no new authority')),
                           observer, allow_write=False)
            for wrong in (False, True):
                observer.wrong = wrong
                self.assertEqual(recover()['action'], 'hold')
                self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'reserved')
                self.assertEqual(observer.sends, 0)
            observer.wrong = False
            self.assertEqual((w / 'sends').read_text().splitlines(), ['send'])
            if not same_live(hook_identity) or not same_live(receive_identity):
                raise RuntimeError('CAPABILITY_HOLD: remote capability lost before release')
            (w / 'release').touch()
            wait_for(lambda: not same_live(hook_identity), 'remote hook exit')
            wait_for(lambda: not same_live(receive_identity), 'real receive-pack exit')
            if git(receiver_arg, 'rev-parse', 'refs/heads/main') != merge:
                raise RuntimeError('CAPABILITY_HOLD: actual remote did not complete exact late effect')
            observer.wrong = True
            self.assertEqual(recover()['action'], 'hold')
            self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['status'], 'reserved')
            observer.wrong = False
            self.assertEqual(recover()['action'], 'confirmed')
            self.assertEqual(recover()['action'], 'idle')
            self.assertEqual(Journal(str(w / 'journal.sqlite3')).load(1)['merge_sha'], merge)
            self.assertEqual(observer.sends, 0)
            self.assertEqual((w / 'sends').read_text().splitlines(), ['send'])
            print(json.dumps(dict(event='real-openssh-v2-reconciled', merge=merge, sends=1,
                                  recovery_sends=observer.sends, reservation='confirmed')), flush=True)
        finally:
            for row in live_group(p.pid):
                stop_exact(row)
            if p.poll() is None:
                p.wait(timeout=5)
            wait_for(lambda: not live_group(p.pid), 'local group cleanup')
            for row in remote_chain:
                # Never stop the shared disposable listener/PID1 here. Namespace exit owns it.
                if row['pid'] > 1 and row['pid'] != remote_chain[-1]['pid'] and same_live(row):
                    try:
                        uid_line = next(line for line in Path('/proc/' + str(row['pid']) + '/status').read_text().splitlines() if line.startswith('Uid:'))
                        owned = int(uid_line.split()[1]) == os.geteuid()
                    except (FileNotFoundError, StopIteration):
                        owned = False
                    if owned and not executable(row, '/usr/sbin/sshd'):
                        stop_exact(row)
        self.assertFalse(local_lock_available,
                         'production merge flock became available while actual SSH descendant lived; '
                         'this proves lock-lifetime loss, not a second send')


def wait_for_value(predicate, label):
    values = []
    def capture():
        value = predicate()
        if value:
            values.append(value)
        return bool(value)
    wait_for(capture, label)
    return values[0]


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == '--owner':
        owner(Path(sys.argv[2]), Path(sys.argv[3]))
    else:
        unittest.main()
