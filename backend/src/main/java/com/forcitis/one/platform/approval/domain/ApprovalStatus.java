package com.forcitis.one.platform.approval.domain;

/**
 * Lifecycle of one approval request (RULE-289/293): RUNNING with approvers, RETURNED = parked at the initiator after a
 * return (still alive), then one of APPROVED / REJECTED / CANCELLED. Stored by name; never inferred from wording.
 */
public enum ApprovalStatus {
    RUNNING,
    RETURNED,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isLive() {
        return this == RUNNING || this == RETURNED;
    }
}
