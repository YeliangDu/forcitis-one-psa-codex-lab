"""Trusted local approval loader acceptance; temporary fixtures, hosted only."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

INITIAL = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
HEAD = "b" * 40


def loader():
    source = Path(__file__).with_name("mq_lab_authorization.py")
    if not source.exists(): return lambda directory, number: None
    spec = importlib.util.spec_from_file_location("mq_authorization_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.load_approval


class LocalApprovalAcceptanceTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.directory = self.root / "merge-queue"
        self.directory.mkdir()
        self.approvals = self.directory / "approvals"
        self.approvals.mkdir()
        self.path = self.approvals / "15.json"
        self.record = dict(number=15, head=HEAD, base=INITIAL, target="main", repo_id=1449415666,
                           required_reviews=["首轮", "终审"],
                           reviewers={"首轮": "codex-首轮-one-design", "终审": "codex-终审-one-dev-identity"},
                           review_ids={"首轮": 501, "终审": 502}, run_id=100, run_attempt=1,
                           mode="normal", author="one-platform")

    def write(self, record=None, raw=None):
        self.path.write_bytes(raw if raw is not None else json.dumps(
            self.record if record is None else record, ensure_ascii=False).encode("utf-8"))
        self.path.chmod(0o600)

    def load(self, directory=None, number=15):
        return loader()(directory if directory is not None else self.directory, number)

    def test_normal_and_pinned_bootstrap_return_exact_data_without_writes(self):
        for bootstrap in (False, True):
            with self.subTest(bootstrap=bootstrap):
                record = copy.deepcopy(self.record)
                if bootstrap: record.update(mode="bootstrap", baseline_run_id=42418209077)
                self.write(record)
                before = self.path.read_bytes()
                self.assertEqual(record, self.load())
                self.assertEqual(before, self.path.read_bytes())
                self.assertEqual([self.path], list(self.approvals.iterdir()))

    def test_absence_returns_none_without_creating_files(self):
        self.approvals.rmdir()
        self.assertIsNone(self.load())
        self.assertEqual([], list(self.directory.iterdir()))
        self.approvals.mkdir()
        self.assertIsNone(self.load())
        self.assertEqual([], list(self.approvals.iterdir()))

    def test_wrong_scope_roles_ids_and_arbitrary_metadata_rejected(self):
        changes = [dict(number=16), dict(repo_id=True), dict(repo_id=0), dict(run_id="100"),
                   dict(run_attempt=0), dict(head="B" * 40), dict(base="bad"), dict(target="topic"),
                   dict(author="one-design"), dict(required_reviews=[]), dict(mode="skip"),
                   dict(review_ids={"首轮": 0, "终审": 502}),
                   dict(reviewers={"首轮": "other-首轮-one-design", "终审": "codex-终审-one-dev-identity"}),
                   dict(reviewers={"首轮": "codex-首轮-shared", "终审": "codex-终审-shared"}),
                   dict(reviewers={"首轮": "codex-首轮-one-design"}),
                   dict(url="https://synthetic.invalid"), dict(command="synthetic"),
                   dict(baseline_run_id=42418209077)]
        for change in changes:
            with self.subTest(change=change):
                self.write(dict(self.record, **change))
                with self.assertRaises((ValueError, OSError)): self.load()
        for field in self.record:
            record = dict(self.record)
            del record[field]
            self.write(record)
            with self.subTest(missing=field):
                with self.assertRaises((ValueError, OSError)): self.load()

    def test_bootstrap_requires_exact_pr_base_and_independent_baseline(self):
        for change in (dict(number=16), dict(base="c" * 40), dict(baseline_run_id=100),
                       dict(baseline_run_id=None)):
            with self.subTest(change=change):
                record = dict(self.record, mode="bootstrap", baseline_run_id=42418209077)
                record.update(change)
                self.write(record)
                with self.assertRaises((ValueError, OSError)): self.load()

    def test_links_modes_nonregular_and_nonphysical_paths_rejected(self):
        self.write()
        for mode in (0o644, 0o660, 0o400):
            with self.subTest(mode=oct(mode)):
                self.path.chmod(mode)
                with self.assertRaises((ValueError, OSError)): self.load()
        self.path.chmod(0o600)
        hardlink = self.root / "extra-link"
        os.link(self.path, hardlink)
        with self.assertRaises((ValueError, OSError)): self.load()
        hardlink.unlink()
        saved = self.root / "saved.json"
        self.path.rename(saved)
        self.path.symlink_to(saved)
        with self.assertRaises((ValueError, OSError)): self.load()
        self.path.unlink()
        self.path.mkdir()
        with self.assertRaises((ValueError, OSError)): self.load()
        self.path.rmdir()
        self.approvals.rmdir()
        self.approvals.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises((ValueError, OSError)): self.load()
        self.approvals.unlink()
        self.approvals.mkdir()
        alias = self.root / "alias"
        alias.symlink_to(self.directory, target_is_directory=True)
        for directory in (alias, Path("relative"), self.root / "missing", self.directory / ".."):
            with self.subTest(directory=str(directory)):
                with self.assertRaises((ValueError, OSError)): self.load(directory)
        for number in (True, 0, -1, "15", "../15"):
            with self.subTest(number=number):
                with self.assertRaises((ValueError, OSError)): self.load(number=number)

    def test_malformed_oversize_duplicate_keys_and_utf8_rejected(self):
        encoded = json.dumps(self.record, ensure_ascii=False).encode("utf-8")
        duplicate = b'{"number":15,' + encoded[1:]
        nested_duplicate = encoded.replace(b'"review_ids": {', b'"review_ids": {"' + "首轮".encode("utf-8") + b'": 501, ')
        for raw in (b"not-json", b"[]", b"null", b"\xff", duplicate, nested_duplicate,
                    b" " * (16 * 1024 + 1) + encoded):
            with self.subTest(length=len(raw)):
                self.write(raw=raw)
                with self.assertRaises((ValueError, OSError)): self.load()


if __name__ == "__main__":
    unittest.main()
