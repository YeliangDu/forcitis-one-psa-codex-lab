package com.forcitis.one.platform.approval.domain;

/** Stored by name; never inferred from wording. RETURNED ends the round, the initiator starts a new one. */
public enum ApprovalStatus {
    RUNNING,
    RETURNED,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isLive() {
        return this == RUNNING;
    }
}
