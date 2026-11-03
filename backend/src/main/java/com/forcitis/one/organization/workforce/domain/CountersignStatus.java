package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;

/** ADR-0073 item 3.6: a countersign request is open until a finance head approves or rejects it, or it lapses because its command left the wait. */
public enum CountersignStatus {
    OPEN,
    APPROVED,
    REJECTED,
    LAPSED;

    public static final StateMachine<CountersignStatus> MACHINE = StateMachine.of(CountersignStatus.class)
            .allow(OPEN, APPROVED, REJECTED, LAPSED)
            .build();
}
