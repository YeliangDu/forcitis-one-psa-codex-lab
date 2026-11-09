"""One vertical acceptance selection; no embedded fake controller or native transport.

A lead-pinned trusted launcher binding is required. Without it exit 86/HOLD.
Binding must operate the actual single CLI/Broker and independently authenticate
raw native and hosted receipts. This module never invents implementation RPCs.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import secrets
import sys
import unittest


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


class CapabilityHold(RuntimeError):
    pass


class R1R2OneVerticalAcceptance(unittest.TestCase):
    binding = None
    contract = None
    nonce = None

    def test_same_cli_broker_native_host_to_hosted_ci_once(self):
        # run_vertical is an external test-driver seam, never a product API.
        result = self.binding.run_vertical(self.contract, self.nonce)
        if result.get('classification') == 'capability-hold':
            raise CapabilityHold(result.get('reason', 'live capability unavailable'))
        self.assertEqual(result['nonce'], self.nonce)
        # Trusted verifier checks raw receipt source/coverage, not candidate flags.
        observed = self.binding.authenticate_vertical(result, self.contract, self.nonce)
        if observed.get('classification') != 'genuine-native-and-hosted':
            raise CapabilityHold('independent native/hosted authentication or correlation absent')
        identities = observed['identity_chain']
        self.assertTrue(identities)
        for item in identities:
            for field in ('cli_closure', 'broker_instance', 'native_host', 'native_session', 'card'):
                self.assertEqual(item[field], identities[0][field], 'SPLIT_VERTICAL_ENTRY:' + field)
        owner = observed['owner']
        self.assertEqual(owner['competing_clients'], 2)
        self.assertEqual(owner['granted_owners'], 1, 'TWO_CLIENTS_GRANTED_OWNER')
        self.assertEqual(owner['old_generation_accepted_effects'], 0, 'STALE_OWNER_NOT_FENCED')
        self.assertEqual(owner['permitted_effect_creations'], 1)
        revocation = observed['revocation']
        self.assertLess(revocation['revoking_seq'], revocation['fenced_seq'])
        self.assertLess(revocation['fenced_seq'], revocation['new_revision_published_seq'],
                        'REVISION_PUBLISHED_BEFORE_EXECUTION_FENCE')
        self.assertIn(revocation['old_turn_state'], ('terminal', 'quarantined-unresolved'))
        self.assertEqual(revocation['old_binding_accepted_requests'], 0)
        self.assertGreaterEqual(revocation['archived_late_items'], 1)
        self.assertEqual(revocation['obsolete_call_replies'], 0, 'LATE_RESULT_RETURNED_TO_OLD_CALL')
        first = observed['first_authorized_actual_action']
        self.assertEqual(first['binding'], observed['new_assignment_binding'], 'FIRST_ACTION_OLD_BINDING')
        self.assertEqual(first['physical_cwd'], observed['new_assignment_binding']['physical_cwd'])
        self.assertEqual(first['authorization_seq'], observed['first_authorized_tool_seq'])
        self.assertLess(first['authorization_seq'], first['execution_seq'])
        self.assertTrue(first['native_turn_id'] and first['native_call_id'] and first['actual_operation_id'])
        race = observed['goal_ci_race']
        self.assertTrue(race['goal_idle_observed'] and race['completion_during_disconnect'])
        self.assertGreaterEqual(race['duplicate_notifications'], 1)
        self.assertEqual(race['durable_wait_completions'], 1, 'WAIT_COMPLETED_TWICE')
        self.assertEqual(race['assignment_advances_after_explicit_resume'], 1, 'DOUBLE_CONTINUATION')
        self.assertEqual(race['advances_while_paused'], 0)
        self.assertEqual(race['pause_cleared_by_background_events'], 0)
        self.assertTrue(race['pause_persisted_across_reconnect'])
        self.assertEqual(race['obsolete_call_replies'], 0)
        self.assertEqual(race['native_wait_call_binding'], race['ci_effect_wait_binding'])
        unknown = observed['unknown_external_effect']
        self.assertIn(unknown['class'], ('commit-push', 'pr-create-update', 'ci-request-rerun'))
        self.assertTrue(unknown['actual_reply_dropped'] and unknown['restarted_or_reconnected'])
        self.assertEqual(unknown['effect_id_before'], unknown['effect_id_after'])
        self.assertEqual(unknown['accepted_external_requests'], 1)
        self.assertEqual(unknown['resend_count'], 0, 'UNKNOWN_EFFECT_RESENT')
        self.assertEqual(unknown['recovery_operations'], 'authoritative-read-only')
        self.assertTrue(unknown['complete_unique_exact_result'])
        boundary = observed['formal_boundary']
        self.assertEqual(boundary['enqueue_denied_attempts'], 1)
        self.assertEqual(boundary['writer_denied_attempts'], 1)
        self.assertEqual(boundary['formal_state_before_sha256'], boundary['formal_state_after_sha256'])
        self.assertEqual(boundary['formal_enqueue_effects'], 0)
        self.assertEqual(boundary['writer_effects'], 0)
        self.assertEqual(observed['final_stage'], 'readonly-plan')


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--trusted-driver', type=Path)
    parser.add_argument('--trusted-driver-sha256')
    parser.add_argument('--scenario-sha256', required=True)
    args = parser.parse_args()
    scenario = Path(__file__).with_name('SCENARIO.json')
    if digest(scenario.read_bytes()) != args.scenario_sha256:
        print('HOLD: independent scenario digest differs', file=sys.stderr)
        return 86
    if args.trusted_driver is None or args.trusted_driver_sha256 is None:
        print('HOLD: actual single CLI/Broker/native-host trusted driver not bound; 0 tests executed', file=sys.stderr)
        return 86
    path = args.trusted_driver.resolve(strict=True)
    raw = path.read_bytes()
    if digest(raw) != args.trusted_driver_sha256:
        print('HOLD: externally pinned trusted driver bytes differ', file=sys.stderr)
        return 86
    # Driver pin must come from independent lead/launcher policy, never candidate JSON.
    spec = importlib.util.spec_from_file_location('_trusted_vertical_driver', path)
    driver = importlib.util.module_from_spec(spec)
    exec(compile(raw, str(path), 'exec'), driver.__dict__)
    if not all(callable(getattr(driver, name, None)) for name in ('run_vertical', 'authenticate_vertical')):
        print('HOLD: trusted driver lacks actual execution/authentication seams', file=sys.stderr)
        return 86
    case = R1R2OneVerticalAcceptance
    case.binding, case.contract, case.nonce = driver, json.loads(scenario.read_text()), secrets.token_hex(24)
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(case))
    if result.errors or result.skipped or result.testsRun != 1:
        return 86
    return 0 if result.wasSuccessful() else 1


if __name__ == '__main__':
    raise SystemExit(main())
