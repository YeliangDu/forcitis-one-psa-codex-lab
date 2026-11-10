"""Persistent native-role storage and credential-free consistent snapshots."""
from contextlib import contextmanager, closing
import ctypes
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import sqlite3
import stat
import sys
import tempfile
from uuid import UUID

PREFIX=(".codex-lab","role-runtime","slots")
LOCK=".exclusive.lock"
MANIFEST="backup-manifest.json"
DATABASE="owner/runtime.sqlite3"
NATIVE_DATABASES={"home/.codex/state_5.sqlite","home/.codex/thread_history_1.sqlite"}
NATIVE_FILES=NATIVE_DATABASES|{"runtime.sb","model-catalog.json","owned-installation.json",
              "single-role-installation.json","home/.codex/config.toml",
              "home/.codex/session_index.jsonl",
              "work/assignment.json"}
NATIVE_PREFIXES=("home/.codex/sessions/","home/.codex/archived_sessions/")
SECRETS={"auth.json","auth.json.tmp","authorization.json","credentials","secrets"}
LIMIT=128*1024*1024


def require(ok, reason):
    if not ok: raise ValueError(reason)


def uid(value):
    try:return type(value) is str and str(UUID(value))==value
    except (ValueError,TypeError):return False


def directory(path):
    try:
        row=path.lstat()
        require(path.is_absolute() and path.resolve(strict=True)==path and stat.S_ISDIR(row.st_mode)
                and row.st_uid==os.geteuid() and not row.st_mode&0o022,"unsafe-physical-role-directory")
        return row
    except OSError as error:
        raise ValueError("unavailable-physical-role-directory") from error


def validate_root(lab,slot):
    lab,slot=Path(lab),Path(slot)
    directory(lab)
    require(slot.parent==lab.joinpath(*PREFIX) and re.fullmatch(r"one-[a-z0-9-]{1,55}",slot.name),
            "noncanonical-durable-role-slot")
    for i in range(1,len(PREFIX)+1):directory(lab.joinpath(*PREFIX[:i]))
    directory(slot)
    return slot


def canonical(slot):
    slot=Path(slot)
    require(len(slot.parents)>=4,"missing-durable-role-root")
    return validate_root(slot.parents[3],slot)


def signature(st):
    return tuple(getattr(st,k) for k in ("st_dev","st_ino","st_mode","st_uid","st_nlink","st_size","st_mtime_ns","st_ctime_ns"))


@contextmanager
def exclusive(slot):
    slot=canonical(slot)
    before=directory(slot)
    lock=slot/LOCK
    try:
        fd=os.open(lock,os.O_RDWR|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CREAT,0o600)
    except OSError as error:
        raise ValueError("durable-lock-open-refused") from error
    try:
        row=os.fstat(fd)
        require(stat.S_ISREG(row.st_mode) and row.st_uid==os.geteuid() and row.st_nlink==1
                and not row.st_mode&0o077 and signature(row)==signature(lock.lstat()),"unsafe-durable-role-lock")
        try:fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError as error:raise ValueError("durable-role-slot-already-locked") from error
        require(signature(row)==signature(lock.lstat()) and
                (before.st_dev,before.st_ino)==(slot.stat().st_dev,slot.stat().st_ino),"durable-role-lock-moved")
        yield fd
    finally:
        os.close(fd)


def _allowed(name):
    p=PurePosixPath(name)
    return (not p.is_absolute() and p.as_posix()==name and not set(p.parts)&(SECRETS|{"..","."})
            and (name==DATABASE or name in NATIVE_FILES
                 or name.startswith(("history/","install/",*NATIVE_PREFIXES)))
            and not name.endswith(("-wal","-shm","-journal")))


def _read(path):
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    with os.fdopen(fd,"rb") as stream:
        row=os.fstat(stream.fileno())
        require(path.resolve(strict=True)==path and stat.S_ISREG(row.st_mode) and row.st_uid==os.geteuid()
                and row.st_nlink==1 and not row.st_mode&0o022 and row.st_size<=LIMIT,"unsafe-backup-member")
        raw=stream.read(LIMIT+1)
        require(len(raw)==row.st_size and signature(row)==signature(os.fstat(stream.fileno()))
                ==signature(path.lstat()),"backup-member-moved")
        return raw


