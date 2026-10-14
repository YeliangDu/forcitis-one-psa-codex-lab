package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.kernel.ErrorCode;

public enum TimesheetError implements ErrorCode {
    COST_TIMESHEET_EXCEEDS_REMAINING_HOURS,
    COST_TIMESHEET_UNKNOWN_WORK_PACKAGE
}
