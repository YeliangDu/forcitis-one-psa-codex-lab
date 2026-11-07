"""Loaded source and one-time baseline trust: fake read-only ports, hosted only."""
import copy
import importlib.util
from pathlib import Path
import types
import unittest

INITIAL = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
HEAD = "b" * 40
CONTROL = "f18fdd7265dc7450818a2303ceb630c0892deb57"
RUN = 42418209077
SLUG = "YeliangDu/forcitis-one-psa-codex-lab"
PATHS = (
    ".github/workflows/lab-pr-ci.yml", "scripts/session/codex_lab/lab_pr_ci.py",
    "scripts/session/codex_lab/install_gitleaks.sh", "scripts/session/codex_lab/install_hosted_shellcheck.sh",
    "scripts/ci/light.sh", "scripts/ci/light_triggers.sh", "scripts/ci/backend_verify.sh",
    "scripts/codegen/generate_api.sh", "policy.json", ".gitleaks.toml", ".gitleaksignore",
)


def target():
    source = Path(__file__).with_name("mq_lab_trust.py")
    if not source.exists():
        return types.SimpleNamespace(trusted_source=lambda *a: False, bootstrap_check=lambda *a: False)
    spec = importlib.util.spec_from_file_location("mq_trust_acceptance", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class BootstrapGithub:
    def __init__(self, case):
        self.case = case
        self.calls = []
        self.fail = None
        self.final_main = INITIAL
        self.repo = dict(id=123, full_name=SLUG, private=True)
        self.run = dict(id=RUN, run_attempt=1, repository=copy.deepcopy(self.repo),
                        path=".github/workflows/lab-bootstrap-baseline.yml", event="push",
                        head_branch="lab/14-bootstrap-baseline-ci", head_sha=CONTROL,
                        status="completed", conclusion="success")
        self.job_rows = [dict(name="baseline", runner_group_name="GitHub Actions",
                              labels=["ubuntu-24.04"], head_sha=CONTROL, conclusion="success")]
        self.artifact_rows = [dict(name="lab-bootstrap-baseline", expired=False, baseline=dict(
            kind="bootstrap-baseline", head_sha=INITIAL, repository=SLUG,
            run_id=RUN, run_attempt=1, light="success", secret="success"))]

    def record(self, name, *args):
        self.calls.append((name, *args))
        if self.fail == name: raise OSError("synthetic unavailable proof")

    def repository(self):
        self.record("repository")
        return copy.deepcopy(self.repo)

    def main_ref(self):
        self.record("main_ref")
        sha = INITIAL if self.calls.count(("main_ref",)) == 1 else self.final_main
        return dict(ref="refs/heads/main", object=dict(sha=sha))

    def bootstrap_run(self, run_id):
        self.record("bootstrap_run", run_id)
        self.case.assertEqual(RUN, run_id)
        return copy.deepcopy(self.run)

    def jobs(self, run_id, attempt):
        self.record("jobs", run_id, attempt)
        self.case.assertEqual((RUN, 1), (run_id, attempt))
        return copy.deepcopy(self.job_rows)

    def bootstrap_artifacts(self, run_id, attempt):
        self.record("bootstrap_artifacts", run_id, attempt)
        self.case.assertEqual((RUN, 1), (run_id, attempt))
        return copy.deepcopy(self.artifact_rows)


class SourceTrustAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.intent = dict(number=15, head=HEAD, base=INITIAL, target="main")
        self.approval = dict(self.intent, mode="bootstrap", baseline_run_id=RUN, repo_id=123)
        self.calls = []
        self.local_head = INITIAL
        self.dirty = ""
        self.changed = self.missing = None
        self.github = BootstrapGithub(self)

    def git(self, args):
        self.calls.append(args)
        if args == ["status", "--porcelain", "--untracked-files=all"]: return self.dirty
        if args == ["rev-parse", "HEAD"]: return self.local_head + "\n"
        self.assertEqual("rev-parse", args[0])
        self.assertEqual(2, len(args))
        sha, path = args[1].split(":", 1)
        self.assertIn(sha, (INITIAL, HEAD))
        self.assertIn(path, PATHS)
        if path == self.missing: raise RuntimeError("synthetic missing blob")
        return (("d" if sha == HEAD and path == self.changed else "a") * 40) + "\n"

    def test_normal_clean_loaded_base_and_all_control_blobs_unchanged(self):
        self.assertIs(target().trusted_source(self.intent, "normal", self.git), True)
        for sha in (INITIAL, HEAD):
            for path in PATHS: self.assertIn(["rev-parse", f"{sha}:{path}"], self.calls)
        self.assertIn(["status", "--porcelain", "--untracked-files=all"], self.calls)

    def test_dirty_wrong_loaded_head_changed_or_missing_control_fails(self):
        for mutation in ("tracked", "untracked", "local-head", *PATHS):
            with self.subTest(mutation=mutation):
                self.dirty, self.local_head, self.changed = "", INITIAL, None
                if mutation == "tracked": self.dirty = " M policy.json\n"
                elif mutation == "untracked": self.dirty = "?? synthetic.py\n"
                elif mutation == "local-head": self.local_head = HEAD
                else: self.changed = mutation
                self.assertIsNot(target().trusted_source(self.intent, "normal", self.git), True)
        self.dirty, self.local_head, self.changed = "", INITIAL, None
        for path in PATHS:
            with self.subTest(missing=path):
                self.missing = path
                self.assertIsNot(target().trusted_source(self.intent, "normal", self.git), True)

    def test_bootstrap_loaded_reviewed_head_only_for_fixed_pr_and_base(self):
        self.local_head = HEAD
        self.assertIs(target().trusted_source(self.intent, "bootstrap", self.git), True)
        for fields, mode in ((dict(number=16), "bootstrap"), (dict(base="c" * 40), "bootstrap"),
                             (dict(target="topic"), "bootstrap"), ({}, "skip")):
            with self.subTest(fields=fields, mode=mode):
                self.assertIsNot(target().trusted_source(dict(self.intent, **fields), mode, self.git), True)
        self.local_head = INITIAL
        self.assertIsNot(target().trusted_source(self.intent, "bootstrap", self.git), True)

    def test_exact_real_shaped_baseline_and_decimal_ids_recheck_main(self):
        for strings in (False, True):
            with self.subTest(strings=strings):
                self.github = BootstrapGithub(self)
                if strings:
                    self.github.artifact_rows[0]["baseline"].update(run_id=str(RUN), run_attempt="1")
                self.assertIs(target().bootstrap_check(self.intent, self.approval, self.github), True)
                self.assertEqual(2, self.github.calls.count(("main_ref",)))
                self.assertIn(("bootstrap_run", RUN), self.github.calls)

    def test_bad_baseline_job_artifact_or_identity_cannot_bypass(self):
        for mutation in ("run-id", "attempt", "workflow", "event", "branch", "head", "red",
                         "public", "repo-id", "job", "runner", "duplicate-job", "artifact", "expired",
                         "payload-head", "secret", "wrong-pr", "approval-run"):
            with self.subTest(mutation=mutation):
                self.github = BootstrapGithub(self)
                approval, intent = copy.deepcopy(self.approval), dict(self.intent)
                run, payload = self.github.run, self.github.artifact_rows[0]["baseline"]
                if mutation == "run-id": run["id"] = RUN + 1
                elif mutation == "attempt": run["run_attempt"] = 2
                elif mutation == "workflow": run["path"] = ".github/workflows/other.yml"
                elif mutation == "event": run["event"] = "pull_request"
                elif mutation == "branch": run["head_branch"] = "main"
                elif mutation == "head": run["head_sha"] = HEAD
                elif mutation == "red": run["conclusion"] = "failure"
                elif mutation == "public": self.github.repo["private"] = False
                elif mutation == "repo-id": approval["repo_id"] = 999
                elif mutation == "job": self.github.job_rows[0]["conclusion"] = "failure"
                elif mutation == "runner": self.github.job_rows[0]["runner_group_name"] = "self-hosted"
                elif mutation == "duplicate-job": self.github.job_rows *= 2
                elif mutation == "artifact": self.github.artifact_rows *= 2
                elif mutation == "expired": self.github.artifact_rows[0]["expired"] = True
                elif mutation == "payload-head": payload["head_sha"] = HEAD
                elif mutation == "secret": payload["secret"] = "failure"
                elif mutation == "wrong-pr": intent["number"] = approval["number"] = 16
                else: approval["baseline_run_id"] = RUN + 1
                self.assertIsNot(target().bootstrap_check(intent, approval, self.github), True)

    def test_moved_main_or_transport_error_fails_closed(self):
        self.github.final_main = HEAD
        self.assertIsNot(target().bootstrap_check(self.intent, self.approval, self.github), True)
        for failure in ("repository", "main_ref", "bootstrap_run", "jobs", "bootstrap_artifacts"):
            with self.subTest(failure=failure):
                self.github = BootstrapGithub(self)
                self.github.fail = failure
                self.assertIsNot(target().bootstrap_check(self.intent, self.approval, self.github), True)


if __name__ == "__main__":
    unittest.main()
