"""Launcher-only unit fixture; simulated unittest results, not P1 green/proof."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parent


class LauncherClassificationTest(unittest.TestCase):
    def exercise(self,*,count=7,failures=(),errors=(),skipped=(),expected=(),unexpected=()):
        spec=importlib.util.spec_from_file_location('actual_A2_launcher',ROOT/'run.py')
        actual=importlib.util.module_from_spec(spec);spec.loader.exec_module(actual)
        fake_test=types.SimpleNamespace(id=lambda:'fixture.synthetic-case')
        result=types.SimpleNamespace(testsRun=count,failures=[(fake_test,x) for x in failures],
            errors=[(fake_test,x) for x in errors],skipped=[(fake_test,x) for x in skipped],
            expectedFailures=list(expected),unexpectedSuccesses=list(unexpected),
            wasSuccessful=lambda:not failures and not errors and not unexpected)
        # Only classification control flow is under test. No seven original
        # tests, candidate, worker behavior or containment is simulated as green.
        loader=types.SimpleNamespace(exec_module=lambda module:setattr(module,'net_check',lambda parent:None))
        fake_spec=types.SimpleNamespace(loader=loader)
        with tempfile.TemporaryDirectory(prefix='launcher-classification-') as temporary:
            folder=Path(temporary);args=types.SimpleNamespace(closure=folder/'synthetic-closure.json',source_root=folder,
                closure_sha256='f'*64,report=folder/'result.json')
            with patch.object(actual.importlib.util,'spec_from_file_location',return_value=fake_spec), \
                 patch.object(actual.importlib.util,'module_from_spec',return_value=types.SimpleNamespace()), \
                 patch.object(actual.unittest.defaultTestLoader,'discover',return_value=object()), \
                 patch.object(actual.unittest,'TextTestRunner') as runner, \
                 patch.dict(os.environ,{'INTEGRATED_PARENT_NETNS':'1'}):
                runner.return_value.run.return_value=result
                code=actual.inside(args)
            report=json.loads(args.report.read_text())
        self.assertEqual(count,report['tests'])
        self.assertIs(False,report['activation_authority'])
        self.assertIs(False,report['actual_native_authentication_proved'])
        return code,report

    def test_actual_observed_three_capability_errors_are_hold86_without_nameerror(self):
        code,report=self.exercise(errors=('CAPABILITY_HOLD:open',)*3)
        self.assertEqual(86,code)
        self.assertEqual('HOLD-entry-API-or-capability',report['classification'])
        self.assertEqual(3,len(report['errors']))

    def test_normal_assertion_failure_is_failure1_not_capability_hold(self):
        code,report=self.exercise(failures=('synthetic named assertion',))
        self.assertEqual(1,code)
        self.assertEqual('candidate-contract-assertions-failed',report['classification'])

    def test_exact_seven_success_is_success0(self):
        code,report=self.exercise()
        self.assertEqual(0,code)
        self.assertEqual('hosted-replay-contract-passed',report['classification'])

    def test_skip_or_zero_discovery_is_hold86(self):
        for options in (dict(skipped=('synthetic skipped',)),dict(count=0)):
            with self.subTest(options=options):
                code,report=self.exercise(**options)
                self.assertEqual(86,code)
                self.assertEqual('HOLD-entry-API-or-capability',report['classification'])

    def test_expected_failure_or_unexpected_success_is_not_green(self):
        for options in (dict(expected=('synthetic expected failure',)),dict(unexpected=('synthetic unexpected success',))):
            with self.subTest(options=options):
                code,report=self.exercise(**options)
                self.assertEqual(86,code)
                self.assertEqual('HOLD-entry-API-or-capability',report['classification'])


if __name__=='__main__':unittest.main(verbosity=2)
