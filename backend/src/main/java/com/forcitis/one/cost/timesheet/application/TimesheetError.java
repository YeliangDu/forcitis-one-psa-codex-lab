package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;

public enum TimesheetError implements ErrorCode {
    COST_TIMESHEET_EXCEEDS_REMAINING_HOURS(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_UNKNOWN_WORK_PACKAGE(ErrorKind.NOT_FOUND),
    COST_TIMESHEET_NOT_FOUND(ErrorKind.NOT_FOUND),
    COST_TIMESHEET_TRANSITION_ILLEGAL(ErrorKind.CONFLICT),
    COST_TIMESHEET_CONCURRENT_CHANGE(ErrorKind.CONFLICT),
    COST_TIMESHEET_REASON_REQUIRED(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_CUTOFF_PASSED(ErrorKind.CONFLICT);

    private final ErrorKind kind;

    TimesheetError(ErrorKind kind) {
        this.kind = kind;
    }

    public DomainException refusal(String message) {
        return new DomainException(this, kind, message);
    }
}
