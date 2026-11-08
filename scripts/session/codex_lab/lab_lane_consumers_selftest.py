"""Lab #51: actual planner CLI + same-repo events + isolated real Git diffs.

Run only on the lead's independent hosted runner. A plan is not proof that the
selected Java/Node consumers executed; retain their separate hosted receipts.
"""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[3]
PLANNER = ROOT / "scripts/session/codex_lab/lab_pr_ci.py"
REPO = "YeliangDu/forcitis-one-psa-codex-lab"
REPO_ID = 1449415666
LANES = ("light", "secret", "shadow", "frontend", "backend")
EVENT_PATH = "docs/architecture/events/asyncapi.yaml"
PROPOSED_PATH = "backend/glossary/proposed/frontend-ui.yaml"
DOC_PATH = "docs/lab-51-synthetic-note.md"
UNKNOWN_PATH = "unclassified/lab-51-synthetic-executable.py"
LEGACY_PATHS = (
    "docs/data/legacy-views/seeyon-views.sql",
    "backend/src/main/java/com/forcitis/one/platform/integration/Lab51Synthetic.java",
)


class HostedLaneConsumersAcceptanceTest(unittest.TestCase):
    def invoke(self, paths, *, alter=None, context=None, empty_diff=False):
        # No target module imports/mocks, no hand-supplied planner paths, no
        # cloned/shared repository: only this new temporary repository mutates.
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith("GIT_")}
        environment.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1",
                           PYTHONDONTWRITEBYTECODE="1")
        with tempfile.TemporaryDirectory(prefix="lab51-cli-") as directory:
            temporary = Path(directory)
            repository = temporary / "isolated-git"
            repository.mkdir()

            def git(*args):
                return subprocess.check_output(
                    ["git", "-c", "user.name=Lab51 Synthetic",
                     "-c", "user.email=lab51@example.invalid",
                     "-c", "core.hooksPath=" + os.devnull, *args],
                    cwd=repository, env=environment, text=True,
                    stderr=subprocess.PIPE, timeout=20).strip()

            git("init", "--initial-branch=main", ".")
            for path in paths:
                target = repository / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("synthetic baseline\n", encoding="utf-8")
            git("add", "--all")
            git("commit", "--allow-empty", "-m", "synthetic base")
            base = git("rev-parse", "HEAD")
            if not empty_diff:
                for path in paths:
                    (repository / path).write_text("synthetic changed input\n", encoding="utf-8")
            git("add", "--all")
            git("commit", "--allow-empty", "-m", "synthetic head")
            head = git("rev-parse", "HEAD")
            actual_paths = git("diff", "--name-only", "--no-renames", base, head, "--").splitlines()
            self.assertEqual([] if empty_diff else sorted(paths), actual_paths)
            repo = dict(full_name=REPO, id=REPO_ID, private=False,
                        visibility="public", fork=False)
            event = dict(action="synchronize", number=51, repository=copy.deepcopy(repo), pull_request=dict(
                number=51, head=dict(repo=copy.deepcopy(repo), sha=head),
                base=dict(repo=copy.deepcopy(repo), ref="main", sha=base)))
            if alter is not None:
                alter(event, base, head)
            event_file = temporary / "event.json"
            event_file.write_text(json.dumps(event), encoding="utf-8")
            receipt_file = temporary / "plan.json"
            output_file = temporary / "output"
            child_env = dict(environment, GITHUB_EVENT_NAME="pull_request",
                             GITHUB_REPOSITORY=REPO, GITHUB_REPOSITORY_ID=str(REPO_ID),
                             GITHUB_EVENT_PATH=str(event_file), GITHUB_OUTPUT=str(output_file))
            for key, value in (context or {}).items():
                if value is None:
                    child_env.pop(key, None)
                else:
                    child_env[key] = value
            started = time.monotonic()
            process = subprocess.run(
                [sys.executable, "-B", str(PLANNER), "--event", str(event_file),
                 "--receipt", str(receipt_file), "--output", str(output_file)],
                cwd=repository, env=child_env, capture_output=True, text=True,
                timeout=120, check=False)
            record = dict(test=self.id(), head=head, base=base, paths=actual_paths,
                          elapsed_seconds=round(time.monotonic() - started, 6),
                          returncode=process.returncode, stdout=process.stdout,
                          stderr=process.stderr,
                          receipt=json.loads(receipt_file.read_text()) if receipt_file.exists() else None,
                          output=output_file.read_text() if output_file.exists() else "",
                          event=event,
                          context={key: child_env.get(key) for key in (
                              "GITHUB_EVENT_NAME", "GITHUB_REPOSITORY", "GITHUB_REPOSITORY_ID")})
            print("LAB51_CASE " + json.dumps(record, sort_keys=True), flush=True)
            return record

    def assert_plan(self, record, required, *, legacy=False):
        self.assertEqual(0, record["returncode"], record["stderr"])
        receipt = record["receipt"]
        self.assertIsInstance(receipt, dict)
        self.assertEqual(record["head"], receipt["head_sha"])
        self.assertEqual(record["base"], receipt["base_sha"])
        self.assertEqual(record["paths"], receipt["paths"])
        self.assertIs(legacy, receipt["legacy_it"])
        expected = {lane: "required" if lane in required else "not-applicable" for lane in LANES}
        self.assertEqual(expected, receipt["lanes"], "actual CLI must select each real input consumer")
        outputs = dict(line.split("=", 1) for line in record["output"].splitlines() if "=" in line)
        for lane, value in expected.items():
            self.assertEqual(value, outputs[lane], "workflow output must agree with persisted plan")
        self.assertEqual(record["head"], outputs["head_sha"])
        self.assertEqual(record["base"], outputs["base_sha"])
        self.assertEqual(str(legacy).lower(), outputs["legacy_it"])
        printed = json.loads(record["stdout"])
        self.assertEqual(receipt["lanes"], printed["lanes"])

    def assert_rejected(self, record):
        self.assertNotEqual(0, record["returncode"], "unmatched identity/context cannot yield an accepted plan")
        self.assertIsNone(record["receipt"], "rejection must not leave an adoptable plan")
        self.assertEqual("", record["output"], "rejected input must not publish workflow outputs")

    def test_event_contract_only_schedules_event_catalogue_consumer(self):
        self.assert_plan(self.invoke([EVENT_PATH]), {"light", "secret", "backend"})

    def test_proposed_glossary_only_schedules_copy_consumer_and_keeps_backend(self):
        self.assert_plan(self.invoke([PROPOSED_PATH]), {"light", "secret", "frontend", "backend"})

    def test_ordinary_nonconsumer_document_keeps_only_mandatory_lanes(self):
        self.assert_plan(self.invoke([DOC_PATH]), {"light", "secret"})

    def test_unknown_executable_path_conservatively_requires_every_lane(self):
        self.assert_plan(self.invoke([UNKNOWN_PATH]), set(LANES))

    def test_sql_server_inputs_keep_legacy_backend_route(self):
        for path in LEGACY_PATHS:
            with self.subTest(path=path):
                self.assert_plan(self.invoke([path]), {"light", "secret", "backend"}, legacy=True)

    def test_wrong_or_malformed_event_head_is_rejected(self):
        for value in ("base", "malformed"):
            with self.subTest(value=value):
                def alter(event, base, head):
                    event["pull_request"]["head"]["sha"] = base if value == "base" else "not-a-sha"
                self.assert_rejected(self.invoke([EVENT_PATH], alter=alter))

    def test_wrong_base_ref_malformed_missing_or_unavailable_base_is_rejected(self):
        for kind in ("ref", "malformed", "missing", "unavailable"):
            with self.subTest(kind=kind):
                def alter(event, base, head):
                    target = event["pull_request"]["base"]
                    if kind == "ref":
                        target["ref"] = "topic"
                    elif kind == "malformed":
                        target["sha"] = "not-a-sha"
                    elif kind == "missing":
                        del target["sha"]
                    else:
                        target["sha"] = "f" * 40  # Not an object in this isolated Git repository.
                self.assert_rejected(self.invoke([EVENT_PATH], alter=alter))

    def test_all_three_webhook_repository_identities_are_enforced(self):
        for position in ("repository", "head", "base"):
            for key, wrong in (("full_name", "synthetic/forbidden"), ("id", REPO_ID + 1), ("private", True)):
                with self.subTest(position=position, key=key):
                    def alter(event, base, head):
                        target = event["repository"] if position == "repository" else event["pull_request"][position]["repo"]
                        target[key] = wrong
                    self.assert_rejected(self.invoke([PROPOSED_PATH], alter=alter))

    def test_unmatched_workflow_events_or_repository_context_cannot_publish_plan(self):
        cases = [dict(GITHUB_EVENT_NAME="push"), dict(GITHUB_EVENT_NAME="workflow_dispatch"),
                 dict(GITHUB_EVENT_NAME=None), dict(GITHUB_REPOSITORY="synthetic/forbidden"),
                 dict(GITHUB_REPOSITORY_ID=str(REPO_ID + 1)), dict(GITHUB_REPOSITORY_ID=None)]
        for context in cases:
            with self.subTest(context=context):
                self.assert_rejected(self.invoke([EVENT_PATH], context=context))

    def test_incomplete_pull_request_identity_cannot_publish_plan(self):
        for part in ("pull_request", "head-repo"):
            with self.subTest(part=part):
                def alter(event, base, head):
                    if part == "pull_request":
                        del event["pull_request"]
                    else:
                        del event["pull_request"]["head"]["repo"]
                self.assert_rejected(self.invoke([EVENT_PATH], alter=alter))

    def test_empty_real_commit_diff_cannot_supply_required_path_proof(self):
        self.assert_rejected(self.invoke([DOC_PATH], empty_diff=True))


if __name__ == "__main__":
    unittest.main()
