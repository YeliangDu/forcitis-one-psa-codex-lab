"""Durable journal acceptance with real temporary SQLite storage; hosted only."""
import copy
import importlib.util
from pathlib import Path
import sqlite3
import stat
import tempfile
import unittest

HEAD, BASE, MERGE, OTHER = (c * 40 for c in "abcd")


def journal_type():
    source = Path(__file__).with_name("mq_lab_journal.py")
    if not source.exists():
        class ManualJournal:
            def __init__(self, path): pass
            def load(self, number): return None
            def reserve(self, record): return False
            def confirm(self, record): return False
        return ManualJournal
    spec = importlib.util.spec_from_file_location("mq_journal_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.ReleaseJournal


class ReleaseJournalAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.path = self.root / "journal.sqlite"
        self.reserved = dict(status="reserved", number=14, head=HEAD, base=BASE,
                             target="main", release_key="synthetic-release-key")

    def open(self, path=None):
        journal = journal_type()(path or self.path)
        close = getattr(journal, "close", None)
        if callable(close): self.addCleanup(close)
        return journal

    def refused(self, operation):
        try:
            result = operation()
        except (ValueError, OSError, sqlite3.Error):
            return
        self.assertIs(result, False, "invalid operation must fail closed")

    def test_reservation_is_real_sqlite_private_and_survives_restart(self):
        journal = self.open()
        self.assertIsNone(journal.load(14))
        self.assertIs(journal.reserve(copy.deepcopy(self.reserved)), True)
        self.assertTrue(self.path.is_file())
        self.assertEqual(0o600, stat.S_IMODE(self.path.stat().st_mode))
        self.assertEqual(b"SQLite format 3\x00", self.path.read_bytes()[:16])
        with sqlite3.connect(self.path) as db:
            self.assertEqual("ok", db.execute("pragma integrity_check").fetchone()[0])
        reopened = self.open()
        self.assertEqual(self.reserved, reopened.load(14))
        self.assertIsNone(reopened.load(27))

    def test_duplicate_or_conflicting_reserve_never_overwrites(self):
        journal = self.open()
        self.assertIs(journal.reserve(self.reserved), True)
        for field, value in ((None, None), ("head", OTHER), ("base", OTHER),
                             ("release_key", "synthetic-other-key")):
            with self.subTest(field=field):
                record = dict(self.reserved)
                if field: record[field] = value
                self.refused(lambda: self.open().reserve(record))
                self.assertEqual(self.reserved, self.open().load(14))

    def test_exact_confirmation_survives_reopen_and_cannot_regress(self):
        journal = self.open()
        self.assertIs(journal.reserve(self.reserved), True)
        confirmed = dict(self.reserved, status="confirmed", merge_sha=MERGE)
        self.assertIs(journal.confirm(confirmed), True)
        self.assertEqual(confirmed, self.open().load(14))
        self.refused(lambda: self.open().reserve(self.reserved))
        self.assertEqual(confirmed, self.open().load(14))

    def test_missing_or_mismatched_confirmation_preserves_reservation(self):
        journal = self.open()
        confirmed = dict(self.reserved, status="confirmed", merge_sha=MERGE)
        self.refused(lambda: journal.confirm(confirmed))
        self.assertIsNone(journal.load(14))
        self.assertIs(journal.reserve(self.reserved), True)
        for field, value in (("head", OTHER), ("base", OTHER),
                             ("release_key", "synthetic-other"), ("target", "topic"),
                             ("number", 27), ("merge_sha", "bad")):
            with self.subTest(field=field):
                wrong = dict(confirmed, **{field: value})
                self.refused(lambda: self.open().confirm(wrong))
                self.assertEqual(self.reserved, self.open().load(14))
                self.assertIsNone(self.open().load(27))

    def test_invalid_shapes_and_strict_identity_never_persist(self):
        journal = self.open()
        invalid = [dict(self.reserved, number=True), dict(self.reserved, number=0),
                   dict(self.reserved, number="14"), dict(self.reserved, head="B" * 40),
                   dict(self.reserved, base="bad"), dict(self.reserved, target="topic"),
                   dict(self.reserved, release_key=""), dict(self.reserved, release_key=None),
                   dict(self.reserved, status="confirmed"), dict(self.reserved, metadata="unapproved")]
        for key in self.reserved:
            record = dict(self.reserved)
            del record[key]
            invalid.append(record)
        for record in invalid:
            with self.subTest(record=record):
                self.refused(lambda: journal.reserve(record))
                self.assertIsNone(journal.load(14))
        # Positive proof keeps the missing-module baseline at a behavior assertion.
        self.assertIs(journal.reserve(self.reserved), True)

    def test_unsafe_paths_missing_parents_and_corruption_rejected(self):
        sentinel = self.root / "sentinel"
        sentinel.write_bytes(b"preserve")
        link = self.root / "linked.sqlite"
        link.symlink_to(sentinel)
        alias = self.root / "alias"
        alias.symlink_to(self.root, target_is_directory=True)
        directory = self.root / "directory.sqlite"
        directory.mkdir()
        paths = [link, alias / "escaped.sqlite", self.root / "missing" / "journal.sqlite",
                 directory, Path("relative-journal.sqlite")]
        for path in paths:
            with self.subTest(path=str(path)):
                with self.assertRaises((ValueError, OSError, sqlite3.Error)):
                    self.open(path).reserve(self.reserved)
        self.assertEqual(b"preserve", sentinel.read_bytes())
        self.assertFalse((self.root / "missing").exists())
        self.assertFalse((self.root / "escaped.sqlite").exists())
        self.path.write_bytes(b"not a SQLite database")
        with self.assertRaises((ValueError, OSError, sqlite3.Error)):
            self.open().load(14)


if __name__ == "__main__":
    unittest.main()
