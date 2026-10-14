package com.forcitis.one.cost.timesheet.application;

/** A business refusal; permission failures are Spring Security's AccessDeniedException. */
public class TimesheetOperationException extends RuntimeException {

    private final TimesheetError error;

    public TimesheetOperationException(TimesheetError error, String message) {
        super(message);
        this.error = error;
    }

    public TimesheetError error() {
        return error;
    }
}
