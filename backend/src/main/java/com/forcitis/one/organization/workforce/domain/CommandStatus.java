package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;

/**
 * ADR-0068 item 1.4: pending moves to effective, withdrawn or held; held moves to effective, withdrawn or, when the release comes before the original
 * date, back to pending (ruling on #732: the due-date job then makes it effective and it is never held again). Only a separation can be held.
 */
public enum CommandStatus {
    PENDING,
    HELD,
    EFFECTIVE,
    WITHDRAWN;

    public static final StateMachine<CommandStatus> MACHINE = StateMachine.of(CommandStatus.class)
            .allow(PENDING, EFFECTIVE, WITHDRAWN, HELD)
            .allow(HELD, EFFECTIVE, WITHDRAWN, PENDING)
            .build();

    /** Open commands hold their day (ADR-0068 item 1.5): one per employee per effective date. */
    public boolean isOpen() {
        return this == PENDING || this == HELD;
    }
}
