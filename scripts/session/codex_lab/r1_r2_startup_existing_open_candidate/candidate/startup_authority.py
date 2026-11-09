"""One explicit typed startup, existing-schema-only. No persistent authority grant."""
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import stat
from urllib.parse import quote

from private_resources import load_reference
from portable_config import require, canonical

TABLES = {'installation','owner','tasks','turns','effects','waits','events','inbox',
          'role_registration','native_dispatch','native_tool_operations','native_resume_schema',
          'checkpoint_wait_control','checkpoint_wait_payload','managed_wait_goal','goal_retirement',
          'native_handoff','native_reply_fence','handoff_retirement','orphan_handoff_archive'}
OPERATIONS = dict(storage='open-existing-schema-only', migrations='deny', initialization='deny',
                  socket='exclusive-runtime-socket', startup_events='bounded-existing-constructor-audit')


def identity(path):
    p=Path(path)
    require(p.resolve(strict=True)==p and not p.is_symlink(),'physical-existing-startup-resource-required')
    s=p.lstat()
    return dict(dev=s.st_dev,ino=s.st_ino,uid=s.st_uid,mode=stat.S_IMODE(s.st_mode),
                kind=stat.S_IFMT(s.st_mode),nlink=s.st_nlink,size=s.st_size,mtime_ns=s.st_mtime_ns,ctime_ns=s.st_ctime_ns)


def schema_snapshot(db):
    rows=db.execute("select type,name,tbl_name,sql from sqlite_schema where name!='sqlite_sequence' "
                    'order by type,name,tbl_name').fetchall()
    require({r[1] for r in rows if r[0]=='table'}==TABLES,'complete-known-existing-runtime-schema-required')
    require(all(r[0]=='table' or (r[0]=='index' and r[1].startswith('sqlite_autoindex_') and r[3] is None)
                for r in rows),'unknown-view-trigger-or-index-denied')
    return hashlib.sha256(canonical([list(r) for r in rows]).encode()).hexdigest()


