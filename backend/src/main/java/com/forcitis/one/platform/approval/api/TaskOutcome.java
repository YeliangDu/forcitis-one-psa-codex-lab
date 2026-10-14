package com.forcitis.one.platform.approval.api;

/** RETURNED sends the task back to the initiator: this round ends and the initiator starts a new one. */
public enum TaskOutcome {
    APPROVED,
    REJECTED,
    RETURNED
}
