package com.forcitis.one.platform.approval.domain;

public class ApprovalTransitionException extends RuntimeException {

    public enum Violation {
        ILLEGAL_TRANSITION,
        NOT_INITIATOR,
        REASON_REQUIRED,
        CUTOFF_PASSED
    }

    private final Violation violation;

    public ApprovalTransitionException(Violation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public Violation violation() {
        return violation;
    }
}
