"""Lab #55 independent ordinary V2 cleanup crash/restart acceptance.

Lead-hosted Linux only. Real mq.py once, Git, FIFO/head files/flock and SQLite;
only offline authorization/write ports and precise process-death hooks differ.
"""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from lab55_cleanup_fixture import snapshot
from mq_lab_journal_v2 import ALTER, BINDINGS, V1, ReleaseJournalV2
from mq_queue import Queue


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class Fixture:
    def __init__(self, case):
        self.case = case
        self.temporary = tempfile.TemporaryDirectory(prefix="lab55-")
        case.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.repository = self.root / "forcitis-one-psa-codex-lab"
        self.repository.mkdir()
        self.environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        self.environment.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1",
                                GIT_AUTHOR_DATE="2026-01-01T00:00:00Z", GIT_COMMITTER_DATE="2026-01-01T00:00:00Z",
                                PYTHONDONTWRITEBYTECODE="1")
        self.git("init", "--initial-branch=main", ".")
        for key, value in (("user.name", "Lab55 Synthetic"), ("user.email", "lab55@example.invalid"),
                           ("core.hooksPath", os.devnull)):
            self.git("config", key, value)
        self.git("remote", "add", "origin", "git@github.com:YeliangDu/forcitis-one-psa-codex-lab.git")
        (self.repository / "synthetic-base.txt").write_text("base\n")
        self.git("add", ".")
        self.git("commit", "-m", "isolated baseline")
        self.base = self.git("rev-parse", "HEAD")
        self.heads = {}
        for number in (61, 62, 63):
            self.git("checkout", "-b", "synthetic-" + str(number), self.base)
            (self.repository / ("synthetic-" + str(number) + ".txt")).write_text(str(number) + "\n")
            self.git("add", ".")
            self.git("commit", "-m", "synthetic topic " + str(number))
            self.heads[number] = self.git("rev-parse", "HEAD")
        self.git("checkout", "main")
        self.directory = self.repository / ".git/merge-queue"
        self.directory.mkdir(mode=0o700)
        self.directory.chmod(0o700)
        self.database = self.directory / "releases.sqlite3"
        self.history = [(n, "confirmed", str(n) * 40, str(n + 1) * 40, "main",
                         "lab55-historical-" + str(n), str(n + 2) * 40, "ordinary") for n in range(1, 6)]
        with sqlite3.connect(self.database) as db:
            db.execute(V1)
            db.execute(ALTER)
            db.execute(BINDINGS)
            db.execute("pragma user_version=2")
            db.executemany("insert into releases values (?,?,?,?,?,?,?,?)", self.history)
        self.database.chmod(0o600)
        ReleaseJournalV2(self.database)  # Existing exact migrated fixture only.
        self.queue_path = self.directory / "merge.queue"
        self.queue_path.write_text("")
        self.queue = Queue(str(self.queue_path))
        self.queue.enqueue(61, 62, 63)
        for number, head in self.heads.items():
            self.queue.set_head(number, head)
        settings = dict(repository=str(self.repository), intents={str(n): dict(number=n, head=h,
                        base=self.base, target="main") for n, h in self.heads.items()})
        (self.root / "fixture.json").write_text(json.dumps(settings))
        self.original_heads = Path(str(self.queue_path) + ".heads").read_text()
        self.command("plan", 62, mode="initial", expect=0)
        self.command("plan", 63, mode="initial", expect=0)

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.repository), *args], env=self.environment,
                                       text=True, stderr=subprocess.PIPE, timeout=20).strip()

    def command(self, command="once", number=61, *, mode="recover", fault="none", expect=None, timeout=20):
        result = subprocess.run([sys.executable, "-B", str(HERE / "lab55_cleanup_fixture.py"),
                                 str(self.root), command, str(number), mode, fault],
                                env=self.environment, capture_output=True, text=True, timeout=timeout, check=False)
        print("LAB55_COMMAND " + json.dumps(dict(test=self.case.id(), command=command, number=number,
              mode=mode, fault=fault, returncode=result.returncode, stdout=result.stdout, stderr=result.stderr,
              state=snapshot(self.root), ports=self.ports()), sort_keys=True), flush=True)
        if expect is not None:
            self.case.assertEqual(expect, result.returncode, "LAB55_CAPABILITY: " + result.stdout + result.stderr)
        return result

    def ports(self):
        path = self.root / "ports.jsonl"
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def attempts(self):
        return sum(row["operation"] == "send-attempt" for row in self.ports())

    def authorizations(self):
        return sum(row["operation"] in ("load", "verify") for row in self.ports())

    def core(self):
        state = snapshot(self.root)
        state["database_sha256"] = sha(self.database) if self.database.exists() else None
        return state

    def cut(self, fault="after-remove"):
        code = 73 if fault == "after-remove" else 74
        self.command(mode="initial", fault=fault, expect=code)
        marker_path = self.root / (fault + ".json")
        marker = json.loads(marker_path.read_text())
        print("LAB55_CHECKPOINT " + json.dumps(dict(test=self.case.id(), marker_sha256=sha(marker_path),
              marker=marker), sort_keys=True), flush=True)
        self.case.assertTrue(marker["lock_held"])
        self.case.assertFalse(marker["fifo_lock_held"])
        self.case.assertEqual(fault == "partial-heads", marker["heads_lock_held"])
        row = next(row for row in marker["snapshot"]["rows"] if row[0] == 61)
        self.case.assertEqual(("confirmed", "ordinary"), (row[1], row[7]))
        self.case.assertEqual(marker["snapshot"]["main"], row[6])
        self.case.assertEqual([self.base, self.heads[61]], self.git("show", "-s", "--format=%P", row[6]).split())
        self.case.assertEqual([62, 63], self.queue.numbers())
        self.case.assertEqual(1, self.attempts())
        self.case.assertEqual(1, sum(x["operation"] == "send-completed" for x in self.ports()))
        return marker

    def assert_tail(self):
        self.case.assertEqual([62, 63], self.queue.numbers())
        for number in (62, 63):
            self.case.assertEqual(self.heads[number], self.queue.head_of(number))
        stamps = Path(str(self.queue_path) + ".heads").read_text().splitlines()
        self.case.assertEqual({62, 63}, {int(line.split()[0]) for line in stamps})
        self.case.assertEqual(2, len(stamps), "normal cleanup leaves no orphan or duplicate stamp")
        with sqlite3.connect(self.database) as db:
            self.case.assertEqual(self.history, db.execute("select * from releases where number<=5 order by number").fetchall())
            self.case.assertEqual([], db.execute("select * from upgrade_bindings").fetchall())
            self.case.assertEqual((2,), db.execute("pragma user_version").fetchone())

    def assert_held_unchanged(self, number=61, *, timeout=20):
        before, sends, authorizations = self.core(), self.attempts(), self.authorizations()
        result = self.command(number=number, timeout=timeout)
        self.case.assertNotEqual(0, result.returncode)
        self.case.assertIn("HOLD:", result.stdout + result.stderr)
        self.case.assertEqual(before, self.core())
        self.case.assertEqual(sends, self.attempts(), "recovery must not resend")
        self.case.assertEqual(authorizations, self.authorizations(), "recovery must not seek old approval")


