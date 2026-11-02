package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import java.util.Set;

/**
 * The floor under the available super administrators for a revoke or a disable by hand (ADR-0068 item 2.2; user decision 2026-10-30 「加，同离职一样」):
 * refused when none would remain, announced when one remains. Lock order of every path that uses it: {@link #lockDecisions} first, then the account row
 * FOR UPDATE, then the role row FOR SHARE.
 */
public interface AdministratorFloor {

    /** What the act leaves: noticeTo is empty unless exactly one available super administrator remains. */
    record Judgement(Set<EmployeeId> noticeTo) {
        public static final Judgement UNAFFECTED = new Judgement(Set.of());

        public Judgement {
            noticeTo = Set.copyOf(noticeTo);
        }

        public boolean announce() {
            return !noticeTo.isEmpty();
        }
    }

    /** Takes the separation advisory lock every decision about the available administrators takes first; held until the transaction ends. */
    void lockDecisions();

    /**
     * Counts, through the separation review and leaving out the held, released-pending and unpaid-leave-entered people, the available super administrators
     * after the person loses the administrator roles or the account. Throws a refusal when none would remain; unaffected when the person is not an available
     * holder of super_admin or security_auditor. losesSuperAdmin: the person is not a super administrator any more after the act.
     */
    Judgement judge(UserId person, boolean losesSuperAdmin);
}
