package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;

/** ADR-0068 item 2.1: a held separation is held until it is released or its command is withdrawn. */
public enum HoldStatus {
    HELD,
    RELEASED,
    WITHDRAWN;

    public static final StateMachine<HoldStatus> MACHINE = StateMachine.of(HoldStatus.class)
            .allow(HELD, RELEASED, WITHDRAWN)
            .build();
}
