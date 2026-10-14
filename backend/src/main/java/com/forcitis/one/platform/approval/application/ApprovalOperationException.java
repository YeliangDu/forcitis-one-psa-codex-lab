package com.forcitis.one.platform.approval.application;

/**
 * A task or start operation the server refuses for a business reason (not for lack of permission: that is Spring
 * Security's AccessDeniedException). Always thrown before anything is changed, or the transaction rolls back.
 */
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
