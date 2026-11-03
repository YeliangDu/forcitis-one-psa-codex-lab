package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.organization.workforce.api.CountersignCommandStatus;

/**
 * ADR-0073 item 3 adds the wait for the finance countersign: a command that changes the cost legal entity is entered awaiting, writes no version, and leaves only when
 * every required countersign is approved (pending or effective) or when it is rejected, lapses or is withdrawn. ADR-0068 item 1.4: pending moves to effective, withdrawn or held; held moves to effective, withdrawn or, when the release comes before the original
 * date, back to pending (ruling on #732: the due-date job then makes it effective and it is never held again). Only a separation can be held.
 */
public enum CommandStatus {
    PENDING,
    HELD,
    EFFECTIVE,
    WITHDRAWN,
    AWAITING_ATTRIBUTION_COUNTERSIGN;

    public static final StateMachine<CommandStatus> MACHINE = StateMachine.of(CommandStatus.class)
            .allow(PENDING, EFFECTIVE, WITHDRAWN, HELD)
            .allow(HELD, EFFECTIVE, WITHDRAWN, PENDING)
            .allow(AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING, EFFECTIVE, WITHDRAWN)
            .build();

    /** Open commands hold their day (ADR-0068 item 1.5): one per employee per effective date; an awaiting one holds it too (ADR-0073 item 3.6). */
    public boolean isOpen() {
        return this == PENDING || this == HELD || this == AWAITING_ATTRIBUTION_COUNTERSIGN;
    }

    /** The same status as the countersign read paths name it. */
    public CountersignCommandStatus asCountersignCommandStatus() {
        return CountersignCommandStatus.valueOf(name());
    }
}
