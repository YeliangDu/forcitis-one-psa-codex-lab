"""Receiver contract negatives. Same hosted candidate, not standalone rollout."""
import base64
import copy
import unittest
from cutover_owned_receiver import ParentReceiver


class RawFixture:
    def __init__(self):
        self.reads = []
    def read(self, key, scope):
        self.reads.append((key, 'synthetic-known-raw-digest'))
        return b'{"native":"synthetic exact raw bytes"}'
    def require_complete(self, required):
        if {key for key, _ in self.reads} != set(required):
            raise AssertionError('RAW_COLLECTOR_NOT_ACTUALLY_READ')


class ParentReceiverContractTest(unittest.TestCase):
    def setUp(self):
        # Pure handle tests avoid socket creation; real peer/socket ownership is
        # exercised by actual old/successor E2E child IPC, never simulated here.
        self.server = object.__new__(ParentReceiver)
        self.server.ledger = RawFixture()
        self.server.allowed = {'candidate_zip': dict(roles=['old'], scope=dict(run=700, attempt=2), before_protected_cas=True)}
        self.server.audit = []
        self.server.approval = dict(number=42, base='a' * 40, head='b' * 40)
        self.server.merge = 'c' * 40
        self.server.recovery = None
        self.server.ordinary = None
        self.effects = []
        class Host:
            def cas(host, base, merge):
                self.effects.append((base, merge))
        self.server.host = Host()

    def request(self, method, **args):
        return dict(version=1, method=method, arguments=args)

    def test_exact_read_returns_original_raw_not_candidate_expected_value(self):
        result, reply = self.server.handle('old', self.request('read', key='candidate_zip', scope=dict(run=700, attempt=2)))
        self.assertTrue(reply)
        self.assertEqual(b'{"native":"synthetic exact raw bytes"}', base64.b64decode(result['base64']))

    def test_wrong_key_scope_phase_refuses_without_raw_read_or_cas(self):
        for role, key, scope in [('old', 'unknown', dict(run=700, attempt=2)),
                                 ('old', 'candidate_zip', dict(run=700, attempt=1)),
                                 ('successor', 'candidate_zip', dict(run=700, attempt=2))]:
            with self.subTest(role=role, key=key, scope=scope):
                with self.assertRaises(AssertionError):
                    self.server.handle(role, self.request('read', key=key, scope=scope))
        self.assertEqual([], self.server.ledger.reads)
        self.assertEqual([], self.effects)

    def test_protected_cas_requires_exact_approval_and_consumed_raw(self):
        request = self.request('protected-cas', approval=self.server.approval, expected_base='a' * 40)
        with self.assertRaisesRegex(AssertionError, 'RAW_COLLECTOR_NOT_ACTUALLY_READ'):
            self.server.handle('old', request)
        self.assertEqual([], self.effects)
        self.server.handle('old', self.request('read', key='candidate_zip', scope=dict(run=700, attempt=2)))
        self.server.handle('old', request)
        self.assertEqual([('a' * 40, 'c' * 40)], self.effects)

    def test_successor_cannot_call_protected_cas_or_unbound_ordinary(self):
        for method, args in [('protected-cas', dict(approval=self.server.approval, expected_base='a' * 40)),
                             ('ordinary-cas', dict(intent={}, expected_base='c' * 40))]:
            with self.subTest(method=method):
                with self.assertRaises(AssertionError):
                    self.server.handle('successor', self.request(method, **args))
        self.assertEqual([], self.effects)

    def test_fault_after_actual_effect_has_no_success_reply(self):
        self.server.handle('old', self.request('read', key='candidate_zip', scope=dict(run=700, attempt=2)))
        effects = self.effects
        class Recovery:
            def cas_then_interrupt_old(recovery, base, merge):
                effects.append(('actual-recovery-port', base, merge))
        self.server.recovery = Recovery()
        result, reply = self.server.handle('old', self.request('protected-cas', approval=self.server.approval, expected_base='a' * 40))
        self.assertIsNone(result)
        self.assertFalse(reply)
        self.assertEqual(1, len(effects))


if __name__ == '__main__':
    unittest.main(verbosity=2)
