"""Additive recovery scenarios. Original8+storage9 are selected separately."""
import unittest
import cutover_e2e_selftest as existing
from cutover_host_probe import HostProbe
from recovery_probe import RecoveryProbe


class RecoveryPathAcceptanceTest(unittest.TestCase):
    def fixture(self):
        test = existing.CutoverEndToEndTest(methodName='runTest')
        test.setUp()
        self.addCleanup(test.doCleanups)
        return test

    def test_actual_post_effect_fault_os_reap_then_disk_ref_readback_never_second_cas(self):
        for fault in ('lost-response-restart', 'crash-after-send'):
            with self.subTest(fault=fault):
                test = self.fixture()
                run = getattr(test.driver, 'run_cutover_scenario', None)
                self.assertTrue(callable(run), 'CUTOVER_E2E_DRIVER_UNBOUND')
                host = HostProbe(test)
                recovery = RecoveryProbe(test, host)
                result = run(dict(root=test.root, common_dir=test.common, old_base=test.old,
                    candidate_head=test.candidate, reviewed_v1_sha256=test.digest,
                    history=list(test.history), fault=fault,
                    host_probe=host, recovery_probe=recovery))
                # No protected_send_count or restart_trace existence assertions:
                # OS handles, test-owned actual CAS ledger and real child reads.
                recovery.verify_recovery()
                self.assertIn(result['action'], ('hold', 'confirmed', 'adopted'))
                test.assert_history()

    def test_absent_real_recovery_launcher_is_named_hold_not_simulated_restart(self):
        test = self.fixture()
        recovery = RecoveryProbe(test, HostProbe(test))
        with self.assertRaisesRegex(AssertionError, 'RECOVERY_LAUNCHER_MATERIAL_UNBOUND'):
            recovery.verify_recovery()


if __name__ == '__main__':
    unittest.main(verbosity=2)
