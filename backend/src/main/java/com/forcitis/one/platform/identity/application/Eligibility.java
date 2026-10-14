package com.forcitis.one.platform.identity.application;

/** The one definition of "may use the system now": account enabled AND employee employed (RULE-326/327/338). */
public enum Eligibility {
    ACTIVE,
    ACCOUNT_MISSING,
    ACCOUNT_DISABLED,
    EMPLOYEE_UNKNOWN,
    EMPLOYEE_SEPARATED
}
