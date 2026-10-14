package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.cost.timesheet.api.TimesheetId;

public record TimesheetSubmitted(TimesheetId timesheetId, UserId employee, ApprovalInstanceId approvalInstanceId) {
}
