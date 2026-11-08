"""Controlled-main writer acceptance using fake Git, GET and authority only."""
import copy
import importlib.util
from pathlib import Path
import types
import unittest

BASE, HEAD, TREE, MERGE, OTHER = (c * 40 for c in "abcde")
SLUG = "YeliangDu/forcitis-one-psa-codex-lab"
REF = "refs/heads/main"


def module():
    spec = importlib.util.spec_from_file_location("main_writer_acceptance_target",
                                                Path(__file__).with_name("lab_queue_git_writer.py"))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def main_writer(git, read, authorize):
    implementation = module()
    if not hasattr(implementation, "GitLabMainWriter"):
        return types.SimpleNamespace(send=lambda intent: None, readback=lambda intent: None)
    return implementation.GitLabMainWriter(git, read, authorize)


class MainWriterAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.intent = dict(number=14, head=HEAD, base=BASE, target="main")
        self.events = []
        self.authorized = True
        self.fail_git = None
        self.parents = BASE + " " + HEAD
        self.stale_at_boundary = None
        self.origin = f"git@github.com:{SLUG}.git"
        repo = dict(id=123, full_name=SLUG, private=True)
        self.payload = {
            "": repo,
            "pulls/14": dict(number=14, state="open", merged=False, draft=False,
                             head=dict(sha=HEAD, ref="reviewed-topic", repo=copy.deepcopy(repo)),
                             base=dict(sha=BASE, ref="main", repo=copy.deepcopy(repo))),
            "commits/" + MERGE: dict(sha=MERGE, parents=[dict(sha=BASE), dict(sha=HEAD)]),
            "git/ref/heads/main": dict(ref=REF, object=dict(sha=MERGE, type="commit")),
            "compare/" + MERGE + "..." + OTHER: dict(status="ahead", merge_base_commit=dict(sha=MERGE)),
        }

    def authorize(self, intent):
        self.events.append(("authorize",))
        self.assertEqual(self.intent, intent)
        if self.authorized == "raise": raise RuntimeError("synthetic denied authority")
        return self.authorized

    def git(self, args):
        self.events.append(("git", list(args)))
        if self.fail_git == args[0]: raise RuntimeError("synthetic Git rejection")
        if args == ["remote", "-v"]:
            return f"origin\t{self.origin} (fetch)\norigin\t{self.origin} (push)\n"
        if args == ["merge-tree", "--write-tree", BASE, HEAD]: return TREE
        if args[0] == "commit-tree":
            self.assertEqual(["commit-tree", TREE, "-p", BASE, "-p", HEAD, "-m"], args[:7])
            self.assertEqual(8, len(args))
            self.assertTrue(args[7])
            return MERGE
        if args == ["show", "-s", "--format=%P", MERGE]: return self.parents
        if args == ["merge-base", "--is-ancestor", BASE, MERGE]: return ""
        if args[0] == "push":
            self.assertEqual(["push", "--porcelain", f"--force-with-lease={REF}:{BASE}",
                              "origin", f"{MERGE}:{REF}"], args)
            return "synthetic success, not confirmation"
        self.fail("unexpected Git argv: " + repr(args))

    def read(self, path):
        self.events.append(("read", path))
        self.assertIn(path, self.payload)
        result = copy.deepcopy(self.payload[path])
        if path == "pulls/14" and self.events.count(("read", path)) > 1 and self.stale_at_boundary:
            result[self.stale_at_boundary]["sha"] = OTHER
        return result

    def pushes(self):
        return [e[1] for e in self.events if e[0] == "git" and e[1][0] == "push"]

    def refused(self, operation):
        try: operation()
        except (ValueError, RuntimeError): pass
        self.assertEqual([], self.pushes())

    def test_authorized_exact_lease_ordered_parents_and_ff(self):
        self.assertTrue(hasattr(module(), "GitLabMainWriter"),
                        "authorized release requires the controlled main writer implementation")
        main_writer(self.git, self.read, self.authorize).send(copy.deepcopy(self.intent))
        self.assertEqual(("authorize",), self.events[0])
        self.assertEqual([["push", "--porcelain", f"--force-with-lease={REF}:{BASE}",
                           "origin", f"{MERGE}:{REF}"]], self.pushes())
        self.assertIn(("git", ["merge-base", "--is-ancestor", BASE, MERGE]), self.events)
        self.assertGreaterEqual(self.events.count(("read", "pulls/14")), 2)
        self.assertGreaterEqual(self.events.count(("read", "")), 2)
        self.assertGreaterEqual(self.events.count(("git", ["remote", "-v"])), 2)

    def test_denied_missing_nonliteral_or_throwing_authority_cannot_write(self):
        for allowed in (False, None, 1, "true", "raise"):
            with self.subTest(allowed=allowed):
                self.events = []
                self.authorized = allowed
                self.refused(lambda: main_writer(self.git, self.read, self.authorize).send(self.intent))
                self.assertFalse(any(e[0] == "git" and e[1][0] in ("merge-tree", "commit-tree") for e in self.events))
        self.events = []
        self.refused(lambda: main_writer(self.git, self.read, None).send(self.intent))

    def test_invalid_intent_rejected_before_any_port_io_and_disposable_still_denies_main(self):
        for fields in (dict(target="lab/disposable-other"), dict(target="refs/heads/main"),
                       dict(number=True), dict(head="BAD"), dict(base=None), dict(extra="unapproved")):
            with self.subTest(fields=fields):
                self.events = []
                self.refused(lambda: main_writer(self.git, self.read, self.authorize).send(dict(self.intent, **fields)))
                self.assertEqual([], self.events)
        self.events = []
        self.refused(lambda: module().GitDisposableWriter(self.git, self.read).send(self.intent))
        self.assertEqual([], self.events)

    def test_pr_identity_and_send_boundary_movement_rejected(self):
        pristine = copy.deepcopy(self.payload)
        for mutation in ("head", "base", "draft", "foreign", "boundary-head", "boundary-base", "origin"):
            with self.subTest(mutation=mutation):
                self.payload = copy.deepcopy(pristine)
                self.events = []
                self.stale_at_boundary = None
                self.origin = f"git@github.com:{SLUG}.git"
                if mutation in ("head", "base"): self.payload["pulls/14"][mutation]["sha"] = OTHER
                elif mutation == "draft": self.payload["pulls/14"]["draft"] = True
                elif mutation == "foreign": self.payload["pulls/14"]["head"]["repo"]["id"] = 999
                elif mutation.startswith("boundary-"): self.stale_at_boundary = mutation.split("-")[1]
                else: self.origin = "git@github.com:synthetic/foreign.git"
                self.refused(lambda: main_writer(self.git, self.read, self.authorize).send(self.intent))

    def test_conflict_parents_ff_and_stale_lease_fail_without_retry(self):
        for failure in ("merge-tree", "parents", "merge-base", "push"):
            with self.subTest(failure=failure):
                self.events = []
                self.parents = HEAD + " " + BASE if failure == "parents" else BASE + " " + HEAD
                self.fail_git = None if failure == "parents" else failure
                try: main_writer(self.git, self.read, self.authorize).send(self.intent)
                except (ValueError, RuntimeError): pass
                self.assertEqual(1 if failure == "push" else 0, len(self.pushes()))

    def test_readonly_recovery_needs_exact_proof_but_no_write_authority(self):
        self.payload["pulls/14"].update(merged=True, state="closed", merge_commit_sha=MERGE)
        pristine = copy.deepcopy(self.payload)
        for mutation in ("exact", "advanced", "head", "base", "parents", "unmerged", "not-contained"):
            with self.subTest(mutation=mutation):
                self.payload = copy.deepcopy(pristine)
                self.events = []
                if mutation in ("head", "base"): self.payload["pulls/14"][mutation]["sha"] = OTHER
                elif mutation == "parents": self.payload["commits/" + MERGE]["parents"].reverse()
                elif mutation == "unmerged": self.payload["pulls/14"]["merged"] = False
                elif mutation in ("advanced", "not-contained"):
                    self.payload["git/ref/heads/main"]["object"]["sha"] = OTHER
                    if mutation == "not-contained":
                        self.payload["compare/" + MERGE + "..." + OTHER]["merge_base_commit"]["sha"] = BASE
                proof = main_writer(self.git, self.read, None).readback(self.intent)
                if mutation in ("exact", "advanced"):
                    self.assertEqual(dict(self.intent, merged=True, merge_sha=MERGE, target_contains_merge=True), proof)
                else: self.assertIsNone(proof)
                self.assertTrue(all(e[0] == "read" for e in self.events))


if __name__ == "__main__":
    unittest.main()
