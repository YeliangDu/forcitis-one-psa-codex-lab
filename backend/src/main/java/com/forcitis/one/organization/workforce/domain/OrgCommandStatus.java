package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.StateMachine;

/**
 * ADR-0068 item 4: pending moves to effective or withdrawn; only a merge goes through in progress (its people are being moved, it can no longer be
 * withdrawn) to effective. The countersign status comes with the countersign line.
 */
public enum OrgCommandStatus {
    PENDING,
    IN_PROGRESS,
    EFFECTIVE,
    WITHDRAWN;

    public static final StateMachine<OrgCommandStatus> MACHINE = StateMachine.of(OrgCommandStatus.class)
            .allow(PENDING, IN_PROGRESS, EFFECTIVE, WITHDRAWN)
            .allow(IN_PROGRESS, EFFECTIVE)
            .build();

    /** Open commands hold their day (one per target per effective date, uk_org_structure_commands_open_*). */
    public boolean isOpen() {
        return this == PENDING || this == IN_PROGRESS;
    }
}
