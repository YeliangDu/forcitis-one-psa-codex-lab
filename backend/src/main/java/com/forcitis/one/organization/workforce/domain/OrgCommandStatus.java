package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.organization.workforce.api.CountersignCommandStatus;

/**
 * ADR-0068 item 4: pending moves to effective or withdrawn; only a merge goes through in progress (its people are being moved, it can no longer be
 * withdrawn) to effective. A command that changes people's cost legal entity first waits for the finance countersign and then goes on as pending or effective (ADR-0073 item 3).
 */
public enum OrgCommandStatus {
    PENDING,
    IN_PROGRESS,
    EFFECTIVE,
    WITHDRAWN,
    AWAITING_ATTRIBUTION_COUNTERSIGN;

    public static final StateMachine<OrgCommandStatus> MACHINE = StateMachine.of(OrgCommandStatus.class)
            .allow(PENDING, IN_PROGRESS, EFFECTIVE, WITHDRAWN)
            .allow(IN_PROGRESS, EFFECTIVE)
            .allow(AWAITING_ATTRIBUTION_COUNTERSIGN, PENDING, EFFECTIVE, WITHDRAWN)
            .build();

    /** Open commands hold their day (one per target per effective date, uk_org_structure_commands_open_*). */
    public boolean isOpen() {
        return this == PENDING || this == IN_PROGRESS || this == AWAITING_ATTRIBUTION_COUNTERSIGN;
    }

    /** The same status as the countersign read paths name it. */
    public CountersignCommandStatus asCountersignCommandStatus() {
        return CountersignCommandStatus.valueOf(name());
    }
}
