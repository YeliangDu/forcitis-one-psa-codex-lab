"""Lab-local queue cutover acceptance with temporary files; hosted only."""
import importlib.util
from pathlib import Path
import tempfile
import types
import unittest

SLUG = "YeliangDu/forcitis-one-psa-codex-lab"
URL = "git@github.com:" + SLUG + ".git"


def target():
    source = Path(__file__).with_name("mq_lab.py")
    if not source.exists():
        return types.SimpleNamespace(
            checkout_profile=lambda *args: "legacy",
            queue_command=lambda *args, **kwargs: dict(status="hold", queue_present=False,
                                                       queued=[], reason="manual-no-canonical-queue"))
    spec = importlib.util.spec_from_file_location("mq_lab_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class MqLabAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.common = self.root / "forcitis-one-psa-codex-lab" / ".git"
        self.common.mkdir(parents=True)
        self.file = self.common / "merge-queue" / "merge.queue"
        self.remote_calls = 0

    def read_repo(self):
        self.remote_calls += 1
        return dict(id=123, full_name=SLUG, private=True)

    def command(self, command, common=None, reader=None):
        result = target().queue_command(command, common or self.common, reader)
        self.assertIn(result["status"], ("ok", "hold"))
        self.assertIsInstance(result["queue_present"], bool)
        self.assertIsInstance(result["queued"], list)
        self.assertTrue(result["reason"])
        return result

    def snapshot(self):
        return {str(path.relative_to(self.root)): path.read_bytes()
                for path in self.root.rglob("*") if path.is_file() and not path.is_symlink()}

    def test_exact_private_lab_profile_and_unrelated_legacy(self):
        profile = target().checkout_profile
        self.assertEqual("lab", profile(self.common.parent, ["origin"], [URL], [URL]))
        self.assertEqual("lab", profile(self.root / "managed-name", ["origin"], [URL], [URL]))
        self.assertEqual("legacy", profile(self.root / "synthetic-unrelated", ["origin"],
                                            ["git@github.com:synthetic/unrelated.git"],
                                            ["git@github.com:synthetic/unrelated.git"]))
        for remotes, fetch, push in ((["origin", "other"], [URL], [URL]),
                                     (["origin"], [URL], ["git@github.com:synthetic/foreign.git"]),
                                     (["origin"], [], [URL]), (["origin"], [URL, URL], [URL]),
                                     (["origin"], ["git@github.com:synthetic/foreign.git"], [URL])):
            with self.subTest(remotes=remotes, fetch=fetch, push=push):
                with self.assertRaises(ValueError):
                    profile(self.common.parent, remotes, fetch, push)

    def test_missing_and_empty_status_distinguished_without_io_side_effects(self):
        for command in ("list", "status"):
            with self.subTest(command=command):
                result = self.command(command, reader=self.read_repo)
                self.assertEqual("ok", result["status"])
                self.assertFalse(result["queue_present"])
                self.assertEqual([], result["queued"])
                self.assertFalse(self.file.parent.exists())
        self.file.parent.mkdir()
        self.file.write_text("")
        before = self.snapshot()
        for command in ("list", "status"):
            result = self.command(command, reader=self.read_repo)
            self.assertEqual("ok", result["status"])
            self.assertTrue(result["queue_present"])
            self.assertEqual([], result["queued"])
        self.assertEqual(before, self.snapshot())
        self.assertEqual(0, self.remote_calls)

    def test_canonical_init_idempotent_never_truncates_and_uses_merge_lock(self):
        result = self.command("init", reader=self.read_repo)
        self.assertEqual("ok", result["status"])
        self.assertTrue(result["queue_present"])
        self.assertTrue(self.file.is_file())
        self.assertEqual("", self.file.read_text())
        self.assertTrue(Path(str(self.file) + ".merge-lock").exists())
        self.file.write_text("14\n27\n")
        result = self.command("init", reader=self.read_repo)
        self.assertEqual("ok", result["status"])
        self.assertEqual([14, 27], result["queued"])
        self.assertEqual("14\n27\n", self.file.read_text())
        self.assertEqual(2, self.remote_calls)

    def test_init_missing_or_wrong_identity_cannot_create_queue(self):
        for identity in (None, dict(id=123, full_name=SLUG, private=False),
                         dict(id=123, full_name="synthetic/foreign", private=True),
                         dict(id=0, full_name=SLUG, private=True)):
            with self.subTest(identity=identity):
                reader = None if identity is None else lambda: identity
                self.assertEqual("hold", self.command("init", reader=reader)["status"])
                self.assertFalse(self.file.exists())

    def test_dangerous_paths_and_symlink_parent_or_queue_fail_closed(self):
        outside = self.root / "outside"
        outside.mkdir()
        wrong = self.root / "not-git"
        wrong.mkdir()
        alias = self.root / "alias.git"
        alias.symlink_to(self.common, target_is_directory=True)
        for path in (wrong, alias, self.common / ".."):
            with self.subTest(path=str(path)):
                self.assertEqual("hold", self.command("init", path, self.read_repo)["status"])
                self.assertFalse(self.file.exists())
        self.file.parent.symlink_to(outside, target_is_directory=True)
        self.assertEqual("hold", self.command("init", reader=self.read_repo)["status"])
        self.assertEqual([], list(outside.iterdir()))
        self.file.parent.unlink()
        self.file.parent.mkdir()
        sentinel = outside / "sentinel"
        sentinel.write_text("preserve")
        self.file.symlink_to(sentinel)
        for command in ("init", "list", "status"):
            self.assertEqual("hold", self.command(command, reader=self.read_repo)["status"])
        self.assertEqual("preserve", sentinel.read_text())

    def test_all_legacy_mutators_hold_without_remote_or_local_writes(self):
        self.file.parent.mkdir()
        self.file.write_text("14\n")
        before = self.snapshot()
        for command in ("enqueue", "remove", "before", "resume", "run", "once"):
            with self.subTest(command=command):
                result = self.command(command, reader=self.read_repo)
                self.assertEqual("hold", result["status"])
                self.assertTrue(result["queue_present"])
                self.assertEqual([14], result["queued"])
                self.assertEqual(before, self.snapshot())
        self.assertEqual(0, self.remote_calls)


if __name__ == "__main__":
    unittest.main()
