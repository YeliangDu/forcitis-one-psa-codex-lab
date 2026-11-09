import argparse
import hashlib
import importlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import unittest

CFG = None
MODULE = None
OWNER = ("synthetic-owner", 1, hashlib.sha256(b"synthetic-token").hexdigest())
THREAD = "11111111-1111-4111-8111-111111111111"
OTHER = "22222222-2222-4222-8222-222222222222"
EPOCH = "synthetic-active-epoch"


class FirstLaunchAcceptance(unittest.TestCase):
    def capability(self):
        cls = getattr(MODULE, "FirstLaunch", None)
        self.assertTrue(callable(cls), "FIRST_LAUNCH_CAPABILITY_MISSING")
        for name in ("require_stable", "require_resume"):
            self.assertTrue(
                callable(getattr(MODULE, name, None)),
                "FIRST_LAUNCH_CAPABILITY_MISSING:" + name,
            )
        return cls

    def issued(self):
        launch = self.capability()()
        connection = object()
        launch.issue(
            thread_id=THREAD, receipt_id="synthetic-receipt",
            connection=connection, epoch=EPOCH, owner=OWNER,
        )
        return launch, connection

    def authorize(self, launch, connection, **changes):
        arguments = dict(
            thread_id=THREAD, connection=connection, epoch=EPOCH,
            owner=OWNER, revision=7, prior_dispatch=None,
            has_persisted=False,
        )
        arguments.update(changes)
        return launch.authorize(**arguments)

    def test_01_first_launch_binds_receipt_owner_connection_and_revision(self):
        # Each rejected attempt must leave the genuine first launch usable.
        mutations = (
            dict(thread_id=OTHER),
            dict(connection=object()),
            dict(epoch="old-epoch"),
            dict(owner=("other-owner", OWNER[1], OWNER[2])),
            dict(owner=(OWNER[0], 2, OWNER[2])),
            dict(owner=(OWNER[0], OWNER[1], "wrong-token-digest")),
        )
        for mutation in mutations:
            with self.subTest(mutation=tuple(mutation)):
                launch, connection = self.issued()
                with self.assertRaises(ValueError):
                    self.authorize(launch, connection, **mutation)
                self.assertEqual(self.authorize(launch, connection), "first")
                # Consumption is final, including a different assignment.
                for revision in (7, 8):
                    with self.assertRaises(ValueError):
                        self.authorize(launch, connection, revision=revision)

    def test_02_restart_needs_persisted_state_and_verified_stability(self):
        cls = self.capability()
        fresh = cls()
        with self.assertRaises(ValueError):
            self.authorize(fresh, object())
        MODULE.require_resume(phase="stable", persisted=True)
        for phase in ("provisional", "stable"):
            with self.subTest(phase=phase):
                with self.assertRaises(ValueError):
                    MODULE.require_resume(phase=phase, persisted=False)

        MODULE.require_stable(completed=True, persisted=True)
        for completed, persisted in ((True, False), (False, True), (False, False)):
            with self.subTest(completed=completed, persisted=persisted):
                with self.assertRaises(ValueError):
                    MODULE.require_stable(
                        completed=completed, persisted=persisted
                    )

        self.assertEqual(
            self.authorize(cls(), object(), has_persisted=True), "persisted"
        )

    def test_03_duplicate_unknown_or_observed_first_launch_cannot_resend(self):
        self.capability()
        for prior in ("unknown", "observed"):
            for persisted in (False, True):
                with self.subTest(prior=prior, persisted=persisted):
                    launch, connection = self.issued()
                    with self.assertRaises(ValueError):
                        self.authorize(
                            launch, connection, prior_dispatch=prior,
                            has_persisted=persisted,
                        )

        launch, connection = self.issued()
        self.assertEqual(self.authorize(launch, connection), "first")
        with self.assertRaises(ValueError):
            self.authorize(launch, connection)
        # Reissuing the same receipt must not restore consumed authority.
        with self.assertRaises(ValueError):
            launch.issue(
                thread_id=THREAD, receipt_id="synthetic-receipt",
                connection=connection, epoch=EPOCH, owner=OWNER,
            )
        with self.assertRaises(ValueError):
            self.authorize(launch, connection)

    def test_04_issued_receipt_cannot_be_overwritten_or_reused(self):
        self.capability()
        for changes in (
            {},
            dict(thread_id=OTHER),
            dict(connection=object()),
            dict(epoch="another-epoch"),
            dict(owner=(OWNER[0], 2, OWNER[2])),
        ):
            with self.subTest(changes=tuple(changes)):
                launch, connection = self.issued()
                arguments = dict(
                    thread_id=THREAD, receipt_id="synthetic-receipt",
                    connection=connection, epoch=EPOCH, owner=OWNER,
                )
                arguments.update(changes)
                with self.assertRaises(ValueError):
                    launch.issue(**arguments)
                self.assertEqual(self.authorize(launch, connection), "first")


