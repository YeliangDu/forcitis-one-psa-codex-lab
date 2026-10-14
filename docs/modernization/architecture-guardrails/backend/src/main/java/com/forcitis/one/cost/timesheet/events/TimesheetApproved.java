package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.cost.timesheet.api.TimesheetId;

public record TimesheetApproved(TimesheetId timesheetId, UserId employee) {
}
