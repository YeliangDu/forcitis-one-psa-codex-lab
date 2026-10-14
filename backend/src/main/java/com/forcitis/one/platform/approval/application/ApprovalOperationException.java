package com.forcitis.one.platform.approval.application;

/** A business refusal; permission failures are Spring Security's AccessDeniedException. */
public class ApprovalOperationException extends RuntimeException {

    private final ApprovalError error;

    public ApprovalOperationException(ApprovalError error, String message) {
        super(message);
        this.error = error;
    }

    public ApprovalError error() {
        return error;
    }
}
