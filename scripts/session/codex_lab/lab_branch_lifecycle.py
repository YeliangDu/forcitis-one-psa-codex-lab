"""Durable card bindings and effect fences; no GitHub/Git/session side effects."""
from contextlib import contextmanager
import copy
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import tempfile

REPOSITORY = 'YeliangDu/forcitis-one-psa-codex-lab'
RID = 1449415666
ROLES = {'one-lead', 'one-test', 'one-design', 'one-platform', 'one-dev-org', 'one-dev-identity'}


def require(value, reason):
    if not value:
        raise ValueError(reason)


def sha(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{40}', value) is not None


def positive(value):
    return type(value) is int and 0 < value <= 2**63-1


def branch_name(value):
    require(type(value) is str and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._/-]{0,180}', value)
            and '..' not in value and '//' not in value and not value.endswith(('/', '.', '.lock'))
            and value != 'main' and not value.startswith('refs/'), 'unsafe-or-main-business-branch')
    return value


def fixed_repo(value, full=False):
    require(type(value) is dict and type(value.get('id')) is int and value['id'] == RID
            and value.get('full_name') == REPOSITORY, 'foreign-repository')
    if full:
        require(value.get('private') is False, 'repository-not-explicit-public')


def unique(pairs):
    value = {}
    for key, item in pairs:
        require(key not in value, 'duplicate-state-key')
        value[key] = item
    return value


def nonfinite(_value):
    raise ValueError('nonfinite-state')


def regular(info):
    require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and info.st_nlink == 1
            and stat.S_IMODE(info.st_mode) == 0o600, 'unsafe-state-or-lock-file')


class Store:
    def __init__(self, path):
        self.path = Path(path)
        require(self.path.is_absolute() and '..' not in self.path.parts
                and self.path.parent.resolve(strict=True) == self.path.parent, 'unsafe-state-path')
        info = self.path.parent.stat()
        require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.geteuid() and not info.st_mode & 0o022,
                'unsafe-state-parent')
        self.parent_id = info.st_dev, info.st_ino
        self.data = None

    def check(self):
        p = self.path.parent
        require(p.resolve(strict=True) == p and (p.stat().st_dev, p.stat().st_ino) == self.parent_id,
                'state-parent-moved')

    @contextmanager
    def locked(self):
        self.check()
        path = self.path.with_name(self.path.name + '.lock')
        fd = os.open(path, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        try:
            regular(os.fstat(fd))
            fcntl.flock(fd, fcntl.LOCK_EX)
            require((os.fstat(fd).st_dev, os.fstat(fd).st_ino) == (path.lstat().st_dev,path.lstat().st_ino), 'state-lock-moved')
            self.data = self.load()
            yield self
        finally:
            os.close(fd)

    def load(self):
        self.check()
        try:
            fd = os.open(self.path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        except FileNotFoundError:
            return dict(version=1, repository=REPOSITORY, repository_id=RID, revision=0, bindings={})
        with os.fdopen(fd, 'rb') as stream:
            before = os.fstat(stream.fileno());regular(before)
            require(before.st_size <= 256*1024, 'state-too-large')
            raw = stream.read(256*1024+1)
            identity = lambda x:(x.st_dev,x.st_ino,x.st_size,x.st_mtime_ns,x.st_ctime_ns)
            require(len(raw)==before.st_size and identity(before)==identity(os.fstat(stream.fileno()))
                    ==identity(self.path.lstat()), 'state-changed')
        value = json.loads(raw.decode('utf-8'),object_pairs_hook=unique,parse_constant=nonfinite)
        require(type(value) is dict and set(value)=={'version','repository','repository_id','revision','bindings'}
                and type(value['version']) is int and value['version']==1 and value['repository']==REPOSITORY
                and type(value['repository_id']) is int and value['repository_id']==RID
                and type(value['revision']) is int and value['revision']>=0 and type(value['bindings']) is dict,'invalid-state-schema')
        for key,row in value['bindings'].items():
            require(type(row) is dict and set(row)=={'binding','phase','operations'} and type(row['operations']) is dict
                    and row['phase'] in ('dispatching','bound','retired'), 'invalid-binding-row')
            b=row['binding'];require(type(b) is dict and set(b)=={'repository','repository_id','card','role','worktree','base','branch','head','target','pr_number','common'},'invalid-binding-shape')
            require(positive(b['card']) and key==str(b['card']) and b['repository']==REPOSITORY
                    and type(b['repository_id']) is int and b['repository_id']==RID and b['role'] in ROLES
                    and sha(b['base']) and sha(b['head']) and b['target']=='main'
                    and (b['pr_number'] is None or positive(b['pr_number'])),'invalid-binding-identity')
            branch_name(b['branch'])
            require(type(b['worktree']) is str and Path(b['worktree']).is_absolute()
                    and type(b['common']) is str and Path(b['common']).is_absolute(),'invalid-binding-paths')
        return value

    def save(self):
        self.check();self.data['revision']+=1
        raw=(json.dumps(self.data,sort_keys=True,ensure_ascii=False,separators=(',',':'),allow_nan=False)+'\n').encode()
        require(len(raw)<=256*1024,'state-too-large')
        fd,name=tempfile.mkstemp(prefix='.branch-state-',dir=self.path.parent)
        try:
            with os.fdopen(fd,'wb') as stream:stream.write(raw);stream.flush();os.fsync(stream.fileno())
            self.check();os.replace(name,self.path)
            parent=os.open(self.path.parent,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
            try:os.fsync(parent)
            finally:os.close(parent)
        finally:
            if os.path.exists(name):os.unlink(name)

    def find(self, supplied):
        require(type(supplied) is dict and positive(supplied.get('card')),'missing-binding')
        row=self.data['bindings'].get(str(supplied['card']));require(row is not None,'binding-not-found')
        b=row['binding']
        for key in ('repository','repository_id','card','role','worktree','base','branch','head','target','common'):
            require(type(supplied.get(key)) is type(b[key]) and supplied[key]==b[key],'stale-or-rebound-card-identity')
        return row


def result(action, row, **extra):
    return dict(action=action,binding=copy.deepcopy(row['binding']),**extra)
