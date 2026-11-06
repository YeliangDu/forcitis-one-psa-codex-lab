package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;

/** The legal edges of a separation request, as tg_separation_requests_change allows them (data design record WC0); SEPARATING returns to APPROVED when its SEPARATE command is withdrawn. */
public enum SeparationRequestStatus {
    REQUESTED,
    APPROVED,
    SEPARATING,
    COMPLETED,
    WITHDRAWN,
    REJECTED;

    public static final StateMachine<SeparationRequestStatus> MACHINE = StateMachine.of(SeparationRequestStatus.class)
            .allow(REQUESTED, APPROVED, REJECTED, WITHDRAWN)
            .allow(APPROVED, SEPARATING, WITHDRAWN)
            .allow(SEPARATING, APPROVED, COMPLETED)
            .build();
}
