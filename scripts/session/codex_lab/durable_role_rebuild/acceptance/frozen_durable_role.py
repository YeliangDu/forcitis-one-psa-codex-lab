"""Independent durable-role acceptance; author has not executed this suite.

Exact seams:
  validate_root(lab: Path, slot: Path) -> Path
  exclusive(slot: Path) -> context manager
  backup(slot: Path, target: Path, native_id: str) -> Path
  restore(backup: Path, target: Path, native_id: str) -> Path

All rejection paths raise ValueError.
Canonical lock: slot/.exclusive.lock; never unlink or replace this lock.
Backup preserves relative data paths and includes a verified integrity manifest.
Credentials directories and every file named auth.json are excluded.

Synthetic Labs are created only in test-owned temporary directories.
"Temporary path" rejection means a noncanonical slot such as lab/tmp/role,
not rejection of the test-owned temporary Lab itself.

Missing-module baseline:
  4 tests, 4 assertion failures, 0 errors, exit 1.
No actual resident directory, credentials, or native service is accessed.
"""
import importlib
import importlib.util
import json
import multiprocessing
from pathlib import Path
import sqlite3
import tempfile
import unittest


ROLE = "one-readonly-lab"
NATIVE = "11111111-1111-4111-8111-111111111111"
OTHER = "22222222-2222-4222-8222-222222222222"


def competing_process(slot, result):
    """Opens its own lock FD; never removes the canonical lock."""
    try:
        module = importlib.import_module("durable_role")
        with module.exclusive(Path(slot)):
            result.send("acquired")
    except ValueError:
        result.send("rejected")
    except Exception as error:
        result.send("error:" + type(error).__name__)
    finally:
        result.close()


