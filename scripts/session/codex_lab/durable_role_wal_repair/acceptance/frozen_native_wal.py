"""Independent native SQLite WAL backup supplement.

Dependencies:
  frozen_durable_role.py: unchanged prior synthetic fixture
  durable_role.py: candidate implementation

Native snapshot paths:
  slot/home/.codex/state_5.sqlite
  slot/home/.codex/thread_history_1.sqlite

Only these native SQLite databases gain consistent-snapshot handling.
The owner control ledger retains its existing sidecar rejection boundary.

No现场 data or native process is accessed. Author has not run this suite.
Two test methods. An old backup rejection of committed native WAL must be
an assertion failure marked NATIVE_WAL_SNAPSHOT_REQUIRED, not a setup error.
"""
import hashlib
import importlib
import importlib.util
from pathlib import Path
import shutil
import sqlite3
import unittest

from frozen_durable_role import DurableRoleAcceptance, NATIVE


class NativeWALBackupAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("durable_role") is not None:
            module = importlib.import_module("durable_role")
        function = getattr(module, "backup", None)
        self.assertTrue(
            callable(function), "NATIVE_WAL_BACKUP_CAPABILITY_MISSING"
        )
        return function

    def fingerprint(self, path):
        info = path.stat()
        return (
            info.st_dev,
            info.st_ino,
            path.stat().st_size,
            hashlib.sha256(path.read_bytes()).hexdigest(),
        )

    def native_database(self, path, table):
        path.parent.mkdir(parents=True, exist_ok=True)
        connection = sqlite3.connect(path)
        self.addCleanup(connection.close)
        self.assertEqual(
            connection.execute("PRAGMA journal_mode=WAL").fetchone()[0],
            "wal",
        )
        connection.execute("PRAGMA wal_autocheckpoint=0")
        connection.execute(
            f"CREATE TABLE {table}(id TEXT PRIMARY KEY,history TEXT)"
        )
        connection.commit()
        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchall()

        # The row below is committed to WAL after the main DB was checkpointed.
        main_before = path.read_bytes()
        connection.execute(
            f"INSERT INTO {table} VALUES(?,?)",
            (NATIVE, "synthetic committed native history"),
        )
        connection.commit()
        self.assertEqual(path.read_bytes(), main_before)
        self.assertGreater(Path(str(path) + "-wal").stat().st_size, 0)
        self.assertTrue(Path(str(path) + "-shm").is_file())
        return connection

    def test_01_native_state_and_thread_history_include_committed_wal(self):
        backup = self.seam()
        root, _, slot = DurableRoleAcceptance.fixture(self)
        native = slot / "home/.codex"
        databases = (
            (native / "state_5.sqlite", "threads"),
            (native / "thread_history_1.sqlite", "thread_history"),
        )
        for path, table in databases:
            self.native_database(path, table)

        auth = native / "auth.json"
        auth.write_bytes(b'{"synthetic":"must-not-be-backed-up"}')

        source_bytes = {}
        shm_identity = {}
        for path, _ in databases:
            for source in (path, Path(str(path) + "-wal")):
                source_bytes[source] = self.fingerprint(source)
            shm = Path(str(path) + "-shm")
            info = shm.stat()
            shm_identity[shm] = (info.st_dev, info.st_ino)

        target = root / "native-wal-backup"
        try:
            returned = backup(slot, target, NATIVE)
        except Exception as error:
            self.fail(
                "NATIVE_WAL_SNAPSHOT_REQUIRED:"
                + type(error).__name__ + ":" + str(error)
            )
        self.assertEqual(returned, target)

        # No deletion/replacement/checkpoint or DB/WAL modification at source.
        for path, fingerprint in source_bytes.items():
            self.assertEqual(
                self.fingerprint(path), fingerprint,
                "SOURCE_DB_OR_WAL_CHANGED:" + str(path),
            )
        for shm, identity in shm_identity.items():
            self.assertTrue(shm.is_file(), "SOURCE_SHM_REMOVED")
            info = shm.stat()
            self.assertEqual((info.st_dev, info.st_ino), identity)

        # Copy only each snapshot DB to another directory. Its committed data
        # must be readable without source WAL/SHM or backup-side sidecars.
        independent = root / "independent-readback"
        independent.mkdir()
        for source, table in databases:
            snapshot = target / source.relative_to(slot)
            self.assertTrue(snapshot.is_file())
            isolated = independent / source.name
            shutil.copyfile(snapshot, isolated)
            uri = isolated.as_uri() + "?mode=ro&immutable=1"
            with sqlite3.connect(uri, uri=True) as connection:
                self.assertEqual(
                    connection.execute("PRAGMA integrity_check").fetchone()[0],
                    "ok",
                )
                self.assertEqual(
                    connection.execute(
                        f"SELECT id,history FROM {table}"
                    ).fetchall(),
                    [(NATIVE, "synthetic committed native history")],
                )

        self.assertFalse(any(
            path.name in ("auth.json", "credentials")
            for path in target.rglob("*")
        ))
        self.assertEqual(
            auth.read_bytes(), b'{"synthetic":"must-not-be-backed-up"}'
        )

    def test_02_control_ledger_does_not_inherit_native_wal_exception(self):
        backup = self.seam()
        root, _, slot = DurableRoleAcceptance.fixture(self)
        ledger = slot / "owner/runtime.sqlite3"
        connection = sqlite3.connect(ledger)
        self.addCleanup(connection.close)
        self.assertEqual(
            connection.execute("PRAGMA journal_mode=WAL").fetchone()[0],
            "wal",
        )
        connection.execute("PRAGMA wal_autocheckpoint=0")
        connection.execute(
            "INSERT INTO effects VALUES('synthetic-control-wal',1,'unknown')"
        )
        connection.commit()

        protected = [
            ledger, Path(str(ledger) + "-wal"), Path(str(ledger) + "-shm")
        ]
        before = {path: self.fingerprint(path) for path in protected}
        target = root / "control-ledger-backup"
        with self.assertRaises(ValueError):
            backup(slot, target, NATIVE)
        for path, fingerprint in before.items():
            self.assertEqual(self.fingerprint(path), fingerprint)
        self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
