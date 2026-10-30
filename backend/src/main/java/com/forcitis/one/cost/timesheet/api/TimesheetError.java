package com.forcitis.one.cost.timesheet.api;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;

public enum TimesheetError implements ErrorCode {
    COST_TIMESHEET_NOT_FOUND(ErrorKind.NOT_FOUND),
    COST_TIMESHEET_NOT_OWNER(ErrorKind.FORBIDDEN),
    COST_TIMESHEET_CUTOFF_PASSED(ErrorKind.CONFLICT),
    COST_TIMESHEET_WORK_DATE_REQUIRED(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_WORK_DATE_DUPLICATE(ErrorKind.CONFLICT),
    COST_TIMESHEET_WORK_DATE_IN_FUTURE(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_WORK_DATE_FIXED(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_RESULT_PENDING(ErrorKind.CONFLICT),
    COST_TIMESHEET_EMPLOYEE_UNKNOWN(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN(ErrorKind.UNPROCESSABLE),
    COST_TIMESHEET_ON_UNPAID_LEAVE(ErrorKind.UNPROCESSABLE);

    private final ErrorKind kind;

    TimesheetError(ErrorKind kind) {
        this.kind = kind;
    }

    public DomainException refusal(String message) {
        return new DomainException(this, kind, message);
    }
}