class OrdinaryConfirmedCleanupAcceptanceTest(unittest.TestCase):
    def fixture(self):
        if not sys.platform.startswith("linux"):
            self.fail("LAB55_CAPABILITY: hosted Linux/proc/flock required, not a behavior RED")
        return Fixture(self)

    def test_confirmed_orphan_recovers_in_fresh_original_cli_without_resend(self):
        f = self.fixture()
        marker = f.cut()
        self.assertEqual(f.original_heads, marker["snapshot"]["heads"])
        before, authorizations = f.core(), f.authorizations()
        result = f.command()
        self.assertEqual(1, f.attempts(), "no new send after durable confirmation")
        self.assertEqual(authorizations, f.authorizations(), "no old approval reused")
        self.assertEqual(before["rows"], f.core()["rows"])
        self.assertEqual(before["refs"], f.core()["refs"])
        self.assertIsNone(f.queue.head_of(61), "CONFIRMED_CLEANUP_ORPHAN_STAMP_SURVIVED_RESTART")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        f.assert_tail()
        cleaned = f.core()
        f.command()  # Repeated recovery is a read-only no-op, regardless of CLI wording.
        self.assertEqual(cleaned, f.core())
        self.assertEqual(1, f.attempts())
        self.assertEqual(authorizations, f.authorizations())

    def test_interrupted_head_persistence_does_not_truncate_other_heads(self):
        f = self.fixture()
        marker = f.cut("partial-heads")
        self.assertEqual(f.original_heads, marker["snapshot"]["heads"],
                         "HEAD_SIDEFILE_TORN_WRITE_DESTROYED_OTHER_ADMISSIONS")
        for number in (62, 63):
            self.assertEqual(f.heads[number], f.queue.head_of(number))
        f.command(expect=0)
        f.assert_tail()
        self.assertEqual(1, f.attempts())

    def test_normal_confirmed_cleanup_and_already_cleaned_repeat_preserve_tail(self):
        f = self.fixture()
        f.command(mode="initial", expect=0)
        self.assertIsNone(f.queue.head_of(61))
        f.assert_tail()
        before, authorizations = f.core(), f.authorizations()
        f.command()
        f.command()
        self.assertEqual(before, f.core())
        self.assertEqual(1, f.attempts())
        self.assertEqual(authorizations, f.authorizations())

    def test_reserved_missing_record_or_missing_database_never_authorizes_orphan_cleanup(self):
        for kind in ("reserved", "missing-record", "missing-database"):
            with self.subTest(kind=kind):
                f = self.fixture()
                f.cut()
                if kind == "missing-database":
                    f.database.unlink()
                else:
                    with sqlite3.connect(f.database) as db:
                        if kind == "reserved":
                            db.execute("update releases set status='reserved',merge_sha=NULL where number=61")
                        else:
                            db.execute("delete from releases where number=61")
                f.assert_held_unchanged()

    def test_conflicting_duplicate_or_malformed_head_stamps_hold(self):
        for kind in ("conflict", "duplicate", "malformed"):
            with self.subTest(kind=kind):
                f = self.fixture()
                f.cut()
                heads = Path(str(f.queue_path) + ".heads")
                if kind == "conflict":
                    f.queue.set_head(61, f.heads[62])
                elif kind == "duplicate":
                    with heads.open("a") as handle:
                        handle.write("61 " + f.heads[63] + "\n")
                else:
                    heads.write_text(heads.read_text().replace("61 " + f.heads[61], "61 not-a-sha"))
                f.assert_held_unchanged()

    def test_wrong_number_head_base_or_merge_in_existing_record_holds(self):
        for kind in ("number", "head", "base", "merge_sha"):
            with self.subTest(kind=kind):
                f = self.fixture()
                f.cut()
                with sqlite3.connect(f.database) as db:
                    if kind == "number":
                        db.execute("update releases set number=71 where number=61")
                    else:
                        db.execute("update releases set " + kind + "=? where number=61", (f.heads[62],))
                f.assert_held_unchanged()

    def test_active_canonical_or_heads_lock_holds_without_mutation(self):
        for suffix in (".merge-lock", ".heads-lock"):
            with self.subTest(lock=suffix):
                f = self.fixture()
                f.cut()
                with Path(str(f.queue_path) + suffix).open("a") as lock:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    f.assert_held_unchanged(timeout=5)

    def test_confirmed_item_still_behind_another_fifo_head_cannot_be_skipped(self):
        f = self.fixture()
        f.cut()
        f.queue.enqueue(61)
        f.assert_held_unchanged()

    def test_corrupt_fifo_or_sqlite_is_not_cleanup_authority(self):
        for kind in ("fifo", "database"):
            with self.subTest(kind=kind):
                f = self.fixture()
                f.cut()
                if kind == "fifo":
                    f.queue_path.write_text("62\nnot-a-queue-entry\n63\n")
                else:
                    f.database.write_bytes(b"corrupt synthetic sqlite fixture")
                f.assert_held_unchanged()

    def test_confirmed_record_without_actual_target_merge_proof_holds(self):
        f = self.fixture()
        f.cut()
        f.git("update-ref", "refs/heads/main", f.heads[62])
        f.assert_held_unchanged()

    def test_preadmission_stamp_without_record_is_not_confirmed_cleanup(self):
        f = self.fixture()
        f.queue.set_head(90, f.heads[62])
        f.assert_held_unchanged(number=90)
        self.assertEqual(0, f.attempts())
        self.assertEqual([61, 62, 63], f.queue.numbers())


if __name__ == "__main__":
    unittest.main(verbosity=2)