def _snapshot_native(path, scratch):
    """Checkpoint a disposable byte copy, never open the source via SQLite."""
    wal=path.with_name(path.name+"-wal")
    shm=path.with_name(path.name+"-shm")
    journal=path.with_name(path.name+"-journal")
    require(not os.path.lexists(journal),"native-rollback-journal-present")
    raw=_read(path)
    wal_raw=_read(wal) if os.path.lexists(wal) else None
    if os.path.lexists(shm):
        row=shm.lstat()
        require(stat.S_ISREG(row.st_mode) and row.st_uid==os.geteuid() and row.st_nlink==1
                and wal_raw is not None,"invalid-native-wal-sidecar")
    with tempfile.TemporaryDirectory(prefix=".native-snapshot-",dir=scratch) as name:
        root=Path(name)
        inputs={"source.sqlite":raw}
        if wal_raw is not None:inputs["source.sqlite-wal"]=wal_raw
        _write_tree(root,inputs)
        clone=root/"source.sqlite"
        result=root/"snapshot.sqlite"
        with closing(sqlite3.connect(clone.as_uri()+"?mode=rw",uri=True)) as source, \
                closing(sqlite3.connect(result)) as target:
            require(source.execute("pragma integrity_check").fetchall()==[("ok",)],"inconsistent-native-source")
            source.backup(target)
            require(target.execute("pragma journal_mode=delete").fetchone()==("delete",)
                    and target.execute("pragma integrity_check").fetchall()==[("ok",)],"inconsistent-native-snapshot")
        require(_read(path)==raw and
                ((_read(wal)==wal_raw) if wal_raw is not None else not os.path.lexists(wal)),
                "native-source-drift-during-snapshot")
        return _read(result)


def _files(slot,scratch):
    files={}
    roots=("owner","history","install","home/.codex/sessions","home/.codex/archived_sessions")
    for name in (*NATIVE_FILES,DATABASE):
        p=slot/name
        if p.exists() or p.is_symlink():
            files[name]=_snapshot_native(p,scratch) if name in NATIVE_DATABASES else _read(p)
        if name==DATABASE:
            require(all(not os.path.lexists(str(p)+s) for s in ("-wal","-shm","-journal")),
                    "sqlite-sidecar-present-stop-and-checkpoint-first")
    for rel in roots[1:]:
        base=slot/rel
        if not base.exists() and not base.is_symlink():continue
        directory(base)
        for parent,dirs,names in os.walk(base,followlinks=False):
            parent=Path(parent)
            dirs[:]=sorted(x for x in dirs if x not in SECRETS)
            for n in dirs:directory(parent/n)
            for n in sorted(names):
                name=(parent/n).relative_to(slot).as_posix()
                if _allowed(name):files[name]=_read(parent/n)
                elif n not in SECRETS:raise ValueError("unrecognized-durable-history-member")
    require(DATABASE in files,"missing-role-ledger")
    return files


def _state(root,native_id,files):
    require(uid(native_id),"invalid-role-native-id")
    db=root/DATABASE
    with closing(sqlite3.connect(db.as_uri()+"?mode=ro&immutable=1",uri=True)) as conn:
        require(conn.execute("pragma integrity_check").fetchall()==[("ok",)],"inconsistent-role-ledger")
        require(conn.execute("select session_id,phase from role_registration").fetchall()==[(native_id,"stable")],
                "stable-role-id-mismatch")
        require(conn.execute("select paused from owner").fetchall()==[(1,)],"snapshot-requires-durable-pause")
        effects=conn.execute("select effect_id,issue_count,phase from effects order by effect_id").fetchall()
        require(all(type(x[0]) is str and type(x[1]) is int and x[1]>=0 and type(x[2]) is str for x in effects),
                "malformed-role-effect-state")
    found=False
    for n,raw in files.items():
        if n.startswith(("history/",*NATIVE_PREFIXES)) and n.endswith(".jsonl") and raw:
            try: first=json.loads(raw.split(b"\n",1)[0])
            except (UnicodeError,ValueError):continue
            if type(first) is dict and first.get("type")=="session_meta" and first.get("payload",{}).get("id")==native_id:
                found=True
    require(found,"same-native-history-missing")
    for n in ("home/.codex/state_5.sqlite",):
        if n in files:
            with closing(sqlite3.connect((root/n).as_uri()+"?mode=ro&immutable=1",uri=True)) as db:
                require(db.execute("pragma integrity_check").fetchall()==[("ok",)],"inconsistent-native-ledger")
                require(db.execute("select count(*) from threads where id=?",(native_id,)).fetchone()==(1,),
                        "same-native-state-missing")
    return {"native_id":native_id,"paused":True,"effects":[list(x) for x in effects]}


