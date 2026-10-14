package com.forcitis.one.platform.approval.api;

/**
 * What a person (or the system) did to a task, one value per kind of handling: the trail is append-only. PASSED_AUTOMATICALLY is the
 * system passing a repeat approver (RULE-302); ADVISED is a co-signer's opinion that does not end the round (RULE-273).
 */
public enum TaskRecordAction {
    CLAIMED,
    RELEASED,
    APPROVED,
    REJECTED,
    RETURNED,
    ADVISED,
    PASSED_AUTOMATICALLY,
    TRANSFERRED,
    REASSIGNED;
}
