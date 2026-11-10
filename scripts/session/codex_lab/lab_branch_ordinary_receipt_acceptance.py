"""#45 actual NativeGithub ordinary HISTORICAL receipt profile, captured #49 bytes.

No current open-PR/rerun permission is claimed. Actual run associations are empty
and failed A1 proof is incomplete; both limits are retained, never fabricated.
"""
import copy
from email.message import Message
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import unittest
from unittest.mock import patch
import zipfile

HERE = Path(__file__).resolve().parent
API = "https://api.github.com/repos/YeliangDu/forcitis-one-psa-codex-lab"
INDEX_SHA = "5aa17ed20cd38f90938ffae15beb054663dcb49ad9777f0db3c32d973eecbafd"


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


class RawResponse:
    def __init__(self, raw, url, media):
        self.raw, self.url, self.cursor = raw, url, 0
        self.status = 200
        self.headers = Message()
        self.headers["Content-Type"] = media

    def read(self, limit=-1):
        stop = len(self.raw) if limit < 0 else self.cursor + limit
        raw = self.raw[self.cursor:stop]
        self.cursor += len(raw)
        return raw

    def geturl(self):
        return self.url

    def close(self):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass


class RecordedGetOnly:
    def __init__(self, directory):
        self.directory, self.calls, self.writes = directory, [], []
        self.unavailable = set()
        self.wrong_zip = False
        self.routes = {}
        run = "42512786544"
        # Resource projections use unchanged actual bytes. The top-run alias is
        # explicitly historical/canonical, not a fresh GET observation.
        self.routes["actions/runs/" + run] = ("2-attempt.json", "application/json")
        for attempt in (1, 2):
            self.routes[f"actions/runs/{run}/attempts/{attempt}"] = (f"{attempt}-attempt.json", "application/json")
            self.routes[f"actions/runs/{run}/attempts/{attempt}/jobs?per_page=100&page=1"] = (f"{attempt}-jobs.json", "application/json")
        for name, identity in (("receipt", 13631459192), ("preflight", 13631730354)):
            self.routes[f"actions/artifacts/{identity}"] = (f"2-{name}-artifact.json", "application/json")
            self.routes[f"actions/artifacts/{identity}/zip"] = (f"2-{name}.zip", "application/zip")
        for filename, job in (("2-preflight-job.log", 128836938207), ("2-verification-job.log", 128842867752),
                              ("1-preflight-job.log", 128829353927), ("1-verification-job.log", 128835788948)):
            self.routes[f"actions/jobs/{job}/logs"] = (filename, "text/plain")

    def open(self, request, timeout):
        method, url = request.get_method(), request.full_url
        self.calls.append((method, url))
        if method != "GET":
            self.writes.append((method, url))
            raise AssertionError("RECORDED_NATIVE_SLICE_MUST_NOT_POST")
        if not url.startswith(API + "/"):
            raise AssertionError("OFFLINE_NATIVE_ROUTE_OUTSIDE_FIXED_LAB")
        route = url[len(API) + 1:]
        if route not in self.routes or route in self.unavailable:
            # Missing actual bytes are never replaced with a made-up response.
            raise OSError("recorded native resource unavailable: " + route)
        name, media = self.routes[route]
        if self.wrong_zip and route == "actions/artifacts/13631459192/zip":
            name = "1-receipt-before-rerun.zip"  # Declared transport misdelivery of real bytes.
        return RawResponse((self.directory / name).read_bytes(), url, media)


