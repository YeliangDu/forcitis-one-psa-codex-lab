"""R3 scratch composition ONLY. Fixture observations never authorize live sends."""
import argparse
import base64
from contextlib import contextmanager
import copy
import hashlib
import importlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import threading


def need(ok):
    if not ok:
        raise ValueError('R3 boundary or input refused')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def unique(pairs):
    value = {}
    for k, v in pairs:
        need(k not in value)
        value[k] = v
    return value


def physical(value):
    p = Path(value)
    need(p.is_absolute() and str(p) == value and p.resolve(strict=True) == p)
    need(all(not x.is_symlink() for x in (p, *p.parents)))
    return p


def raw(p, limit):
    p = physical(str(p)); s = p.stat()
    need(stat.S_ISREG(s.st_mode) and s.st_nlink == 1 and s.st_uid == os.geteuid()
         and not s.st_mode & 0o022 and 0 < s.st_size <= limit)
    value = p.read_bytes(); t = p.stat()
    need((s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns) ==
         (t.st_dev, t.st_ino, t.st_size, t.st_mtime_ns, t.st_ctime_ns) and len(value) == s.st_size)
    return value


def system_git(pin):
    """Root-owned system executable ONLY; private input policy stays unchanged."""
    path = physical('/usr/bin/git')
    for parent in path.parents:
        info = parent.stat()
        need(info.st_uid == 0 and not info.st_mode & 0o022
             and not os.access(parent, os.W_OK))
    before = path.lstat()
    need(stat.S_ISREG(before.st_mode) and before.st_uid == 0
         and before.st_nlink == 1 and not before.st_mode & 0o022
         and before.st_mode & 0o111 and not os.access(path, os.W_OK)
         and 0 < before.st_size <= 64 * 1024 * 1024)
    def identity(info):
        return (info.st_dev, info.st_ino, info.st_uid, info.st_mode, info.st_nlink,
                info.st_size, info.st_mtime_ns, info.st_ctime_ns)
    fd = os.open(str(path), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        need(identity(os.fstat(fd)) == identity(before))
        h = hashlib.sha256()
        remaining = before.st_size
        while remaining:
            block = os.read(fd, min(1024 * 1024, remaining))
            need(bool(block)); h.update(block); remaining -= len(block)
        need(os.read(fd, 1) == b'' and identity(os.fstat(fd)) == identity(before)
             and identity(path.lstat()) == identity(before) and h.hexdigest() == pin)
    finally:
        os.close(fd)
    return str(path)


def decode(value):
    return json.loads(value, object_pairs_hook=unique,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))


class GitGate:
    def __init__(self, scope, github, boundary):
        self.scope, self.github, self.boundary = scope, github, boundary
        self.active = None
        self.pushes = 0
        self.audit = []

    @contextmanager
    def lease(self, approval, runner_fd, merge_fd):
        need(self.active is None and approval == self.scope['policy']['expected_approval'])
        fds = (runner_fd, merge_fd)
        need(len(set(fds)) == 2)
        initial = []
        for fd, filename in zip(fds, ('control-upgrade.runner-lock', 'merge.queue.merge-lock')):
            path = physical(str(self.boundary()['common'] / 'merge-queue' / filename))
            opened, named = os.fstat(fd), path.lstat()
            identity = self.lock_identity(opened)
            need(identity == self.lock_identity(named))
            initial.append((str(path), identity))
        self.active = (os.getpid(), threading.get_ident(), fds, tuple(initial))
        try:
            yield
        finally:
            self.active = None

    @staticmethod
    def lock_identity(info):
        need(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
             and info.st_nlink == 1 and not info.st_mode & 0o022)
        return (info.st_dev, info.st_ino, info.st_uid, info.st_mode, info.st_nlink)

    def recheck_lease(self, layout):
        need(self.active is not None and self.active[:2] == (os.getpid(), threading.get_ident()))
        for fd, (path, initial), filename in zip(self.active[2], self.active[3],
                ('control-upgrade.runner-lock', 'merge.queue.merge-lock')):
            need(path == str(layout['common'] / 'merge-queue' / filename))
            named = physical(path).lstat()
            need(self.lock_identity(os.fstat(fd)) == initial == self.lock_identity(named))

    def __call__(self, args):
        layout = self.boundary()
        system_git(self.scope['git_sha256'])
        a = self.scope['policy']['expected_approval']
        base, head = a['base'], a['head']
        need(type(args) is list and all(type(x) is str for x in args))
        fds = ()
        if args == ['remote', '-v']:
            # Explicit synthetic ORIGIN projection required by unchanged writer.
            # ACTUAL config is checked as local file-only before every operation.
            return ('origin\tgit@github.com:YeliangDu/forcitis-one-psa-codex-lab.git (fetch)\n'
                    'origin\tgit@github.com:YeliangDu/forcitis-one-psa-codex-lab.git (push)\n')
        allowed = args in (['rev-parse', 'HEAD'], ['status', '--porcelain'])
        if self.scope['phase'] == 'once':
            allowed |= args == ['merge-tree', '--write-tree', base, head]
            allowed |= (len(args) == 8 and args[:1] == ['commit-tree']
                        and re.fullmatch('[0-9a-f]{40}', args[1]) is not None
                        and args[2:] == ['-p', base, '-p', head, '-m', f"Lab controlled main release #{a['number']}"])
        allowed |= (len(args) == 4 and args[:3] == ['show', '-s', '--format=%P']
                    and re.fullmatch('[0-9a-f]{40}', args[3]) is not None)
        allowed |= (len(args) == 4 and args[:3] == ['merge-base', '--is-ancestor', base]
                    and re.fullmatch('[0-9a-f]{40}', args[3]) is not None)
        if args[:1] == ['push']:
            need(self.scope['phase'] == 'once' and self.active is not None
                 and self.active[:2] == (os.getpid(), threading.get_ident()) and self.pushes == 0)
            need(len(args) == 5 and args[:4] == ['push', '--porcelain',
                 '--force-with-lease=refs/heads/main:' + base, 'origin']
                 and re.fullmatch('[0-9a-f]{40}:refs/heads/main', args[4]) is not None)
            # Native writer already checked ordered parents. Recheck actual immutable object too.
            merge = args[4].split(':')[0]
            parents = self.github.sh(['git', 'show', '-s', '--format=%P', merge], cwd=layout['work'], check=True,
                                      git_executable='/usr/bin/git').stdout
            need(parents.split() == [base, head])
            fds = self.active[2]
            allowed = True
        need(allowed)
        pushing = args[:1] == ['push']
        if pushing:
            self.recheck_lease(layout)  # Refusal here means zero dispatch attempts.
            self.pushes += 1  # Record only after final checks, before subprocess.
        self.audit.append(dict(argv_kind=args[0], inherited_control_fds=len(fds)))
        result = self.github.sh(['git', *args], cwd=layout['work'], check=not pushing,
                                timeout=60, transport_fds=fds, git_executable='/usr/bin/git')
        if pushing and result.returncode:
            # Nonzero/124 is unknown, not retry authority. Do not catch signals.
            raise RuntimeError('fixture-push-outcome-unknown')
        return result.stdout


