"""Hosted-only #54 identity RED; real build bundle and real statement tools.

This initial seam isolates workflow identity using the legacy signature format.
The legacy positive is an isolated test capability, never a Lab release receipt.
No installer, production endpoint, cloud account, Release or tag is accessed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
SOURCE = "396d75e48cba5cefc496fbb572a592e97ea3612a"
LEGACY = ROOT / "scripts/release/statement.sh"
LEGACY_WORKFLOW = "FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main"
LAB_WORKFLOW = "YeliangDu/forcitis-one-psa-codex-lab/.github/workflows/lab-isolated-release.yml@refs/heads/main"
WORKFLOW_REFUSAL = "statement was not produced by the release workflow on main"
FROZEN_TOOLS = {'scripts/release/statement.sh': 'c6dcf97d6c80c0cea056522b936ecd1184b4c97790501e2ed0a840061d76f74b', 'scripts/release/verify_bundle.sh': '86d67eaed0a9870ddac0a55f0b583f10ab8622f0206c530df5b80cad20250c72', 'scripts/release/manifest.py': '7541a5787d056db993899e4b8342526e3e79dc5f8e2f864813fdbae3a4999e4e', 'scripts/release/lib.sh': '78b810f7772b5162374bd1365083144f697de2b7726d30b05f863826358388e0'}
FIXTURE = None


class CapabilityError(RuntimeError):
    """Build/tools/signature preconditions failed; not an accepted behavior RED."""


def digest(path):
    value = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


class Fixture:
    def __init__(self, args, temporary):
        self.args = args
        self.temporary = Path(temporary)
        self.evidence = args.evidence
        self.evidence.mkdir(parents=True, exist_ok=True)
        self.sequence = 0
        self.key = self.temporary / "one-time-test-key"
        self.signers = self.temporary / "allowed-test-signers"
        self.environment = {key: value for key, value in os.environ.items()
                            if not key.startswith(("AWS_", "ALIBABA_", "ALICLOUD_"))
                            and key not in ("SSH_AUTH_SOCK", "GH_TOKEN", "GITHUB_TOKEN")}
        self.environment["PYTHONDONTWRITEBYTECODE"] = "1"

    def call(self, name, command, *, environment=None, stdin=None, timeout=180):
        self.sequence += 1
        completed = subprocess.run(command, env=environment or self.environment,
                                   capture_output=True, text=True, input=stdin,
                                   timeout=timeout, check=False)
        record = dict(name=name, returncode=completed.returncode, command=[str(x) for x in command],
                      stdout=completed.stdout, stderr=completed.stderr)
        (self.evidence / f"{self.sequence:02d}-{name}.json").write_text(json.dumps(record, indent=2) + "\n")
        return completed

    @staticmethod
    def capable(result, label):
        if result.returncode != 0:
            raise CapabilityError(label + ": " + result.stdout + result.stderr)

    def statement(self, name, workflow):
        path = self.temporary / (name + ".release")
        environment = dict(self.environment, GITHUB_WORKFLOW_REF=workflow, GITHUB_RUN_ID="54001")
        self.capable(self.call(name + "-write", ["bash", str(LEGACY), "write", str(self.args.bundle),
                                               str(path), SOURCE], environment=environment), "actual statement write")
        self.capable(self.call(name + "-sign", ["bash", str(LEGACY), "sign", str(path), str(self.key)]),
                     "actual one-time signature")
        shutil.copyfile(path, self.evidence / path.name)
        shutil.copyfile(str(path) + ".sig", self.evidence / (path.name + ".sig"))
        return path

    def verify(self, name, statement, *, verifier=None, bundle=None, version=None, commit=None):
        return self.call(name, ["bash", str(verifier or self.args.verifier), "verify",
                               str(bundle or self.args.bundle), str(statement), str(self.signers),
                               version or self.args.version, commit or SOURCE])

    def preflight(self):
        if os.environ.get("GITHUB_ACTIONS") != "true":
            raise CapabilityError("hosted runner required; do not run locally")
        for path, expected in FROZEN_TOOLS.items():
            if digest(ROOT / path) != expected:
                raise CapabilityError("frozen release tool changed: " + path)
        verifier_pin = self.args.verifier_sha256
        if verifier_pin is None and self.args.verifier == LEGACY.resolve():
            verifier_pin = FROZEN_TOOLS["scripts/release/statement.sh"]
        if verifier_pin is None or digest(self.args.verifier) != verifier_pin:
            raise CapabilityError("selected verifier lacks the independently pinned source digest")
        for tool in ("bash", "ssh-keygen", "java", "javap", "unzip", "tar", "python3"):
            if shutil.which(tool) is None:
                raise CapabilityError("missing tool: " + tool)
        if not self.args.bundle.is_file() or digest(self.args.bundle) != self.args.bundle_sha256:
            raise CapabilityError("bundle is absent or differs from independently pinned build artifact digest")
        if self.args.bundle.name != "forcitis-one-" + self.args.version + ".tar.gz":
            raise CapabilityError("bundle basename/version mismatch")
        self.capable(self.call("real-bundle-validation", ["bash", str(ROOT / "scripts/release/verify_bundle.sh"),
                                                        str(self.args.bundle)], timeout=300), "actual bundle verifier")
        with tarfile.open(self.args.bundle, "r:gz") as archive:
            member = archive.extractfile("forcitis-one-" + self.args.version + "/manifest.json")
            if member is None:
                raise CapabilityError("manifest absent")
            manifest_bytes = member.read()
            manifest = json.loads(manifest_bytes)
        if manifest.get("gitSha") != SOURCE or manifest.get("version") != self.args.version:
            raise CapabilityError("real built bundle does not name frozen source/version")
        self.capable(self.call("ephemeral-key", ["ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", str(self.key)]),
                     "test key generation")
        public = Path(str(self.key) + ".pub").read_text().strip()
        self.signers.write_text("forcitis-one-release " + public + "\n")
        shutil.copyfile(str(self.key) + ".pub", self.evidence / "test-key.pub")
        self.old = self.statement("legacy-capability-only", LEGACY_WORKFLOW)
        self.lab = self.statement("lab-workflow-identity", LAB_WORKFLOW)
        self.capable(self.verify("legacy-capability", self.old, verifier=LEGACY), "isolated legacy-format positive")
        # Prove Lab-workflow bytes have a valid signature before the behavior assertion.
        self.capable(self.call("lab-signature-capability", ["ssh-keygen", "-Y", "verify", "-f", str(self.signers),
                                                           "-I", "forcitis-one-release", "-n", "forcitis-one-release",
                                                           "-s", str(self.lab) + ".sig"], stdin=self.lab.read_text()),
                     "Lab statement cryptographic positive")
        (self.evidence / "capability.json").write_text(json.dumps(dict(
            capability="passed", source=SOURCE, bundle_sha256=digest(self.args.bundle),
            manifest_sha256=hashlib.sha256(manifest_bytes).hexdigest(),
            selected_verifier_sha256=digest(self.args.verifier), legacy_verifier_sha256=digest(LEGACY),
            lab_workflow=LAB_WORKFLOW, format_scope="legacy format isolates workflow gate only",
            key_scope="ephemeral synthetic key; private key destroyed, never uploaded"), indent=2) + "\n")


class LabReleaseIdentityAcceptanceTest(unittest.TestCase):
    def test_legacy_identity_is_only_an_isolated_capability_positive(self):
        result = FIXTURE.verify("legacy-test-positive", FIXTURE.old, verifier=LEGACY)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_actual_verifier_accepts_exact_lab_workflow_identity(self):
        result = FIXTURE.verify("lab-identity-behavior", FIXTURE.lab)
        if result.returncode != 0 and WORKFLOW_REFUSAL not in result.stderr:
            raise CapabilityError("not the frozen workflow-identity refusal: " + result.stderr)
        self.assertEqual(0, result.returncode,
                         "valid real Lab bundle/signature must pass the Lab workflow verifier; " + result.stderr)

    def test_wrong_expected_version_and_commit_are_refused(self):
        for field in ("version", "commit"):
            with self.subTest(field=field):
                options = {field: "999.54.1" if field == "version" else "f" * 40}
                result = FIXTURE.verify("wrong-" + field, FIXTURE.old, verifier=LEGACY, **options)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("statement is for another " + field, result.stderr)

    def test_other_repository_and_workflow_are_refused(self):
        for name, workflow in (
            ("wrong-repository", LAB_WORKFLOW.replace("YeliangDu/", "synthetic/")),
            ("wrong-workflow", LAB_WORKFLOW.replace("lab-isolated-release.yml", "not-the-release.yml")),
        ):
            with self.subTest(name=name):
                statement = FIXTURE.statement(name, workflow)
                result = FIXTURE.verify(name + "-refused", statement)
                self.assertNotEqual(0, result.returncode)

    def test_changed_archive_or_statement_signature_are_refused(self):
        archive = FIXTURE.temporary / "changed.tar.gz"
        shutil.copyfile(FIXTURE.args.bundle, archive)
        with archive.open("ab") as handle:
            handle.write(b"lab54-tamper")
        changed = FIXTURE.verify("changed-archive-refused", FIXTURE.old, verifier=LEGACY, bundle=archive)
        self.assertNotEqual(0, changed.returncode)
        self.assertIn("bundle differs from the signed statement", changed.stderr)
        statement = FIXTURE.temporary / "changed.release"
        statement.write_text(FIXTURE.old.read_text() + "lab54-tamper: yes\n")
        shutil.copyfile(str(FIXTURE.old) + ".sig", str(statement) + ".sig")
        changed = FIXTURE.verify("changed-signature-refused", statement, verifier=LEGACY)
        self.assertNotEqual(0, changed.returncode)
        self.assertIn("signature does not verify", changed.stderr)


def main():
    global FIXTURE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--bundle-sha256", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--verifier", type=Path, default=LEGACY)
    parser.add_argument("--verifier-sha256")
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    args.bundle = args.bundle.resolve()
    args.verifier = args.verifier.resolve()
    args.evidence = args.evidence.resolve()
    try:
        with tempfile.TemporaryDirectory(prefix="lab54-identity-") as temporary:
            FIXTURE = Fixture(args, temporary)
            FIXTURE.preflight()
            suite = unittest.defaultTestLoader.loadTestsFromTestCase(LabReleaseIdentityAcceptanceTest)
            result = unittest.TextTestRunner(verbosity=2).run(suite)
            counts = dict(tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
                          skipped=len(result.skipped), capability="passed", scope="identity only")
            (args.evidence / "result.json").write_text(json.dumps(counts, indent=2) + "\n")
            return 0 if result.wasSuccessful() else 1
    except (CapabilityError, OSError, ValueError, subprocess.SubprocessError, tarfile.TarError) as error:
        args.evidence.mkdir(parents=True, exist_ok=True)
        (args.evidence / "capability.json").write_text(json.dumps(dict(capability="blocked", reason=str(error)), indent=2) + "\n")
        print("CAPABILITY_BLOCKED (not behavior RED): " + str(error), file=sys.stderr)
        return 86


if __name__ == "__main__":
    raise SystemExit(main())
