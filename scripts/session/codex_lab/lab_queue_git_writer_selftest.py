"""Disposable Git writer contract with fake argv/JSON ports only."""
import copy
import importlib.util
from pathlib import Path
import types
import unittest

BASE, HEAD, TREE, MERGE, LATER = (char * 40 for char in "abcde")
SLUG = "YeliangDu/forcitis-one-psa-codex-lab"
TARGET = "lab/disposable-synthetic-14"
REF = "refs/heads/" + TARGET
ORIGIN = "git@github.com:" + SLUG + ".git"


def writer_type():
    source = Path(__file__).with_name("lab_queue_git_writer.py")
    if not source.exists():
        return lambda git, read: types.SimpleNamespace(send=lambda intent: None,
                                                       readback=lambda intent: None)
    spec = importlib.util.spec_from_file_location("lab_git_writer_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.GitDisposableWriter


class LabQueueGitWriterAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.intent = dict(number=14, base=BASE, head=HEAD, target=TARGET)
        self.commands, self.reads = [], []
        self.failure = None
        self.origin = ORIGIN
        repo = dict(id=123, full_name=SLUG, private=True)
        self.data = {
            "": repo,
            "pulls/14": dict(number=14, state="open", draft=False, merged=False,
                             head=dict(sha=HEAD, ref="reviewed", repo=copy.deepcopy(repo)),
                             base=dict(sha=BASE, ref=TARGET, repo=copy.deepcopy(repo))),
            "commits/" + MERGE: dict(sha=MERGE, parents=[dict(sha=BASE), dict(sha=HEAD)]),
            "git/ref/heads/" + TARGET: dict(ref=REF, object=dict(sha=MERGE)),
            "compare/" + MERGE + "..." + LATER:
                dict(status="ahead", merge_base_commit=dict(sha=MERGE)),
        }
        self.parent_output = BASE + " " + HEAD

    def git(self, args):
        self.assertIsInstance(args, list)
        self.commands.append(list(args))
        if self.failure == args[0]:
            raise RuntimeError("synthetic Git failure")
        if args == ["remote", "-v"]:
            return f"origin\t{self.origin} (fetch)\norigin\t{self.origin} (push)\n"
        if args == ["remote"]:
            return "origin\n"
        if args in (["remote", "get-url", "origin"], ["remote", "get-url", "--push", "origin"]):
            return self.origin + "\n"
        if args == ["merge-tree", "--write-tree", BASE, HEAD]:
            return TREE + "\n"
        if args[0] == "commit-tree":
            self.assertEqual(["commit-tree", TREE, "-p", BASE, "-p", HEAD, "-m"], args[:7])
            self.assertEqual(8, len(args))
            self.assertTrue(args[7])
            return MERGE + "\n"
        if args == ["show", "-s", "--format=%P", MERGE]:
            return self.parent_output + "\n"
        if args == ["merge-base", "--is-ancestor", BASE, MERGE]:
            return ""
        if args[0] == "push":
            self.assertEqual(["push", "--porcelain", f"--force-with-lease={REF}:{BASE}",
                              "origin", f"{MERGE}:{REF}"], args)
            return "synthetic push accepted"
        self.fail("unexpected git argv: " + repr(args))

    def read(self, path):
        self.reads.append(path)
        self.assertIn(path, self.data, "only fixed personal-Lab relative GET endpoints")
        return copy.deepcopy(self.data[path])

    def writer(self):
        return writer_type()(self.git, self.read)

    def assert_refused(self, operation):
        # A send may refuse by exception or by returning without writing.
        try:
            operation()
        except (ValueError, RuntimeError):
            pass
        self.assertFalse(any(args[0] == "push" for args in self.commands))

    def test_exact_reviewed_head_merge_parents_and_base_lease(self):
        self.writer().send(copy.deepcopy(self.intent))
        self.assertIn("", self.reads)
        self.assertIn("pulls/14", self.reads)
        self.assertIn(["merge-tree", "--write-tree", BASE, HEAD], self.commands)
        self.assertIn(["show", "-s", "--format=%P", MERGE], self.commands)
        self.assertIn(["merge-base", "--is-ancestor", BASE, MERGE], self.commands)
        pushes = [args for args in self.commands if args[0] == "push"]
        self.assertEqual([["push", "--porcelain", f"--force-with-lease={REF}:{BASE}",
                           "origin", f"{MERGE}:{REF}"]], pushes)

    def test_main_foreign_or_malformed_intent_never_writes(self):
        for field, value in (("target", "main"), ("target", "lab/disposable-../main"),
                             ("target", "lab/disposable-Upper"), ("number", True),
                             ("number", 0), ("head", "BAD"), ("base", None)):
            with self.subTest(field=field, value=value):
                self.commands, self.reads = [], []
                intent = dict(self.intent, **{field: value})
                self.assert_refused(lambda: self.writer().send(intent))
                self.assertEqual([], self.commands)
                self.assertEqual([], self.reads)
        self.origin = "git@github.com:synthetic/foreign.git"
        self.assert_refused(lambda: self.writer().send(self.intent))

    def test_stale_pr_conflict_or_wrong_merge_parents_never_push(self):
        pristine = copy.deepcopy(self.data)
        for mutation in ("head", "base", "target", "fork", "draft", "conflict", "parents"):
            with self.subTest(mutation=mutation):
                self.data = copy.deepcopy(pristine)
                self.commands, self.reads = [], []
                self.failure = None
                self.parent_output = BASE + " " + HEAD
                if mutation in ("head", "base"):
                    self.data["pulls/14"][mutation]["sha"] = LATER
                elif mutation == "target":
                    self.data["pulls/14"]["base"]["ref"] = "main"
                elif mutation == "fork":
                    self.data["pulls/14"]["head"]["repo"]["id"] = 999
                elif mutation == "draft":
                    self.data["pulls/14"]["draft"] = True
                elif mutation == "conflict":
                    self.failure = "merge-tree"
                else:
                    self.parent_output = HEAD + " " + BASE
                self.assert_refused(lambda: self.writer().send(self.intent))

    def test_lease_race_failure_has_exactly_one_request_without_retry(self):
        self.failure = "push"
        try:
            self.writer().send(self.intent)
        except RuntimeError:
            pass
        self.assertEqual(1, sum(args[0] == "push" for args in self.commands))

    def test_independent_merged_readback_exact_parents_and_ancestry(self):
        self.data["pulls/14"].update(merged=True, state="closed", merge_commit_sha=MERGE)
        for advanced in (False, True):
            with self.subTest(advanced=advanced):
                self.reads = []
                self.data["git/ref/heads/" + TARGET]["object"]["sha"] = LATER if advanced else MERGE
                # This is fresh GitHub proof, independent of any send response.
                proof = self.writer().readback(self.intent)
                self.assertIsInstance(proof, dict)
                self.assertEqual(dict(self.intent, merged=True, merge_sha=MERGE,
                                      target_contains_merge=True), proof)
                self.assertIn("commits/" + MERGE, self.reads)
                self.assertIn("git/ref/heads/" + TARGET, self.reads)
                if advanced:
                    self.assertIn("compare/" + MERGE + "..." + LATER, self.reads)
        self.assertEqual([], self.commands)

    def test_forged_unmerged_wrong_parent_or_uncontained_readback_is_not_proof(self):
        self.data["pulls/14"].update(merged=True, state="closed", merge_commit_sha=MERGE)
        pristine = copy.deepcopy(self.data)
        for mutation in ("unmerged", "head", "base", "target", "parents", "ancestry"):
            with self.subTest(mutation=mutation):
                self.data = copy.deepcopy(pristine)
                if mutation == "unmerged":
                    self.data["pulls/14"]["merged"] = False
                elif mutation in ("head", "base"):
                    self.data["pulls/14"][mutation]["sha"] = LATER
                elif mutation == "target":
                    self.data["pulls/14"]["base"]["ref"] = "main"
                elif mutation == "parents":
                    self.data["commits/" + MERGE]["parents"].reverse()
                else:
                    self.data["git/ref/heads/" + TARGET]["object"]["sha"] = LATER
                    self.data["compare/" + MERGE + "..." + LATER]["merge_base_commit"]["sha"] = BASE
                self.assertIsNone(self.writer().readback(self.intent))
        self.assertEqual([], self.commands)


if __name__ == "__main__":
    unittest.main()
