package com.forcitis.one.cost.timesheet.api;

/** DRAFT, RETURNED and REJECTED are the states in which the content may be changed and the timesheet submitted again. */
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    RETURNED,
    APPROVED,
    REJECTED;

    public boolean isEditable() {
        return this == DRAFT || this == RETURNED || this == REJECTED;
    }
}
