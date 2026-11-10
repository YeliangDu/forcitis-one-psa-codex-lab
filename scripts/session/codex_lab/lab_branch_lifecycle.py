"""Durable card bindings and effect fences; no GitHub/Git/session side effects."""
from contextlib import contextmanager
import copy
import fcntl
import json
import importlib
import os
from pathlib import Path
import re
import stat
import sqlite3
import sys
import tempfile
from contextlib import ExitStack

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
    def locked(self, readonly=False):
        self.check()
        path = self.path.with_name(self.path.name + '.lock')
        if readonly:
            regular(self.path.lstat())
        fd = os.open(path, os.O_RDWR | (0 if readonly else os.O_CREAT) | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
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


def canonical_snapshot(common_dir, number):
    """Read existing V2 and FIFO/head under original, existing-only read locks.

    No Queue constructor/method, initialization/migration, permission change,
    reserve, confirm, state save or writer. Shared by CLI and native observation.
    """
    common=Path(common_dir)
    require((number is None or positive(number)) and common.is_absolute() and common.name=='.git'
            and common.parent.name==REPOSITORY.split('/')[1]
            and common.resolve(strict=True)==common and common.is_dir(),'nonphysical-Lab-common')
    directory=common/'merge-queue'
    require(directory.resolve(strict=True)==directory and directory.is_dir()
            and directory.stat().st_uid==os.geteuid() and stat.S_IMODE(directory.stat().st_mode)==0o700,
            'canonical-directory-not-private-V2')
    merge=Path(__file__).resolve().parents[2]/'merge'
    source=merge/'mq_lab_journal_v2.py'
    require(source.resolve(strict=True)==source,'linked-V2-reader-source')
    before=sys.path[:]
    try:
        sys.path.insert(0,str(merge))
        reviewed=importlib.import_module('mq_lab_journal_v2')
    finally:sys.path[:]=before
    require(Path(reviewed.__file__).resolve()==source,'foreign-V2-reader-cache')

    def identity(info):
        return (info.st_dev,info.st_ino,info.st_uid,info.st_mode,info.st_nlink,info.st_size,info.st_mtime_ns,info.st_ctime_ns)

    def read(path,limit=32*1024*1024):
        fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
        with os.fdopen(fd,'rb') as stream:
            start=os.fstat(stream.fileno())
            require(stat.S_ISREG(start.st_mode) and start.st_uid==os.geteuid() and start.st_nlink==1
                    and not start.st_mode&0o022 and start.st_size<=limit,'unsafe-canonical-read')
            raw=stream.read(limit+1)
            require(len(raw)==start.st_size and identity(start)==identity(os.fstat(stream.fileno()))
                    ==identity(path.lstat()),'canonical-read-moved')
            return raw

    with ExitStack() as stack:
        locks=[]
        for name in ('merge.queue.merge-lock','merge.queue.lock','merge.queue.heads-lock'):
            path=directory/name
            fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
            stack.callback(os.close,fd)
            info=os.fstat(fd)
            require(stat.S_ISREG(info.st_mode) and info.st_uid==os.geteuid() and info.st_nlink==1
                    and not info.st_mode&0o022 and identity(info)==identity(path.lstat()),'unsafe-existing-canonical-lock')
            fcntl.flock(fd,fcntl.LOCK_SH|fcntl.LOCK_NB)
            locks.append((path,fd))
        queue_path,heads_path=directory/'merge.queue',directory/'merge.queue.heads'
        q,h=read(queue_path),read(heads_path)
        queued=[]
        for line in q.decode('utf-8').splitlines():
            if not line.strip() or line.lstrip().startswith('#'):continue
            match=re.fullmatch(r'\s*([1-9][0-9]*)(?:\s+after=([1-9][0-9]*(?:,[1-9][0-9]*)*))?\s*',line)
            require(match is not None and int(match[1]) not in queued,'invalid-canonical-FIFO')
            queued.append(int(match[1]))
        heads={}
        for line in h.decode('utf-8').splitlines():
            if not line.strip():continue
            match=re.fullmatch(r'([1-9][0-9]*) ([0-9a-f]{40})',line)
            require(match is not None and int(match[1]) not in heads,'invalid-canonical-head-stamp')
            heads[int(match[1])]=match[2]
        path=directory/'releases.sqlite3'
        storage=reviewed._Storage(path)
        storage.check()
        original=read(path)
        require(len(original)>=100 and original[:16]==b'SQLite format 3\x00'
                and original[18:20]==b'\x01\x01','canonical-storage-not-DELETE-mode')
        db=sqlite3.connect(path.as_uri()+'?mode=ro',uri=True)
        try:
            db.enable_load_extension(False)
            db.execute('PRAGMA trusted_schema=OFF')
            db.execute('PRAGMA query_only=ON')
            db.execute('BEGIN')
            reviewed._schema(db,2)
            row=(db.execute('SELECT '+','.join(reviewed.FIELDS)+',mode FROM releases WHERE number=?',(number,)).fetchone()
                 if number is not None else None)
            record=dict(zip((*reviewed.FIELDS,'mode'),row)) if row else None
            if record and record['mode']=='control-upgrade':
                adoption=db.execute('SELECT phase,adopted_main FROM upgrade_bindings WHERE number=?',(number,)).fetchone()
                require(adoption==('adopted',record['merge_sha']),'protected-upgrade-not-adopted')
            db.rollback()
        finally:db.close()
        storage.check()
        require(read(queue_path)==q and read(heads_path)==h and read(path)==original,'canonical-snapshot-moved')
        for path,fd in locks:
            require(identity(os.fstat(fd))==identity(path.lstat()),'canonical-lock-inode-moved')
        return queued,heads,record
