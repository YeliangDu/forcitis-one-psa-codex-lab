package com.forcitis.one.platform.approval.api;

/** How an approval ended (RULE-289): by enum, never by wording. */
public enum ApprovalOutcome {
    APPROVED,
    REJECTED,
    /** The round was sent back by an approver: it is over; the initiator starts a new round (RULE-271/275). */
    RETURNED,
    CANCELLED
}
