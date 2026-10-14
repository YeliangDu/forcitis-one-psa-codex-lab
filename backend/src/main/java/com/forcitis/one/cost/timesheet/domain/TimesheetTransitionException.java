package com.forcitis.one.cost.timesheet.domain;

public class TimesheetTransitionException extends RuntimeException {

    public enum Violation {
        ILLEGAL_TRANSITION,
        NOT_OWNER
    }

    private final Violation violation;

    public TimesheetTransitionException(Violation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }
}
