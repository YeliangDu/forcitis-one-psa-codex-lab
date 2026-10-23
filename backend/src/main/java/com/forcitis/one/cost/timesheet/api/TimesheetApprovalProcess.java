package com.forcitis.one.cost.timesheet.api;

import com.forcitis.one.platform.approval.api.ProcessKey;

/** The approval process of a timesheet: the id of processes/cost_timesheet_approval.bpmn20.xml. */
public interface TimesheetApprovalProcess {

    ProcessKey KEY = ProcessKey.of("cost_timesheet_approval");
}
