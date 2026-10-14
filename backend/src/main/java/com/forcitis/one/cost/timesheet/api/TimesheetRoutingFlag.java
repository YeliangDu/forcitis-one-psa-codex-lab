package com.forcitis.one.cost.timesheet.api;

/** Routing flags the owning module computes before it starts the timesheet approval; the BPMN only reads them (RULE-262: no business rule in BPMN). */
public enum TimesheetRoutingFlag {
    /** The timesheet contains pre-sales hours, so the regional head reviews as well. */
    PRE_SALES_HOURS("preSalesHours");

    private final String flagName;

    TimesheetRoutingFlag(String flagName) {
        this.flagName = flagName;
    }

    /** The flag name as written in the approval request and in the BPMN. */
    public String flagName() {
        return flagName;
    }
}