def main():
    p = argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument('--scope', required=True); p.add_argument('--scope-sha256', required=True)
    p.add_argument('--phase', choices=('admit', 'once', 'recover', 'successor'), required=True)
    args = p.parse_args()
    need(sys.platform == 'linux' and os.geteuid() != 0
         and os.environ.get('RUNNER_ENVIRONMENT') == 'github-hosted')
    temporary = physical(os.environ['RUNNER_TEMP'])
    content = raw(Path(args.scope), 32 * 1024 * 1024)
    need(sha(content) == args.scope_sha256)
    scope = decode(content)
    need(scope['kind'] == 'lab-r3-disposable-scope/1' and scope['phase'] == args.phase)
    root = physical(scope['root'])
    need(root.parent == temporary and root.name.startswith('lab-r3-') and root.stat().st_uid == os.geteuid()
         and root.stat().st_mode & 0o077 == 0)
    work = physical(str(root / 'forcitis-one-psa-codex-lab'))
    common = physical(str(work / '.git'))
    receiver = physical(str(root / 'receiver.git'))
    runtime = physical(str(root / 'runtime'))
    need(common.is_dir() and receiver.is_dir() and runtime.is_dir())
    # No transport/credentials inherited from invoking session, even in fixture mode.
    os.environ.clear()
    os.environ.update(PATH='/usr/bin:/bin', HOME=str(root), LANG='C.UTF-8', TZ='UTC',
        GIT_CONFIG_GLOBAL='/dev/null', GIT_CONFIG_NOSYSTEM='1', GIT_TERMINAL_PROMPT='0',
        GIT_ALLOW_PROTOCOL='file', GIT_CONFIG_COUNT='3',
        GIT_CONFIG_KEY_0='core.hooksPath', GIT_CONFIG_VALUE_0='/dev/null',
        GIT_CONFIG_KEY_1='credential.helper', GIT_CONFIG_VALUE_1='',
        GIT_CONFIG_KEY_2='protocol.file.allow', GIT_CONFIG_VALUE_2='always',
        GIT_AUTHOR_NAME='R3 Fixture', GIT_AUTHOR_EMAIL='r3@example.invalid',
        GIT_COMMITTER_NAME='R3 Fixture', GIT_COMMITTER_EMAIL='r3@example.invalid',
        PYTHONDONTWRITEBYTECODE='1')
    configs = scope['config_sha256']
    def boundary():
        for directory in (common, receiver):
            need(physical(str(directory)) == directory)
            config = raw(directory / 'config', 16384)
            need(sha(config) == configs[str(directory.relative_to(root))])
            # Require plain fixture config, no includes, aliases, external program or URL rewriting.
            import configparser
            cp = configparser.RawConfigParser(strict=True); cp.read_string(config.decode())
            allowed = {'core': {'repositoryformatversion', 'filemode', 'bare', 'logallrefupdates'}}
            if directory == common:
                allowed['remote "origin"'] = {'url', 'fetch'}
                need(cp.get('remote "origin"', 'url') == receiver.as_uri())
                need(cp.get('core', 'bare') == 'false')
            else:
                need(cp.get('core', 'bare') == 'true')
            need(set(cp.sections()) == set(allowed))
            for section in cp.sections():
                need(set(cp[section]) <= allowed[section])
            for item in directory.rglob('*'):
                need(not item.is_symlink())
                if item.is_file():
                    need(item.stat().st_nlink == 1)
            packed = directory / 'packed-refs'
            need(not packed.exists() or b' refs/replace/' not in raw(packed, 1024 * 1024))
            for forbidden in ('objects/info/alternates', 'commondir', 'info/grafts', 'refs/replace'):
                need(not (directory / forbidden).exists())
        return dict(work=work, common=common)
    boundary()  # BEFORE imports, V2 journal open, or installer admission.
    system_git(scope['git_sha256'])
    files = scope['runtime_files_sha256']
    core = {'scripts/merge/mq_lab_cutover_native.py': '998b5fadfb6c3e7ef805d11ce68fca13418a0f4b2c897e5df63f76fd4bfee15b', 'scripts/merge/mq_lab_control_install.py': '107f52c23ffa9e0a34b8286d5a228a18a9e6a008445d020775420725a883888f', 'scripts/merge/mq_github.py': '7bb39864eb79087f55db58e40e59ed4d02cbb499d0adf5ed14f53c2a3d7c96ba', 'scripts/merge/mq_queue.py': '358b844ff6132f3557db863e2e5f71a4e42f0836715ec21ef2431891bbf0e94a'}
    need(all(files.get(path) == pin for path, pin in core.items()))
    actual = {str(f.relative_to(runtime)) for f in runtime.rglob('*') if f.is_file()}
    need(actual == set(files) and len(actual) <= 5000)
    for name, pin in files.items():
        need(not name.startswith('/') and all(x not in ('', '.', '..') for x in name.split('/'))
             and sha(raw(runtime / name, 32 * 1024 * 1024)) == pin)
    sys.path[:0] = [str(runtime / 'scripts/merge'), str(runtime / 'scripts/session/codex_lab')]
    native = importlib.import_module('mq_lab_cutover_native')
    github = importlib.import_module('mq_github')
    installer = importlib.import_module('mq_lab_control_install')
    gate = GitGate(scope, github, boundary)
    # Explicit captured-observation fixture; no native network transport/defaults.
    def get(path, pages=False, binary=False):
        need(type(path) is str and not path.startswith('/') and '://' not in path)
        key = json.dumps([path, pages, binary], separators=(',', ':'))
        # A once snapshot is never upgraded into a post-send confirmation.
        need(not (args.phase == 'once' and gate.pushes > 0))
        row = scope['observations'][key]
        result = base64.b64decode(row['base64'], validate=True) if binary else copy.deepcopy(row['json'])
        if path == 'git/ref/heads/main':
            boundary()
            system_git(scope['git_sha256'])
            actual = github.sh(['git', '--git-dir=' + str(receiver), 'rev-parse',
                                'refs/heads/main'], cwd=work, check=True,
                                git_executable='/usr/bin/git').stdout.strip()
            need(not binary and result['object']['sha'] == actual)
        return result
    number = scope['policy']['expected_approval']['number']
    journal = native.ReleaseJournalV2(common / 'merge-queue/releases.sqlite3')
    journal.storage.check()  # Existing V2 only, no prepare/migration/schema creation.
    if args.phase == 'once':
        need(journal.load_upgrade(number) is None)
        result = native.run_native_upgrade(number, common, scope['policy'], get, gate,
                                           transport_lease=gate.lease)
    else:
        if args.phase in ('recover', 'successor'):
            need(journal.load_upgrade(number) is not None)
        ports = native.NativePorts(common, scope['policy'], get, gate, journal)
        if args.phase == 'admit':
            result = installer.admit_control_upgrade(number, common, journal, ports)
        else:
            # successor is a separately loaded M/policy invocation; native method validates M.
            result = installer.recover_control_upgrade_once(number, common, journal, ports)
    if args.phase == 'once' and gate.pushes:
        need(journal.load_upgrade(number)['phase'] == 'reserved' and result.get('action') == 'hold')
    print(json.dumps(dict(scope='disposable-fixture-only', result=result,
        send_outcome=('pending-unknown-needs-independent-snapshot' if gate.pushes else 'no-send-attempt'),
        scope_sha256=args.scope_sha256,
        observations_sha256=sha(json.dumps(scope['observations'], sort_keys=True, separators=(',', ':')).encode()),
        push_attempts=gate.pushes, transport_audit=gate.audit, native_authenticated=False,
        canonical_authority=False, activation_authority=False), sort_keys=True))


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('{"action":"hold","reason":"R3 boundary/input/capability refused","canonical_authority":false}')
        raise SystemExit(78)
