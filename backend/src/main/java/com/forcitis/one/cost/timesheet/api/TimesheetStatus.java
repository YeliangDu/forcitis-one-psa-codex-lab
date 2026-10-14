package com.forcitis.one.cost.timesheet.api;

/** RETURNED and DRAFT are the two states in which the content may be changed and the timesheet submitted again. */
public enum TimesheetStatus {
    DRAFT,
    SUBMITTED,
    RETURNED,
    APPROVED,
    REJECTED;

    public boolean isEditable() {
        return this == DRAFT || this == RETURNED;
    }
}
