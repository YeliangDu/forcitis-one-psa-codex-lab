"""Independent #54 material builder; real bundle and OpenSSH, never fake API proof."""
import copy
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import time

ROOT = Path(__file__).resolve().parents[3]
SOURCE = "396d75e48cba5cefc496fbb572a592e97ea3612a"
REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
RID = 1449415666
WORKFLOW = REPOSITORY + "/.github/workflows/lab-isolated-release.yml@refs/heads/main"
LAB_PRINCIPAL = LAB_NAMESPACE = "forcitis-one-codex-lab-release"
LAB_FORMAT = "forcitis-one-codex-lab-release-statement/1"
OLD_PRINCIPAL = OLD_NAMESPACE = "forcitis-one-release"
OLD_FORMAT = "forcitis-one-release-statement/1"
COMPAT_PIN = "eed4e4367bc5337be56d4fe2c3ce4d66f457dea78720cd34f66d13b38f36df9c"
FROZEN_RELEASE_TOOLS = {'scripts/release/statement.sh': 'c6dcf97d6c80c0cea056522b936ecd1184b4c97790501e2ed0a840061d76f74b', 'scripts/release/verify_bundle.sh': '86d67eaed0a9870ddac0a55f0b583f10ab8622f0206c530df5b80cad20250c72', 'scripts/release/lib.sh': '78b810f7772b5162374bd1365083144f697de2b7726d30b05f863826358388e0', 'scripts/release/manifest.py': '7541a5787d056db993899e4b8342526e3e79dc5f8e2f864813fdbae3a4999e4e'}
ORIGINAL_TEST_PIN = "638e235f05d3490bd48aba25fdbdf644089f86648a504908cef15a93796d381c"


class CapabilityError(RuntimeError):
    pass


def sha(path):
    value = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def hash_bytes(raw):
    return hashlib.sha256(raw).hexdigest()


