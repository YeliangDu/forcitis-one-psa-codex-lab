package com.forcitis.one.platform.identity.domain;

/** What the reinstatement listener decided for one personnel command; the decision is recorded and never taken again. */
public enum HandledOutcome {
    GRANT_OPENED,
    NOT_EMPLOYED,
    ALREADY_GRANTED
}
