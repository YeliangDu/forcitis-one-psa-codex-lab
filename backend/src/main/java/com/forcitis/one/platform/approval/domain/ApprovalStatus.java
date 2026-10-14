package com.forcitis.one.platform.approval.domain;

/**
 * Lifecycle of one approval request (RULE-289/293): RUNNING with approvers, RETURNED = the round was sent back and ended
 * (the initiator starts a new round), APPROVED / REJECTED / CANCELLED end the approval for good. Stored by name; never inferred from wording.
 */
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
