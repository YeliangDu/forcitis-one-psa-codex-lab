package com.forcitis.one.cost.timesheet.api;

/** Computed before the approval starts; the BPMN only reads them (no business rule in BPMN). */
public enum TimesheetRoutingFlag {
    /** The timesheet contains project hours, so the project manager reviews. */
    PROJECT_HOURS("projectHours"),
    /** The timesheet contains pre-sales hours, so the org unit head approves. */
    PRE_SALES_HOURS("preSalesHours"),
    /** The timesheet contains overhead hours, so the initiator's supervisor reviews. */
    OVERHEAD_HOURS("overheadHours");

    private final String flagName;

    TimesheetRoutingFlag(String flagName) {
        this.flagName = flagName;
    }

    public String flagName() {
        return flagName;
    }
}
