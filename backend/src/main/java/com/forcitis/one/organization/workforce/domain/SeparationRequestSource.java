package com.forcitis.one.organization.workforce.domain;

/** Where a separation request came from; a SYSTEM request (employer change, migration) skips the human steps and uses a system-only basis. */
public enum SeparationRequestSource {
    EMPLOYEE_SELF,
    HR_ON_BEHALF,
    SYSTEM
}
