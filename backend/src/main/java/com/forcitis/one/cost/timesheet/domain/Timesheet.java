package com.forcitis.one.cost.timesheet.domain;

import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import java.time.LocalDate;

public record Timesheet(TimesheetId timesheetId, UserId employee, LocalDate workDate, Hours submittedHours, TimesheetStatus status) {
}
