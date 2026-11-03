package com.forcitis.one.organization.workforce.api;

/** Whether one employee is employed today (ADR-0033 section 3 item 7), judged from the employee's versions, or from the snapshot row of a person without versions. */
public enum EmploymentVerdict {
    EMPLOYED,
    SEPARATED,
    NOT_LISTED
}