class StartupAuthority:
    def __init__(self,binding,reference):
        self.binding=binding
        self.reference=reference
        self.used=False
        self.policy=self._load()

    def _load(self):
        value=load_reference(self.reference['path'],self.reference['sha256'])
        require(type(value) is dict and set(value)=={'kind','source_closure_sha256','ledger_installation_sha256',
            'registered_session','role','physical_cwd','runtime_directory','runtime_identity','lock_identity',
            'database_identity','schema_sha256','user_version','owner','operations'},'exact-startup-scope-fields-required')
        require(value['kind']=='lab-existing-runtime-startup/1' and value['operations']==OPERATIONS,
                'typed-existing-only-startup-permission-required')
        b=self.binding.view()
        require(value['source_closure_sha256']==self.binding.selected_source_closure_sha256
                and value['ledger_installation_sha256']==b['ledger']['installation_sha256']
                and value['registered_session']==b['native']['expected_registered_session']
                and value['role']==b['role']['name'] and value['physical_cwd']==b['role']['physical_cwd']
                and value['runtime_directory']==b['ledger']['directory'],'exact-code-installation-role-runtime-startup-mismatch')
        require(type(value['owner']) is dict and set(value['owner'])=={'client','generation','paused'}
                and type(value['owner']['client']) is str and bool(value['owner']['client'])
                and type(value['owner']['generation']) is int and value['owner']['generation']>0
                and value['owner']['paused'] is True and type(value['user_version']) is int and value['user_version']>=0,
                'actual-owned-paused-startup-required')
        for name in ('runtime_identity','lock_identity','database_identity'):
            actual=value[name]
            require(type(actual) is dict and set(actual)=={'dev','ino','uid','mode','kind','nlink','size','mtime_ns','ctime_ns'}
                    and all(type(v) is int and v>=0 for v in actual.values()),'exact-resource-stat-pin-required')
        require(type(value['schema_sha256']) is str and len(value['schema_sha256'])==64
                and all(c in '0123456789abcdef' for c in value['schema_sha256']),'independent-existing-schema-pin-required')
        return value

    @contextmanager
    def acquire(self,cfg):
        require(not self.used and cfg.get('binding') is self.binding
                and cfg.get('runtime_directory') == self.policy['runtime_directory']
                and cfg.get('role') == self.policy['role'] and cfg.get('physical_cwd') == self.policy['physical_cwd'],
                'exact-serve-resource-and-one-startup-window-required')
        require(self._load()==self.policy and cfg['pin']==self.policy['ledger_installation_sha256'],
                'startup-authorization-moved')
        p=self.policy;directory=Path(p['runtime_directory']);lock=directory/'broker.lock';database=directory/'runtime.sqlite3'
        for path,field,kind in ((directory,'runtime_identity',stat.S_IFDIR),(lock,'lock_identity',stat.S_IFREG),
                                (database,'database_identity',stat.S_IFREG)):
            info=identity(path)
            require(info==p[field] and info['uid']==os.geteuid() and info['kind']==kind
                    and info['mode']==(0o700 if kind==stat.S_IFDIR else 0o600),'startup-resource-identity-or-owner-mismatch')
        require(p['lock_identity']['nlink']==p['database_identity']['nlink']==1,'single-link-startup-files-required')
        fd=os.open(str(lock),os.O_RDONLY|os.O_NOFOLLOW)
        try:
            fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
            st=os.fstat(fd)
            require((st.st_dev,st.st_ino)==(p['lock_identity']['dev'],p['lock_identity']['ino']),
                    'opened-original-lock-identity-mismatch')
            def stable():
                require(identity(directory)==p['runtime_identity'] and identity(lock)==p['lock_identity']
                        and identity(database)==p['database_identity'],'startup-resources-moved-before-open')
                for suffix in ('-wal','-shm','-journal'):
                    side=database.with_name(database.name+suffix)
                    require(not side.exists() and not side.is_symlink(),'startup-sidecar-or-hot-journal-hold')
                sock=directory/'broker.sock'
                require(not sock.exists() and not sock.is_symlink(),'existing-socket-needs-readonly-reconciliation')
            stable()
            header_fd=os.open(str(database),os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
            try:
                header=os.read(header_fd,100)
                require(header[:16]==b'SQLite format 3\x00' and header[18:20]==b'\x01\x01',
                        'rollback-journal-format-required-no-wal-conversion')
            finally:os.close(header_fd)
            uri='file:'+quote(str(database),safe='/')+'?mode=ro&immutable=1'
            db=sqlite3.connect(uri,uri=True)
            try:
                db.execute('pragma query_only=on')
                require(db.execute('pragma user_version').fetchone()==(p['user_version'],)
                        and db.execute('pragma journal_mode').fetchone()==('delete',)
                        and schema_snapshot(db)==p['schema_sha256'],'existing-schema-version-or-journal-mismatch')
                require(db.execute('select pin from installation where id=1').fetchone()==(p['ledger_installation_sha256'],)
                        and db.execute('select session_id,phase from role_registration where id=1').fetchone()==(p['registered_session'],'stable')
                        and db.execute('select id,version from native_resume_schema').fetchall()==[(1,1)],
                        'existing-installation-registry-or-migration-marker-mismatch')
                row=db.execute('select client,generation,paused from owner where id=1').fetchone()
                require(row==(p['owner']['client'],p['owner']['generation'],1),'actual-owner-generation-not-quiescent-paused')
            finally:db.close()
            stable()
            require(self._load()==p,'startup-policy-changed-under-original-lock')
            self.used=True  # Ephemeral one-window permit; never stored in ledger.
            resource = GuardedRuntime(directory, database, fd, p)
            try:
                yield resource
            finally:
                resource.close()
        finally:
            os.close(fd)  # Original lock held through Store/socket/runtime lifetime.


def select_startup_authority(binding):
    reference=binding.reference('runtime-startup')
    require(reference['kind']=='authorization','independent-startup-authorization-reference-required')
    return StartupAuthority(binding,reference)


class GuardedRuntime:
    """Ephemeral original-resource lease; no caller-selected database/open mode."""
    def __init__(self,directory,database,lock_fd,policy):
        self._directory=Path(directory)
        self._database=Path(database)
        self._lock_fd=lock_fd
        self._policy=json.loads(json.dumps(policy))
        self._pid=os.getpid()
        self._active=True
        self._opened=False
        self._connection=None

    @property
    def directory(self):
        return self._directory

    def _still_original(self):
        p=self._policy
        require(self._active and self._pid==os.getpid(),'startup-resource-lease-no-longer-active')
        st=os.fstat(self._lock_fd)
        require((st.st_dev,st.st_ino)==(p['lock_identity']['dev'],p['lock_identity']['ino'])
                and identity(self.directory)==p['runtime_identity']
                and identity(self.directory/'broker.lock')==p['lock_identity']
                and identity(self._database)==p['database_identity'],'guarded-resource-drift-before-existing-open')
        for suffix in ('-wal','-shm','-journal'):
            value=self._database.with_name(self._database.name+suffix)
            require(not value.exists() and not value.is_symlink(),'guarded-database-sidecar-hold')

    def open_existing(self):
        require(not self._opened,'one-existing-database-open-per-guard')
        self._still_original()
        self._opened=True  # No retry/fallback even if the first open fails.
        uri='file:'+quote(str(self._database),safe='/')+'?mode=rw'
        db=sqlite3.connect(uri,uri=True,isolation_level=None,check_same_thread=False)
        try:
            self._still_original()
            p=self._policy
            require(schema_snapshot(db)==p['schema_sha256']
                    and db.execute('pragma user_version').fetchone()==(p['user_version'],)
                    and db.execute('pragma journal_mode').fetchone()==('delete',),'rw-existing-schema-or-format-mismatch')
            require(db.execute('select pin from installation where id=1').fetchone()==(p['ledger_installation_sha256'],)
                    and db.execute('select session_id,phase from role_registration where id=1').fetchone()==(p['registered_session'],'stable')
                    and db.execute('select id,version from native_resume_schema').fetchall()==[(1,1)]
                    and db.execute('select client,generation,paused from owner where id=1').fetchone()==
                    (p['owner']['client'],p['owner']['generation'],1),'rw-existing-marker-or-owner-mismatch')
            self._still_original()
            denied={sqlite3.SQLITE_CREATE_INDEX,sqlite3.SQLITE_CREATE_TABLE,sqlite3.SQLITE_CREATE_TEMP_INDEX,
                    sqlite3.SQLITE_CREATE_TEMP_TABLE,sqlite3.SQLITE_CREATE_TEMP_TRIGGER,sqlite3.SQLITE_CREATE_TEMP_VIEW,
                    sqlite3.SQLITE_CREATE_TRIGGER,sqlite3.SQLITE_CREATE_VIEW,sqlite3.SQLITE_DROP_INDEX,
                    sqlite3.SQLITE_DROP_TABLE,sqlite3.SQLITE_DROP_TEMP_INDEX,sqlite3.SQLITE_DROP_TEMP_TABLE,
                    sqlite3.SQLITE_DROP_TEMP_TRIGGER,sqlite3.SQLITE_DROP_TEMP_VIEW,sqlite3.SQLITE_DROP_TRIGGER,
                    sqlite3.SQLITE_DROP_VIEW,sqlite3.SQLITE_ALTER_TABLE,sqlite3.SQLITE_ATTACH,sqlite3.SQLITE_DETACH}
            def authorize(action,first,second,database,source):
                if action in denied:
                    return sqlite3.SQLITE_DENY
                if action==sqlite3.SQLITE_PRAGMA and second is not None:
                    if (str(first).lower(),str(second).lower()) not in {('synchronous','full'),('foreign_keys','on')}:
                        return sqlite3.SQLITE_DENY
                return sqlite3.SQLITE_OK
            db.set_authorizer(authorize)
            self._connection=db
            return db
        except BaseException:
            db.close()
            raise

    def close(self):
        self._active=False
        if self._connection is not None:
            try:self._connection.close()
            finally:self._connection=None
