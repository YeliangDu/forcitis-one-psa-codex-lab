package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.kernel.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TimesheetError implements ErrorCode {
    COST_TIMESHEET_EXCEEDS_REMAINING_HOURS(HttpStatus.UNPROCESSABLE_CONTENT),
    COST_TIMESHEET_UNKNOWN_WORK_PACKAGE(HttpStatus.NOT_FOUND),
    COST_TIMESHEET_NOT_FOUND(HttpStatus.NOT_FOUND),
    COST_TIMESHEET_TRANSITION_ILLEGAL(HttpStatus.CONFLICT),
    COST_TIMESHEET_CONCURRENT_CHANGE(HttpStatus.CONFLICT),
    COST_TIMESHEET_REASON_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    COST_TIMESHEET_CUTOFF_PASSED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    TimesheetError(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus httpStatus() {
        return status;
    }
}
