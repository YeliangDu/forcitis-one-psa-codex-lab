package com.forcitis.one.platform.approval.api;

/** The one answer behind both the "can withdraw" display and the withdraw / cancel itself (RULE-269, RULE-268, RULE-309). */
public enum WithdrawCheck {
    ALLOWED,
    NOT_INITIATOR,
    NOT_RUNNING,
    CUTOFF_PASSED
}