def main():
    global CFG, MODULE
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("--runner-temp", required=True, type=Path)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--evidence-dir", required=True, type=Path)
    parser.add_argument("--parent-netns", required=True)
    parser.add_argument("--source-closure-sha256", required=True)
    CFG = parser.parse_args()
    evidence = CFG.evidence_dir.resolve(strict=True)
    if any(evidence.iterdir()):
        raise SystemExit("Evidence directory must be empty")

    def finish(value, code):
        text = json.dumps(value, indent=2, sort_keys=True) + "\n"
        (evidence / "verdict.json").write_text(text)
        print(text, end="")
        return code

    try:
        CFG.source_root = CFG.source_root.resolve(strict=True)
        CFG.runner_temp = CFG.runner_temp.resolve(strict=True)
        if (
            sys.platform != "linux" or os.geteuid() == 0
            or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted"
            or os.readlink("/proc/self/ns/net") == CFG.parent_netns
        ):
            raise ValueError("isolated hosted runner required")
        raw = (CFG.source_root / "SOURCE-CLOSURE.json").read_bytes()
        if hashlib.sha256(raw).hexdigest() != CFG.source_closure_sha256:
            raise ValueError("source closure pin mismatch")
        pins = json.loads(raw)["files_sha256"]
        for name, expected in pins.items():
            path = (CFG.source_root / name).resolve(strict=True)
            path.relative_to(CFG.source_root)
            if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
                raise ValueError("source dependency mismatch")

        sys.path.insert(0, str(CFG.source_root))
        spec = importlib.util.find_spec("first_launch")
        if spec is not None:
            origin = Path(spec.origin).resolve(strict=True)
            relative = str(origin.relative_to(CFG.source_root))
            if relative not in pins:
                raise ValueError("first_launch is not closure-pinned")
            MODULE = importlib.import_module("first_launch")
    except Exception as error:
        return finish(dict(
            classification="setup-HOLD", tests=0,
            reason=type(error).__name__,
        ), 86)

    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(FirstLaunchAcceptance)
    )
    expected_red = (
        result.testsRun == 4 and len(result.failures) == 9
        and not result.errors and not result.skipped
        and all(
            "FIRST_LAUNCH_CAPABILITY_MISSING" in trace
            for _, trace in result.failures
        )
    )
    hold = bool(result.errors or result.skipped or result.testsRun != 4)
    classification = (
        "execution-HOLD" if hold else
        "expected-capability-assertion-red" if expected_red else
        "bounded-first-launch-regression-passed"
        if result.wasSuccessful() else "assertion-red"
    )
    return finish(dict(
        classification=classification,
        tests=result.testsRun,
        failures=len(result.failures),
        errors=len(result.errors),
        skips=len(result.skipped),
        source_closure_sha256=CFG.source_closure_sha256,
        live_rpc_receipt_verification_proven=False,
        public_broker_integration_proven=False,
    ), 86 if hold else (0 if result.wasSuccessful() else 1))


if __name__ == "__main__":
    raise SystemExit(main())
