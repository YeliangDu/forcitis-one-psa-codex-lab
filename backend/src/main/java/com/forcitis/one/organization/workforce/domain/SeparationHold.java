package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WorkforceError;

/**
 * The hold a held separation carries (ADR-0068 item 2). Creating and releasing holds are cards B4 and B6; withdrawing the command withdraws its hold in
 * the same transaction (item 1.6), which is all this card needs of it.
 */
public final class SeparationHold {
    private final SeparationHoldId id;
    private HoldStatus status;
    private long version;

    public SeparationHold(SeparationHoldId id, HoldStatus status, long version) {
        this.id = id;
        this.status = status;
        this.version = version;
    }

    public void withdraw() {
        status = HoldStatus.MACHINE.require(status, HoldStatus.WITHDRAWN, WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_TRANSITION_ILLEGAL);
        version = version + 1;
    }

    public SeparationHoldId id() {
        return id;
    }

    public HoldStatus status() {
        return status;
    }

    public long version() {
        return version;
    }
}
