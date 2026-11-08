"""Durable release records in a caller-owned, independently verified directory.

The caller holds Queue.merge_lock across the whole release sequence. This
adapter has no GitHub or queue operations and never creates parent directories.
It opens fresh SQLite connections, so recovery reads committed on-disk state.
"""
from contextlib import closing, contextmanager
import os
from pathlib import Path
import re
import sqlite3
import stat


FIELDS = ("number", "status", "head", "base", "target", "release_key", "merge_sha")
SCHEMA = (("number", "INTEGER"), ("status", "TEXT"), ("head", "TEXT"),
          ("base", "TEXT"), ("target", "TEXT"), ("release_key", "TEXT"), ("merge_sha", "TEXT"))


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _number(value):
    _require(type(value) is int and 0 < value <= 9223372036854775807, "Invalid PR number")


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _validate(record, status):
    expected = set(FIELDS) if status == "confirmed" else set(FIELDS) - {"merge_sha"}
    _require(isinstance(record, dict) and set(record) == expected, "Invalid release record shape")
    _number(record["number"])
    _require(record["status"] == status and status in ("reserved", "confirmed")
             and _sha(record["head"]) and _sha(record["base"]) and record["target"] == "main"
             and isinstance(record["release_key"], str) and bool(record["release_key"].strip())
             and not any(ord(c) < 32 or ord(c) == 127 for c in record["release_key"]), "Invalid release identity")
    if status == "confirmed":
        _require(_sha(record["merge_sha"]), "Invalid confirmed merge SHA")
    return dict(record)


def _regular(path, missing=False):
    try:
        info = path.lstat()
    except FileNotFoundError:
        if missing:
            return None
        raise
    _require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, "Unsafe journal file or sidecar")
    return info


class ReleaseJournal:
    def __init__(self, path, *, create=True):
        _require(type(create) is bool, "Invalid journal creation mode")
        self.create = create
        try:
            raw = os.fspath(path)
        except TypeError as error:
            raise ValueError("Invalid journal path type") from error
        _require(isinstance(raw, str) and bool(raw) and ".." not in raw.split("/"), "Invalid journal path")
        self.path = Path(raw)
        _require(self.path.is_absolute(), "Journal requires an absolute physical path")
        self._check_path(missing=create)
        if not create:
            # Recovery must not initialize an absent database, add a table or
            # repair an unknown schema. mode=ro also covers disappearance races.
            with self._connection(readonly=True) as db:
                tables = db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
                _require(tables == [("releases",)], "Unrecognized existing journal database")
                self._schema(db)
            return
        try:
            fd = os.open(self.path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        except FileExistsError:
            _regular(self.path)
        else:
            try:
                os.fsync(fd)
            finally:
                os.close(fd)
            parent = os.open(self.path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(parent)
            finally:
                os.close(parent)
        with self._connection() as db, db:
            tables = db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
            _require(all(row[0] == "releases" for row in tables), "Unrecognized journal database")
            db.execute("""CREATE TABLE IF NOT EXISTS releases (
                number INTEGER PRIMARY KEY CHECK(number > 0),
                status TEXT NOT NULL CHECK(status IN ('reserved', 'confirmed')),
                head TEXT NOT NULL, base TEXT NOT NULL, target TEXT NOT NULL CHECK(target = 'main'),
                release_key TEXT NOT NULL, merge_sha TEXT,
                CHECK((status = 'reserved' AND merge_sha IS NULL) OR
                      (status = 'confirmed' AND merge_sha IS NOT NULL)))""")
            self._schema(db)

    def _check_path(self, missing=False):
        # Caller ownership is the race boundary: no untrusted process may swap
        # directories while SQLite opens its database and rollback sidecars.
        parent = self.path.parent
        _require(parent.resolve(strict=True) == parent and parent.is_dir(), "Nonphysical or missing journal parent")
        info = _regular(self.path, missing=missing)
        if info is not None:
            _require(stat.S_IMODE(info.st_mode) == 0o600, "Journal must be private (0600)")
        for suffix in ("-journal", "-wal", "-shm"):
            _regular(Path(str(self.path) + suffix), missing=True)
        return info

    @contextmanager
    def _connection(self, *, readonly=False):
        before = self._check_path()
        # mode=rw prohibits SQLite from silently recreating a disappeared file.
        mode = "ro" if readonly else "rw"
        with closing(sqlite3.connect(self.path.as_uri() + "?mode=" + mode, uri=True)) as db:
            after = self._check_path()
            _require((before.st_dev, before.st_ino) == (after.st_dev, after.st_ino), "Journal identity changed")
            journal_mode = "PRAGMA journal_mode=DELETE" if self.create and not readonly else "PRAGMA journal_mode"
            _require(db.execute(journal_mode).fetchone()[0].lower() == "delete", "Unexpected journal mode")
            db.execute("PRAGMA synchronous=FULL")
            _require(db.execute("PRAGMA synchronous").fetchone()[0] == 2, "Durability unavailable")
            yield db

    @staticmethod
    def _schema(db):
        columns = db.execute("PRAGMA table_info(releases)").fetchall()
        _require(tuple((row[1], row[2]) for row in columns) == SCHEMA
                 and columns[0][5] == 1 and all(row[5] == 0 for row in columns[1:]), "Invalid journal schema")

    @staticmethod
    def _read(db, number):
        row = db.execute("SELECT number,status,head,base,target,release_key,merge_sha FROM releases WHERE number=?", (number,)).fetchone()
        if row is None:
            return None
        record = dict(zip(FIELDS, row))
        if record["status"] == "reserved":
            _require(record.pop("merge_sha") is None, "Corrupt reservation")
        return _validate(record, record["status"])

    def load(self, number):
        _number(number)
        with self._connection(readonly=not self.create) as db:
            self._schema(db)
            return self._read(db, number)

    def reserve(self, record):
        record = _validate(record, "reserved")
        if not self.create:
            return False
        with self._connection() as db, db:
            self._schema(db)
            db.execute("BEGIN IMMEDIATE")
            if self._read(db, record["number"]) is not None:
                return False
            db.execute("INSERT INTO releases (number,status,head,base,target,release_key) VALUES (?,?,?,?,?,?)",
                       tuple(record[field] for field in FIELDS[:-1]))
        return True

    def confirm(self, record):
        record = _validate(record, "confirmed")
        expected = {k: v for k, v in record.items() if k != "merge_sha"}
        expected["status"] = "reserved"
        with self._connection() as db, db:
            self._schema(db)
            db.execute("BEGIN IMMEDIATE")
            if self._read(db, record["number"]) != expected:
                return False
            changed = db.execute("""UPDATE releases SET status='confirmed', merge_sha=?
                WHERE number=? AND status='reserved' AND head=? AND base=? AND target=?
                AND release_key=? AND merge_sha IS NULL""",
                (record["merge_sha"], record["number"], record["head"], record["base"], record["target"], record["release_key"]))
            _require(changed.rowcount == 1, "Reservation changed during confirmation")
        return True
