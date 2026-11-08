"""Hosted-only direct verifier negatives + independent Lab namespace RED.

Original five acceptance methods remain unchanged and run separately. This
suite does not construct GitHub API/run proofs or claim installation authority.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest

from lab54_final_identity_fixture import (COMPAT_PIN, LAB_FORMAT, LAB_NAMESPACE, LAB_PRINCIPAL,
                                         OLD_FORMAT, OLD_NAMESPACE, OLD_PRINCIPAL, REPOSITORY,
                                         RID, ROOT, SOURCE, WORKFLOW, CapabilityError, Materials, sha)

F = None
BAD_PIN = "0" * 64


class FinalLabIdentityAcceptanceTest(unittest.TestCase):
    def refused(self, result):
        self.assertNotEqual(0, result.returncode, "direct verifier accepted the negative input")
        self.assertEqual("", result.stdout.strip(), "refusal must not emit an adoptable success receipt")

    def test_explicit_external_pins_accept_the_valid_same_source_package(self):
        result = F.verify("direct-valid-pins", F.compat)
        self.assertEqual(0, result.returncode, result.stderr)
        receipt = json.loads(result.stdout)
        self.assertIs(True, receipt["external_archive_pin_checked"])
        self.assertIs(True, receipt["external_signer_file_pin_checked"])
        self.assertEqual(F.args.bundle_sha256, receipt["bundle_sha256"])
        self.assertEqual(SOURCE, receipt["commit"])
        self.assertFalse(receipt["native_workflow_run_verified"])
        self.assertFalse(receipt["release_authority"])
        self.assertFalse(receipt["installation_authority"])

    def test_direct_wrong_workflow_repository_ref_version_and_commit_are_refused(self):
        for kind in ("workflow", "repository", "ref", "version", "commit"):
            with self.subTest(kind=kind):
                fields, options = {}, {}
                if kind == "workflow":
                    fields["workflow"] = WORKFLOW.replace("lab-isolated-release.yml", "other.yml")
                elif kind == "repository":
                    fields["workflow"] = WORKFLOW.replace("YeliangDu/", "synthetic/")
                elif kind == "ref":
                    fields["workflow"] = WORKFLOW.replace("refs/heads/main", "refs/heads/topic")
                else:
                    options[kind] = "999.54.1" if kind == "version" else "f" * 40
                material = F.material("direct-wrong-" + kind, fields=fields)
                self.refused(F.verify("direct-wrong-" + kind, material, **options))

    def test_direct_external_archive_pin_signer_pin_and_public_key_are_enforced(self):
        self.refused(F.verify("direct-bad-archive-pin", F.compat, archive_pin=BAD_PIN))
        self.refused(F.verify("direct-bad-signer-pin", F.compat, signers_pin=BAD_PIN))
        wrong = F.material("direct-wrong-external-key")
        wrong["signers"].write_text(OLD_PRINCIPAL + " " + Path(str(F.keys["other"]) + ".pub").read_text())
        self.refused(F.verify("direct-wrong-external-key", wrong))
        key = Path(str(F.keys["other"]) + ".pub").read_bytes()
        info = tarfile.TarInfo(F.name + "/candidate-key.pub")
        info.size, info.mode = len(key), 0o644
        archive = F.variant("candidate-key-in-package", add=(info, key))
        candidate = F.material("direct-package-key-not-trusted", archive=archive, key="other")
        candidate["signers"].write_text(F.compat["signers"].read_text())
        self.refused(F.verify("direct-package-key-not-trusted", candidate))

    def test_direct_unsigned_archive_statement_and_signature_tampering_are_refused(self):
        for kind in ("archive", "statement", "signature"):
            with self.subTest(kind=kind):
                folder = F.root / ("tamper-" + kind + "-copy")
                folder.mkdir()
                archive = folder / (F.name + ".tar.gz")
                shutil.copyfile(F.args.bundle, archive)
                material = F.material("tamper-" + kind, archive=archive)
                if kind == "archive":
                    with archive.open("ab") as handle:
                        handle.write(b"unsigned-lab54-tamper")
                elif kind == "statement":
                    with material["statement"].open("a") as handle:
                        handle.write("unsigned: changed\n")
                else:
                    Path(str(material["statement"]) + ".sig").write_bytes(b"invalid synthetic SSH signature\n")
                self.refused(F.verify("direct-tampered-" + kind, material))

    def test_direct_duplicate_missing_unknown_statement_fields_and_run_are_refused(self):
        variants = {
            "duplicate": lambda body: body + "format: " + OLD_FORMAT + "\n",
            "missing": lambda body: "".join(line for line in body.splitlines(True) if not line.startswith("workflow: ")),
            "unknown": lambda body: body + "candidate-key: use-the-package-key\n",
            "run": lambda body: "".join("run: 0\n" if line.startswith("run: ") else line for line in body.splitlines(True)),
        }
        for name, mutation in variants.items():
            with self.subTest(kind=name):
                material = F.material("statement-" + name, text=mutation)
                self.refused(F.verify("direct-statement-" + name, material))

    def test_direct_signed_manifest_identity_duplicate_and_nonfinite_json_are_refused(self):
        def changed(raw, field, value):
            document = json.loads(raw)
            document[field] = value
            return json.dumps(document).encode()
        variants = {
            "source": lambda raw: changed(raw, "gitSha", "f" * 40),
            "version": lambda raw: changed(raw, "version", "999.54.1"),
            "duplicate": lambda raw: b'{"gitSha":"' + SOURCE.encode() + b'",' + raw.lstrip()[1:],
            "nonfinite": lambda raw: b'{"invalid":NaN,' + raw.lstrip()[1:],
        }
        for name, mutation in variants.items():
            with self.subTest(kind=name):
                archive = F.variant("manifest-" + name, mutate_manifest=mutation)
                material = F.material("signed-manifest-" + name, archive=archive)
                self.refused(F.verify("direct-signed-manifest-" + name, material))

    def test_direct_validly_signed_unsafe_duplicate_or_missing_tar_members_are_refused(self):
        for kind in ("traversal", "absolute", "symlink", "hardlink", "fifo", "duplicate", "missing", "backslash"):
            with self.subTest(kind=kind):
                data = b"synthetic member\n"
                names = {"traversal": F.name + "/../escape", "absolute": "/synthetic-escape",
                         "backslash": F.name + "/bad\\name", "duplicate": F.name + "/manifest.json"}
                info = tarfile.TarInfo(names.get(kind, F.name + "/synthetic-" + kind))
                info.size, info.mode = len(data), 0o644
                if kind == "symlink":
                    info.type, info.linkname, info.size = tarfile.SYMTYPE, "../../escape", 0
                elif kind == "hardlink":
                    info.type, info.linkname, info.size = tarfile.LNKTYPE, F.name + "/forcitis-one.jar", 0
                elif kind == "fifo":
                    info.type, info.size = tarfile.FIFOTYPE, 0
                elif kind == "duplicate":
                    data = F.base_members["manifest.json"]
                    info.size = len(data)
                archive = F.variant("tar-" + kind, add=None if kind == "missing" else (info, data),
                                    omit=F.name + "/SHA256SUMS" if kind == "missing" else None)
                material = F.material("signed-tar-" + kind, archive=archive)
                self.refused(F.verify("direct-tar-" + kind, material))

    def test_direct_input_alias_symlink_and_writable_signers_are_refused(self):
        for kind in ("alias", "symlink", "writable"):
            with self.subTest(kind=kind):
                material = F.material("input-" + kind)
                if kind == "alias":
                    material["signers"] = material["statement"]
                elif kind == "symlink":
                    path = material["statement"].parent / (F.name + ".tar.gz")
                    path.symlink_to(F.args.bundle)
                    material["archive"] = path
                else:
                    material["signers"].chmod(0o666)
                self.refused(F.verify("direct-input-" + kind, material))

    def test_ssh_boundary_gate_without_input_change_is_a_real_positive(self):
        result = F.gated("gate-no-change")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(json.loads(result.stdout)["external_archive_pin_checked"])

    def test_same_byte_inode_replacement_races_are_refused_after_valid_real_ssh(self):
        for kind in ("archive", "statement", "signers", "signature"):
            with self.subTest(input=kind):
                self.refused(F.gated("race-" + kind, replace=kind))

    def test_namespace_accepts_independent_lab_format_principal_and_signature(self):
        result = F.verify("namespace-valid-lab", F.lab, strict=True)
        self.assertEqual(0, result.returncode,
                         "INDEPENDENT_LAB_NAMESPACE_VALID_SIGNATURE_REFUSED: " + result.stderr)
        receipt = json.loads(result.stdout)
        for key, expected in (("statement_format", LAB_FORMAT), ("signature_namespace", LAB_NAMESPACE),
                              ("signer_principal", LAB_PRINCIPAL), ("repository", REPOSITORY),
                              ("repository_id", RID), ("commit", SOURCE), ("version", F.args.version),
                              ("bundle_sha256", F.args.bundle_sha256), ("workflow", WORKFLOW),
                              ("statement_run_claim", os.environ["GITHUB_RUN_ID"]),
                              ("statement_run_attempt_claim", os.environ["GITHUB_RUN_ATTEMPT"])):
            self.assertEqual(expected, receipt[key])
        self.assertIs(int, type(receipt["repository_id"]))
        for key in ("namespace_migrated", "external_archive_pin_checked", "external_signer_file_pin_checked"):
            self.assertIs(True, receipt[key])
        for key in ("native_workflow_run_verified", "release_authority", "installation_authority"):
            self.assertIs(False, receipt[key])

    def test_namespace_refuses_legacy_compatibility_and_other_namespace_confusions(self):
        with self.subTest(kind="legacy-compatibility"):
            result = F.verify("namespace-reject-legacy-compat", F.compat, strict=True)
            self.assertNotEqual(0, result.returncode, "INDEPENDENT_LAB_NAMESPACE_ACCEPTED_LEGACY_COMPATIBILITY")
            self.assertEqual("", result.stdout.strip())
        variants = [
            ("wrong-namespace", dict(namespace=OLD_NAMESPACE), {}),
            ("wrong-format", dict(fields={"format": OLD_FORMAT}), {}),
            ("wrong-principal", dict(principal=OLD_PRINCIPAL), {}),
            ("wrong-repository-id", dict(fields={"repository-id": str(RID + 1)}), {}),
            ("wrong-repository-claim", dict(fields={"repository": "synthetic/forbidden"}), {}),
            ("invalid-attempt-claim", dict(fields={"run-attempt": "0"}), {}),
            ("missing-repository-id", dict(text=lambda body: "".join(
                line for line in body.splitlines(True) if not line.startswith("repository-id: "))), {}),
            ("production-workflow", dict(fields={"workflow": "FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main"}), {}),
            ("missing-pins", {}, dict(pins=False)),
            ("bad-archive-pin", {}, dict(archive_pin=BAD_PIN)),
            ("bad-signer-pin", {}, dict(signers_pin=BAD_PIN)),
        ]
        for kind, material_options, verify_options in variants:
            with self.subTest(kind=kind):
                material = F.material("namespace-" + kind, profile="lab", **material_options)
                self.refused(F.verify("namespace-refuse-" + kind, material, strict=True, **verify_options))
        with self.subTest(kind="wrong-external-key"):
            material = F.material("namespace-wrong-external-key", profile="lab")
            material["signers"].write_text(LAB_PRINCIPAL + " " + Path(str(F.keys["other"]) + ".pub").read_text())
            self.refused(F.verify("namespace-refuse-wrong-external-key", material, strict=True))


def main():
    global F
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--bundle-sha256", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--verifier", type=Path, default=ROOT / "scripts/session/codex_lab/lab_release_statement.sh")
    parser.add_argument("--verifier-sha256", default=COMPAT_PIN)
    parser.add_argument("--namespace-verifier", type=Path)
    parser.add_argument("--namespace-verifier-sha256")
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    args.bundle, args.verifier, args.evidence = args.bundle.resolve(), args.verifier.resolve(), args.evidence.resolve()
    args.namespace_verifier = (args.namespace_verifier or args.verifier).resolve()
    args.namespace_verifier_sha256 = args.namespace_verifier_sha256 or args.verifier_sha256
    try:
        with tempfile.TemporaryDirectory(prefix="lab54-final-identity-") as temporary:
            F = Materials(args, temporary)
            F.preflight()
            suite = unittest.defaultTestLoader.loadTestsFromTestCase(FinalLabIdentityAcceptanceTest)
            result = unittest.TextTestRunner(verbosity=2).run(suite)
            direct_failed = any("test_namespace_" not in test.id() for test, _ in result.failures + result.errors)
            success = result.wasSuccessful() and result.testsRun == 12 and not result.skipped
            F.record("result", dict(tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
                     skipped=len(result.skipped), capability="passed", direct_slice_passed=not direct_failed and result.testsRun == 12 and not result.skipped,
                     strict_namespace_complete=success, native_run_proof_complete=False,
                     separate_signing_job_proved=False, installation_authority=False))
            return 0 if success else 1
    except (CapabilityError, OSError, ValueError, subprocess.SubprocessError, tarfile.TarError) as error:
        args.evidence.mkdir(parents=True, exist_ok=True)
        (args.evidence / "CAPABILITY-BLOCKED.json").write_text(json.dumps(dict(capability="blocked", reason=str(error)), indent=2) + "\n")
        print("CAPABILITY_BLOCKED (not behavior RED): " + str(error), file=sys.stderr)
        return 86


if __name__ == "__main__":
    raise SystemExit(main())