class OrdinaryRecordedReceiptAcceptanceTest(unittest.TestCase):
    def setUp(self):
        value = os.environ.get("LAB45_NATIVE49_DIR")
        self.assertTrue(value, "NATIVE49_CAPABILITY_MISSING: supply sealed raw evidence directory; not behavior RED")
        self.directory = Path(value).resolve(strict=True)
        raw = (self.directory / "index.json").read_bytes()
        self.assertEqual(INDEX_SHA, digest(raw), "NATIVE49_INDEX_PIN_CHANGED")
        self.index = json.loads(raw)
        for name, expected in self.index["hashes"].items():
            self.assertEqual(expected, digest((self.directory / name).read_bytes()), name)
        self.run = json.loads((self.directory / "2-attempt.json").read_text())
        self.assertEqual([], self.run["pull_requests"], "do not add a fake current PR association")
        with zipfile.ZipFile(self.directory / "2-receipt.zip") as archive:
            self.receipt = json.loads(archive.read("receipt.json"))
        with zipfile.ZipFile(self.directory / "2-preflight.zip") as archive:
            self.assertEqual((self.directory / "2-plan.json").read_bytes(), archive.read("plan.json"))
        for filename, job_id, step, artifact in (("2-verification-job.log", 128842867752, 8, "2-receipt-artifact.json"),
                                                ("2-preflight-job.log", 128836938207, 17, "2-preflight-artifact.json")):
            jobs = json.loads((self.directory / "2-jobs.json").read_text())["jobs"]
            self.assertEqual(8, len(jobs))
            job = next(j for j in jobs if j["id"] == job_id)
            self.assertEqual((42512786544, 2), (job["run_id"], job["run_attempt"]))
            self.assertEqual("success", next(s for s in job["steps"] if s["number"] == step)["conclusion"])
            meta = json.loads((self.directory / artifact).read_text())
            log = (self.directory / filename).read_text()
            self.assertIn("SHA256 digest of uploaded artifact zip is " + meta["digest"].removeprefix("sha256:"), log)
            self.assertIn("Artifact " + meta["name"] + ".zip successfully finalized. Artifact ID " + str(meta["id"]), log)
        workflow = (self.directory / "exact-workflow.yml").read_bytes()
        self.assertEqual(self.index["workflow_blob"], hashlib.sha1(b"blob " + str(len(workflow)).encode() + b"\0" + workflow).hexdigest())
        self.binding = dict(repository=self.index["repository"], repository_id=1449415666, card=47,
                            role="one-platform", worktree="/unused-historical-component", common="/unused-historical-component/.git",
                            branch=self.index["branch"], head=self.index["head"], base=self.index["base"], target="main", pr_number=49)
        spec = importlib.util.spec_from_file_location("lab45_actual_ordinary_native", HERE / "lab_branch_native.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        contract = dict(allowed_actions=["read-ci"], ci_workflow=self.index["workflow_path"],
                        ordinary_receipt_pins=dict(version=1, scope="historical-readonly-no-current-PR-authority",
                            number=49, run_id=42512786544, attempt=2, receipt_artifact_id=13631459192,
                            preflight_artifact_id=13631730354, verification_job_id=128842867752,
                            verification_upload_step=8, preflight_job_id=128836938207, preflight_upload_step=17,
                            workflow_blob=self.index["workflow_blob"], workflow_source=workflow.decode(),
                            workflow_sha256=digest(workflow), plan_sha256=digest((self.directory / "2-plan.json").read_bytes())))
        self.native = module.NativeGithub({}, contract, False)
        self.http = RecordedGetOnly(self.directory)
        self.native.opener = self.http
        clean = patch.dict(os.environ, {"GH_TOKEN": "", "GITHUB_TOKEN": ""})
        clean.start()
        self.addCleanup(clean.stop)

    def read(self, binding=None, run=None):
        try:
            value = self.native.event_receipt(binding or self.binding, run or self.run)
            return value, None
        except Exception as error:
            return None, error
        finally:
            self.assertEqual([], self.http.writes)

    def test_actual_ordinary_profile_verifies_unchanged_recorded_success_identity(self):
        value, error = self.read()
        self.assertIsNone(error, "ORDINARY_LAB_PR_RECEIPT_PROFILE_UNBOUND: " + repr(error))
        report, proof = value
        self.assertEqual(self.index["head"], report.get("head_sha", report.get("head")))
        self.assertEqual(self.index["base"], report.get("base_sha", report.get("base")))
        self.assertEqual(42512786544, int(report.get("run_id", report.get("run"))))
        self.assertEqual(2, int(report.get("run_attempt", report.get("attempt"))))
        self.assertIs(True, report["ready"])
        self.assertNotIn(report.get("action"), ("eligible", "verified-ci", "admitted", "approved"))
        self.assertIsNot(True, report.get("release_authority"))
        self.assertIsNot(True, report.get("rerun_authority"))
        self.assertIsNot(True, proof.get("release_authority"))
        self.assertIsNot(True, proof.get("rerun_authority"))
        self.assertEqual(13631459192, proof["artifact_id"])
        self.assertEqual("a8d4ffdee72cecf7853c397a95cf1437582e00bc862692b484d30317c67e6bf6", proof["sha256"])
        self.assertEqual((128842867752, 8), (proof["job_id"], proof["upload_step"]))

    def test_failed_a1_missing_original_plan_and_current_artifact_is_not_success_or_permission(self):
        failed = json.loads((self.directory / "1-attempt.json").read_text())
        self.assertEqual("failure", failed["conclusion"])
        self.assertFalse((self.directory / "1-preflight.zip").exists())
        # Pin the failed subject itself, while retaining the genuinely missing
        # artifact/preflight proof. Never borrow A2's plan or upload metadata.
        self.native.contract["ordinary_receipt_pins"].update(attempt=1, receipt_artifact_id=13631131650,
            preflight_artifact_id=None, verification_job_id=128835788948, preflight_job_id=128829353927)
        with zipfile.ZipFile(self.directory / "1-receipt-before-rerun.zip") as archive:
            self.assertIs(False, json.loads(archive.read("receipt.json"))["ready"])
        value, error = self.read(run=failed)
        self.assertIsNone(value)
        self.assertIsNotNone(error)
        self.assertEqual([], self.http.writes)

    def test_actual_records_do_not_match_wrong_binding_head_base_branch_or_pr(self):
        for kind in ("head", "base", "branch", "pr_number"):
            with self.subTest(kind=kind):
                binding = copy.deepcopy(self.binding)
                binding[kind] = "e" * 40 if kind in ("head", "base") else "codex/foreign" if kind == "branch" else 50
                value, error = self.read(binding=binding)
                self.assertIsNone(value)
                self.assertIsNotNone(error)

    def test_old_real_a1_zip_cannot_be_borrowed_for_a2(self):
        self.http.wrong_zip = True
        value, error = self.read()
        self.assertIsNone(value)
        self.assertIsNotNone(error)

    def test_unreadable_same_attempt_plan_zip_cannot_be_replaced_by_another_attempt(self):
        self.http.unavailable.add("actions/artifacts/13631730354/zip")
        value, error = self.read()
        self.assertIsNone(value)
        self.assertIsNotNone(error)


    def test_merged_historical_run_without_current_pr_association_is_not_current_ci(self):
        self.assertEqual([], self.run["pull_requests"])
        with self.assertRaises(ValueError):
            self.native.current_runs(self.binding, [copy.deepcopy(self.run)])
        self.assertEqual([], self.http.writes)


if __name__ == "__main__":
    unittest.main(verbosity=2)