def _pins(files):
    return {n:hashlib.sha256(b).hexdigest() for n,b in sorted(files.items())}


def _publish(staging,target):
    # Exclusive atomic rename on the two supported hosts: an existing target
    # is never replaced, even if it appears after the initial check.
    lib=ctypes.CDLL(None,use_errno=True)
    if sys.platform=="darwin":
        code=lib.renameatx_np(-2,os.fsencode(staging),-2,os.fsencode(target),4)
    elif sys.platform=="linux":
        code=lib.renameat2(-100,os.fsencode(staging),-100,os.fsencode(target),1)
    else:raise ValueError("exclusive-rename-platform-unavailable")
    require(code==0,"exclusive-snapshot-publication-refused")
    _syncdir(target.parent)


def _syncdir(path):
    fd=os.open(path,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    try:os.fsync(fd)
    finally:os.close(fd)


def _target(target):
    target=Path(target)
    directory(target.parent)
    require(not os.path.lexists(target),"snapshot-target-exists")
    return target


def _write_tree(stage,files):
    for name,raw in files.items():
        p=stage/name
        p.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,"wb") as f:
            f.write(raw);f.flush();os.fsync(f.fileno())
    for path in sorted((p for p in stage.rglob("*") if p.is_dir()),key=lambda p:len(p.parts),reverse=True):
        _syncdir(path)
    _syncdir(stage)


def backup(slot,target,native_id):
    target=_target(target)
    slot=canonical(slot)
    require(not target.is_relative_to(slot),"backup-target-inside-live-role")
    with exclusive(slot):
        stage=Path(tempfile.mkdtemp(prefix=".backup-stage-",dir=target.parent))
        try:
            files=_files(slot,target.parent)
            _write_tree(stage,files)
            state=_state(stage,native_id,files)
            require(_pins(files)==_pins(_files(slot,target.parent)),"live-role-drift-during-snapshot")
            manifest=dict(schema=1,role=slot.name,source_slot=str(slot),state=state,sha256=_pins(files))
            _write_tree(stage,{MANIFEST:(json.dumps(manifest,sort_keys=True)+"\n").encode()})
            _publish(stage,target)
        except (OSError,sqlite3.Error,KeyError,TypeError) as error:
            raise ValueError("durable-role-backup-refused") from error
        finally:
            if stage.exists():shutil.rmtree(stage)
    return target


def restore(backup,target,native_id):
    root=Path(backup);directory(root)
    target=_target(target)
    require(len(target.parents)>=4 and tuple(target.parent.parts[-3:])==PREFIX
            and re.fullmatch(r"one-[a-z0-9-]{1,55}",target.name),"noncanonical-restore-target")
    stage=Path(tempfile.mkdtemp(prefix=".restore-stage-",dir=target.parent))
    try:
        m=json.loads(_read(root/MANIFEST))
        require(m["schema"]==1 and m["role"]==target.name and m["state"]["native_id"]==native_id
                and type(m["sha256"]) is dict and all(_allowed(n) for n in m["sha256"]),"restore-manifest-mismatch")
        files={n:_read(root/n) for n in m["sha256"]}
        require(set(files)|{MANIFEST}=={p.relative_to(root).as_posix() for p in root.rglob("*")
                if not p.is_dir() or p.is_symlink()} and _pins(files)==m["sha256"],"restore-digest-or-inventory-mismatch")
        if "owned-installation.json" in files:
            require(str(target)==m["source_slot"],"native-installation-bound-to-original-physical-slot")
            for n in ("home","home/.codex","owner","runtime","trace","tmp","work"):
                (stage/n).mkdir(parents=True,exist_ok=True,mode=0o700)
        _write_tree(stage,files)
        require(_state(stage,native_id,files)==m["state"],"restored-role-state-mismatch")
        _publish(stage,target)
    except (OSError,sqlite3.Error,KeyError,TypeError) as error:
        raise ValueError("durable-role-restore-refused") from error
    finally:
        if stage.exists():shutil.rmtree(stage)
    return target