class DurableRoleAcceptance(unittest.TestCase):
    def seam(self):
        module = None
        if importlib.util.find_spec("durable_role") is not None:
            module = importlib.import_module("durable_role")
        for name in ("validate_root", "exclusive", "backup", "restore"):
            self.assertTrue(
                callable(getattr(module, name, None)),
                "DURABLE_ROLE_CAPABILITY_MISSING:" + name,
            )
        return module

    def fixture(self):
        temporary = tempfile.TemporaryDirectory(prefix="durable-role-test-")
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name).resolve()
        lab = root / "synthetic-lab"
        slot = lab / ".codex-lab/role-runtime/slots" / ROLE
        for directory in (
            slot / "owner", slot / "history",
            slot / "install", slot / "credentials",
        ):
            directory.mkdir(parents=True, exist_ok=True)

        database = slot / "owner/runtime.sqlite3"
        with sqlite3.connect(database) as db:
            db.executescript("""
            CREATE TABLE role_registration(session_id TEXT,phase TEXT);
            CREATE TABLE owner(paused INTEGER);
            CREATE TABLE effects(
              effect_id TEXT,issue_count INTEGER,phase TEXT);
            """)
            db.execute(
                "INSERT INTO role_registration VALUES(?,'stable')", (NATIVE,)
            )
            db.execute("INSERT INTO owner VALUES(1)")
            db.execute(
                "INSERT INTO effects VALUES('synthetic-unknown',1,'unknown')"
            )

        history = (
            json.dumps(dict(type="session_meta", payload=dict(id=NATIVE)))
            + "\n"
            + json.dumps(dict(
                type="event_msg",
                payload=dict(type="task_complete", last_agent_message="done"),
            ))
            + "\n"
        ).encode()
        (slot / "history/rollout.jsonl").write_bytes(history)
        (slot / "history/older").mkdir()
        (slot / "history/older/segment.jsonl").write_bytes(b"older-history\n")
        (slot / "install/config.toml").write_bytes(
            b'model = "synthetic-model"\n'
        )
        # Entirely synthetic exclusion markers, never actual credentials.
        (slot / "credentials/marker.txt").write_bytes(b"exclude-credentials")
        (slot / "install/auth.json").write_bytes(b'{"synthetic":"exclude"}')
        (slot / "history/auth.json").write_bytes(b'{"synthetic":"exclude"}')
        return root, lab, slot

    def public_files(self, root):
        return {
            str(path.relative_to(root)): path.read_bytes()
            for path in root.rglob("*")
            if path.is_file()
            and "credentials" not in path.relative_to(root).parts
            and path.name not in ("auth.json", ".exclusive.lock")
        }

    def assert_state(self, slot):
        database = slot / "owner/runtime.sqlite3"
        with sqlite3.connect(database) as db:
            self.assertEqual(db.execute(
                "PRAGMA integrity_check"
            ).fetchone()[0], "ok")
            self.assertEqual(db.execute(
                "SELECT session_id,phase FROM role_registration"
            ).fetchall(), [(NATIVE, "stable")])
            self.assertEqual(db.execute(
                "SELECT paused FROM owner"
            ).fetchall(), [(1,)])
            self.assertEqual(db.execute(
                "SELECT effect_id,issue_count,phase FROM effects"
            ).fetchall(), [("synthetic-unknown", 1, "unknown")])

    def competitor(self, slot):
        context = multiprocessing.get_context("fork")
        receiver, sender = context.Pipe(duplex=False)
        process = context.Process(
            target=competing_process, args=(str(slot), sender)
        )
        process.start()
        sender.close()
        try:
            self.assertTrue(receiver.poll(5), "COMPETITOR_DID_NOT_REPORT")
            result = receiver.recv()
        finally:
            receiver.close()
            process.join(5)
            if process.is_alive():
                process.terminate()
                process.join()
        self.assertEqual(process.exitcode, 0)
        return result

    def test_01_only_canonical_physical_role_slot_is_valid(self):
        module = self.seam()
        _, lab, slot = self.fixture()
        self.assertEqual(module.validate_root(lab, slot), slot)

        for invalid in (lab, lab / "tmp" / ROLE, lab / ROLE):
            invalid.mkdir(parents=True, exist_ok=True)
            with self.subTest(path=str(invalid)):
                with self.assertRaises(ValueError):
                    module.validate_root(lab, invalid)

        link = slot.parent / "linked-role"
        link.symlink_to(slot, target_is_directory=True)
        with self.assertRaises(ValueError):
            module.validate_root(lab, link)

        # A canonical-looking final component cannot hide a symlinked ancestor.
        other_lab = lab.parent / "linked-lab"
        other_lab.symlink_to(lab, target_is_directory=True)
        with self.assertRaises(ValueError):
            module.validate_root(
                other_lab,
                other_lab / ".codex-lab/role-runtime/slots" / ROLE,
            )

    def test_02_exclusive_lock_survives_rejection_and_process_contention(self):
        module = self.seam()
        _, _, slot = self.fixture()
        lock = slot / ".exclusive.lock"

        with module.exclusive(slot):
            identity = (lock.stat().st_dev, lock.stat().st_ino)
            with self.assertRaises(ValueError):
                with module.exclusive(slot):
                    self.fail("SECOND_FD_ACQUIRED_HELD_SLOT")
            self.assertEqual(self.competitor(slot), "rejected")
            self.assertEqual(
                (lock.stat().st_dev, lock.stat().st_ino), identity
            )

        self.assertTrue(lock.is_file(), "CANONICAL_LOCK_WAS_DELETED")
        self.assertEqual(self.competitor(slot), "acquired")
        self.assertEqual(
            (lock.stat().st_dev, lock.stat().st_ino), identity
        )

    def test_03_backup_restore_preserve_identity_history_and_unknown_effect(self):
        module = self.seam()
        root, lab, slot = self.fixture()
        before = self.public_files(slot)
        backup = root / "isolated-backup"

        # Backup must acquire the same exclusive slot lock.
        with module.exclusive(slot):
            with self.assertRaises(ValueError):
                module.backup(slot, backup, NATIVE)
        self.assertFalse(backup.exists())

        self.assertEqual(module.backup(slot, backup, NATIVE), backup)
        self.assertEqual(self.public_files(slot), before)
        self.assert_state(slot)
        for relative, raw in before.items():
            self.assertEqual((backup / relative).read_bytes(), raw)
        self.assertFalse(any(
            path.name in ("auth.json", "credentials")
            for path in backup.rglob("*")
        ))

        restored_lab = root / "restored-lab"
        restored = (
            restored_lab / ".codex-lab/role-runtime/slots" / ROLE
        )
        restored.parent.mkdir(parents=True)
        self.assertEqual(
            module.restore(backup, restored, NATIVE), restored
        )
        self.assertEqual(
            module.validate_root(restored_lab, restored), restored
        )
        self.assert_state(restored)
        for relative, raw in before.items():
            self.assertEqual((restored / relative).read_bytes(), raw)
        self.assertFalse(any(
            path.name in ("auth.json", "credentials")
            for path in restored.rglob("*")
        ))

    def test_04_wrong_identity_tampering_and_existing_targets_are_rejected(self):
        module = self.seam()
        root, _, slot = self.fixture()

        with self.assertRaises(ValueError):
            module.backup(slot, root / "wrong-id-backup", OTHER)

        existing_backup = root / "existing-backup"
        existing_backup.mkdir()
        marker = existing_backup / "keep"
        marker.write_bytes(b"unchanged")
        with self.assertRaises(ValueError):
            module.backup(slot, existing_backup, NATIVE)
        self.assertEqual(marker.read_bytes(), b"unchanged")

        backup = root / "verified-backup"
        module.backup(slot, backup, NATIVE)
        restore_parent = root / "restore-lab/.codex-lab/role-runtime/slots"
        restore_parent.mkdir(parents=True)
        target = restore_parent / ROLE

        with self.assertRaises(ValueError):
            module.restore(backup, target, OTHER)
        self.assertFalse(target.exists())

        target.mkdir()
        retained = target / "keep"
        retained.write_bytes(b"do-not-overwrite")
        with self.assertRaises(ValueError):
            module.restore(backup, target, NATIVE)
        self.assertEqual(retained.read_bytes(), b"do-not-overwrite")

        retained.unlink()
        target.rmdir()
        (backup / "history/rollout.jsonl").write_bytes(b"tampered\n")
        with self.assertRaises(ValueError):
            module.restore(backup, target, NATIVE)
        self.assertFalse(target.exists(), "TAMPERED_RESTORE_LEFT_TARGET")


if __name__ == "__main__":
    unittest.main(verbosity=2)
