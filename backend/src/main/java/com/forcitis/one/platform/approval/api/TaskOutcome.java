package com.forcitis.one.platform.approval.api;

/** What an approver did with one task (RULE-272/275). RETURNED = sent back to the initiator: this round ends, the initiator starts a new one. */
public enum TaskOutcome {
    APPROVED,
    REJECTED,
    RETURNED
}