class Materials:
    def __init__(self, args, temporary):
        self.args, self.root = args, Path(temporary)
        self.evidence = args.evidence
        self.evidence.mkdir(parents=True, exist_ok=True)
        self.serial = 0
        self.env = {k: v for k, v in os.environ.items() if not k.startswith(("AWS_", "ALIBABA_", "ALICLOUD_"))
                    and k not in ("GH_TOKEN", "GITHUB_TOKEN", "SSH_AUTH_SOCK")}
        self.env["PYTHONDONTWRITEBYTECODE"] = "1"
        self.ssh = shutil.which("ssh-keygen")
        self.name = "forcitis-one-" + args.version
        self.keys = {}

    def record(self, name, value):
        self.serial += 1
        (self.evidence / f"{self.serial:03d}-{name}.json").write_text(json.dumps(value, indent=2) + "\n")

    def call(self, name, command, *, data=None, environment=None, timeout=240):
        result = subprocess.run([str(x) for x in command], input=data, text=True, env=environment or self.env,
                                capture_output=True, timeout=timeout, check=False)
        self.record(name, dict(command=[str(x) for x in command], returncode=result.returncode,
                               stdout=result.stdout, stderr=result.stderr))
        return result

    @staticmethod
    def capable(result, reason):
        if result.returncode != 0:
            raise CapabilityError(reason + ": " + result.stdout + result.stderr)

    def members(self, archive):
        result = {}
        with tarfile.open(archive, "r:gz") as source:
            for name in ("manifest.json", "SHA256SUMS"):
                try:
                    member = source.extractfile(self.name + "/" + name)
                except KeyError:
                    member = None
                result[name] = member.read() if member is not None else self.base_members[name]
        return result

    def material(self, label, *, archive=None, profile="compat", key=None, fields=None, text=None,
                 namespace=None, principal=None):
        folder = self.root / label
        folder.mkdir(mode=0o700)
        archive = archive or self.args.bundle
        key = key or ("lab" if profile == "lab" else "compat")
        ns = namespace or (LAB_NAMESPACE if profile == "lab" else OLD_NAMESPACE)
        who = principal or (LAB_PRINCIPAL if profile == "lab" else OLD_PRINCIPAL)
        members = self.members(archive)
        values = dict(format=LAB_FORMAT if profile == "lab" else OLD_FORMAT)
        if profile == "lab":
            values.update({"repository": REPOSITORY, "repository-id": str(RID)})
        values.update(version=self.args.version, commit=SOURCE, bundle=self.name + ".tar.gz",
                      **{"bundle-sha256": sha(archive), "manifest-sha256": hash_bytes(members["manifest.json"]),
                         "sha256sums-sha256": hash_bytes(members["SHA256SUMS"]), "workflow": WORKFLOW,
                         "run": os.environ["GITHUB_RUN_ID"]})
        if profile == "lab":
            values["run-attempt"] = os.environ["GITHUB_RUN_ATTEMPT"]
        values.update(fields or {})
        body = "".join(k + ": " + v + "\n" for k, v in values.items())
        statement = folder / "statement"
        statement.write_text(text(body) if text else body)
        statement.chmod(0o644)
        signers = folder / "external-signers"
        signers.write_text(who + " " + Path(str(self.keys[key]) + ".pub").read_text())
        signers.chmod(0o644)
        self.capable(self.call(label + "-sign", [self.ssh, "-q", "-Y", "sign", "-f", self.keys[key],
                                                "-n", ns, statement]), "real ephemeral signing")
        Path(str(statement) + ".sig").chmod(0o644)
        # Valid cryptography independent of target policy; malformed-field/tar
        # cases cannot be mistaken for an invalid SSH signature.
        self.capable(self.call(label + "-crypto-control", [self.ssh, "-Y", "verify", "-f", signers,
                     "-I", who, "-n", ns, "-s", str(statement) + ".sig"], data=statement.read_text()),
                     "real signature capability")
        for path in (statement, Path(str(statement) + ".sig"), signers):
            shutil.copyfile(path, self.evidence / (label + "-" + path.name))
        self.record(label + "-material", dict(archive_sha256=sha(archive), statement_sha256=sha(statement),
                    signers_sha256=sha(signers), principal=who, namespace=ns,
                    statement_claims=values, native_workflow_run_verified=False))
        return dict(archive=Path(archive), statement=statement, signers=signers)

    def verify(self, label, material, *, strict=False, version=None, commit=None,
               archive_pin=None, signers_pin=None, pins=True, environment=None):
        target = self.args.namespace_verifier if strict else self.args.verifier
        command = ["bash", target, "verify", material["archive"], material["statement"], material["signers"],
                   version or self.args.version, commit or SOURCE]
        if pins:
            command += [archive_pin or sha(material["archive"]), signers_pin or sha(material["signers"])]
        self.record(label + "-inputs", dict(archive_sha256=sha(material["archive"]),
                    statement_sha256=sha(material["statement"]), signers_sha256=sha(material["signers"]),
                    signature_sha256=sha(str(material["statement"]) + ".sig"), strict=strict,
                    expected_archive_pin=archive_pin or sha(material["archive"]) if pins else None,
                    expected_signers_pin=signers_pin or sha(material["signers"]) if pins else None))
        for part in ("statement", "signers"):
            shutil.copyfile(material[part], self.evidence / (label + "-actual-" + part))
        shutil.copyfile(str(material["statement"]) + ".sig", self.evidence / (label + "-actual-signature"))
        return self.call(label, command, environment=environment)

    def variant(self, label, *, mutate_manifest=None, add=None, omit=None):
        folder = self.root / (label + "-archive")
        folder.mkdir(mode=0o700)
        target = folder / (self.name + ".tar.gz")
        with target.open("wb") as output, gzip.GzipFile(filename="", fileobj=output, mode="wb", mtime=0,
                                                        compresslevel=1) as zipped:
            with tarfile.open(fileobj=zipped, mode="w|", format=tarfile.PAX_FORMAT) as destination:
                with tarfile.open(self.args.bundle, "r|gz") as source:
                    for item in source:
                        if item.name == omit:
                            continue
                        entry = copy.copy(item)
                        stream = source.extractfile(item) if item.isfile() else None
                        if mutate_manifest and item.name == self.name + "/manifest.json":
                            raw = mutate_manifest(stream.read())
                            entry.size, stream = len(raw), io.BytesIO(raw)
                        destination.addfile(entry, stream)
                        if stream is not None:
                            stream.close()
                if add:
                    info, data = add
                    destination.addfile(info, io.BytesIO(data) if info.isfile() else None)
        target.chmod(0o644)
        self.record(label + "-archive", dict(sha256=sha(target), variant=label, source_sha256=self.args.bundle_sha256,
                                            deterministic_gzip_mtime=0))
        return target

    def gated(self, label, *, replace=None):
        # Test-only SSH boundary barrier, then actual OpenSSH process/status.
        # Target script/stdlib are never imported or patched.
        folder = self.root / label
        folder.mkdir(mode=0o700)
        archive = folder / (self.name + ".tar.gz")
        shutil.copyfile(self.args.bundle, archive)
        archive.chmod(0o644)
        material = self.material(label + "-signed", archive=archive)
        barrier, release, proof = folder / "ready", folder / "release", folder / "ssh-result.json"
        shim = folder / "ssh-keygen"
        shim.write_text("""#!/usr/bin/env python3
import json, os, subprocess, sys, time
from pathlib import Path
if sys.argv[1:3] != ['-Y','verify']:
    raise SystemExit('unexpected SSH barrier vocabulary')
Path(os.environ['LAB54_READY']).write_text(json.dumps({'pid':os.getpid(),'argv':sys.argv[1:]}))
deadline=time.monotonic()+20
while not Path(os.environ['LAB54_RELEASE']).exists():
    if time.monotonic()>deadline: raise SystemExit(87)
    time.sleep(0.01)
result=subprocess.run([os.environ['LAB54_REAL_SSH'],*sys.argv[1:]],check=False)
Path(os.environ['LAB54_SSH_RESULT']).write_text(json.dumps({'real_ssh_returncode':result.returncode}))
raise SystemExit(result.returncode)
""")
        shim.chmod(0o755)
        env = dict(self.env, PATH=str(folder) + os.pathsep + self.env["PATH"], LAB54_READY=str(barrier),
                   LAB54_RELEASE=str(release), LAB54_SSH_RESULT=str(proof), LAB54_REAL_SSH=self.ssh)
        command = ["bash", str(self.args.verifier), "verify", str(archive), str(material["statement"]),
                   str(material["signers"]), self.args.version, SOURCE, sha(archive), sha(material["signers"])]
        process = subprocess.Popen(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        change = None
        try:
            deadline = time.monotonic() + 10
            while not barrier.exists() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.01)
            if not barrier.exists():
                raise CapabilityError("real SSH verification barrier not reached")
            if replace:
                path = Path(str(material["statement"]) + ".sig") if replace == "signature" else material[replace]
                raw, before = path.read_bytes(), path.stat()
                alternative = path.with_name(path.name + ".same-bytes-replacement")
                alternative.write_bytes(raw)
                alternative.chmod(0o644)
                os.replace(alternative, path)
                after = path.stat()
                if (before.st_dev, before.st_ino) == (after.st_dev, after.st_ino) or path.read_bytes() != raw:
                    raise CapabilityError("race did not replace inode with exactly equal bytes")
                change = dict(input=replace, sha256=hash_bytes(raw), before_inode=before.st_ino, after_inode=after.st_ino)
            release.touch()
            stdout, stderr = process.communicate(timeout=45)
            if not proof.exists() or json.loads(proof.read_text()).get("real_ssh_returncode") != 0:
                raise CapabilityError("race lacked a successful actual OpenSSH verification")
            result = subprocess.CompletedProcess(command, process.returncode, stdout, stderr)
            self.record(label + "-race", dict(returncode=result.returncode, stdout=stdout, stderr=stderr,
                        ready=json.loads(barrier.read_text()), real_ssh=json.loads(proof.read_text()), change=change,
                        shim_sha256=sha(shim), actual_ssh_sha256=sha(self.ssh)))
            return result
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=10)

    def preflight(self):
        if os.environ.get("GITHUB_ACTIONS") != "true" or not os.environ.get("GITHUB_RUN_ID", "").isdigit() \
                or not os.environ.get("GITHUB_RUN_ATTEMPT", "").isdigit():
            raise CapabilityError("actual hosted runner context required; do not execute locally")
        for tool in ("bash", "ssh-keygen", "java", "javap", "unzip", "tar", "python3"):
            if shutil.which(tool) is None:
                raise CapabilityError("missing tool: " + tool)
        for path, expected in FROZEN_RELEASE_TOOLS.items():
            if sha(ROOT / path) != expected:
                raise CapabilityError("frozen build/legacy tool changed: " + path)
        if sha(self.args.verifier) != self.args.verifier_sha256 or sha(self.args.namespace_verifier) != self.args.namespace_verifier_sha256:
            raise CapabilityError("selected verifier differs from external reviewed source pin")
        if sha(ROOT / "scripts/session/codex_lab/lab_release_identity_acceptance.py") != ORIGINAL_TEST_PIN:
            raise CapabilityError("original five acceptance cases changed")
        if sha(self.args.bundle) != self.args.bundle_sha256 or self.args.bundle.name != self.name + ".tar.gz":
            raise CapabilityError("actual package differs from external build pin/name")
        self.capable(self.call("real-bundle-capability", ["bash", ROOT / "scripts/release/verify_bundle.sh", self.args.bundle],
                               timeout=300), "actual bundle validation")
        self.base_members = {}
        with tarfile.open(self.args.bundle, "r:gz") as source:
            for name in ("manifest.json", "SHA256SUMS"):
                with source.extractfile(self.name + "/" + name) as member:
                    self.base_members[name] = member.read()
        manifest = json.loads(self.base_members["manifest.json"])
        if manifest.get("gitSha") != SOURCE or manifest.get("version") != self.args.version:
            raise CapabilityError("actual package manifest is not the frozen source/version")
        for name in ("compat", "other", "lab"):
            key = self.root / (name + "-ephemeral-key")
            self.capable(self.call(name + "-keygen", [self.ssh, "-q", "-t", "ed25519", "-N", "", "-f", key]), "ephemeral key")
            self.keys[name] = key
            shutil.copyfile(str(key) + ".pub", self.evidence / (name + "-ephemeral-key.pub"))
        if len({Path(str(k) + ".pub").read_text() for k in self.keys.values()}) != 3:
            raise CapabilityError("namespace test key must be independently generated")
        self.compat = self.material("valid-direct-compat")
        result = self.verify("valid-direct-compat-capability", self.compat)
        self.capable(result, "actual selected verifier with both external pins")
        receipt = json.loads(result.stdout)
        if not (receipt.get("external_archive_pin_checked") is True and receipt.get("external_signer_file_pin_checked") is True
                and receipt.get("bundle_sha256") == self.args.bundle_sha256
                and receipt.get("native_workflow_run_verified") is False
                and receipt.get("release_authority") is False and receipt.get("installation_authority") is False):
            raise CapabilityError("selected direct verifier capability scope/pin proof differs")
        self.lab = self.material("valid-independent-lab-namespace", profile="lab")
        self.record("capability", dict(capability="passed", source=SOURCE, bundle_sha256=self.args.bundle_sha256,
                    verifier_sha256=sha(self.args.verifier), namespace_verifier_sha256=sha(self.args.namespace_verifier),
                    original_five_sha256=ORIGINAL_TEST_PIN, real_ssh_sha256=sha(self.ssh),
                    independent_namespace_crypto_valid=True, native_workflow_run_verified=False,
                    actual_test_job={k: os.environ.get(k) for k in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_WORKFLOW_REF")},
                    statement_claims_are_not_native_proof=True, private_keys_uploaded=False))
