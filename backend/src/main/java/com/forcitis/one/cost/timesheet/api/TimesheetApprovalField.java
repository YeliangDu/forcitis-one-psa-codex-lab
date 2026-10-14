package com.forcitis.one.cost.timesheet.api;

/** The business form fields the timesheet approval reads to find its approvers (RULE-258/260). */
public enum TimesheetApprovalField {
    /** The project manager of the work the hours were booked on (one or more user ids). */
    PROJECT_MANAGER("projectManagerId"),
    /** The regional head who also reviews pre-sales hours (one or more user ids). */
    REGIONAL_HEAD("regionalHeadId");

    private final String fieldName;

    TimesheetApprovalField(String fieldName) {
        this.fieldName = fieldName;
    }

    /** The field name as written in the approval request and in the BPMN. */
    public String fieldName() {
        return fieldName;
    }
}
