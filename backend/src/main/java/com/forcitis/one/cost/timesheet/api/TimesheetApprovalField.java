package com.forcitis.one.cost.timesheet.api;

public enum TimesheetApprovalField {
    /** The project manager of the work the hours were booked on (one or more user ids). */
    PROJECT_MANAGER("projectManagerId"),
    /** The regional head who also reviews pre-sales hours (one or more user ids). */
    REGIONAL_HEAD("regionalHeadId");

    private final String fieldName;

    TimesheetApprovalField(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }
}
