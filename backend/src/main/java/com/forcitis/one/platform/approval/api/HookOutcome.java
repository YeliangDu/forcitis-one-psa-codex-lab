package com.forcitis.one.platform.approval.api;

/** Only ALLOW and NOT_APPLICABLE let the action commit. */
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
