package com.forcitis.one.platform.approval.api;

/** Result of a BEFORE hook. Only ALLOW and NOT_APPLICABLE let the action commit (RULE-277). */
public enum HookOutcome {
    ALLOW,
    NOT_APPLICABLE,
    WARN,
    REJECT,
    SUPPLEMENT;

    public boolean commits() {
        return this == ALLOW || this == NOT_APPLICABLE;
    }
}
