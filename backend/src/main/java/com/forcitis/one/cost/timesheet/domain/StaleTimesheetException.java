package com.forcitis.one.cost.timesheet.domain;

public class StaleTimesheetException extends RuntimeException {

    public StaleTimesheetException(String message) {
        super(message);
    }
}
