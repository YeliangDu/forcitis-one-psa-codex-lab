package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.ErrorCode;
import org.springframework.http.HttpStatus;

/** Closed set of business errors of platform.approval (G48); the status is what the web layer answers. */
public enum ApprovalError implements ErrorCode {
    PLATFORM_APPROVAL_TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
    PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED(HttpStatus.CONFLICT),
    PLATFORM_APPROVAL_DECISION_EMPTY(HttpStatus.BAD_REQUEST),
    PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK(HttpStatus.UNPROCESSABLE_CONTENT),
    PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE(HttpStatus.UNPROCESSABLE_CONTENT),
    PLATFORM_APPROVAL_DECISION_SINGLE_ONLY(HttpStatus.UNPROCESSABLE_CONTENT),
    PLATFORM_APPROVAL_REASON_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
    PLATFORM_APPROVAL_INITIATOR_TASK_RESUBMIT_ONLY(HttpStatus.UNPROCESSABLE_CONTENT),
    PLATFORM_APPROVAL_HOOK_BLOCKED(HttpStatus.CONFLICT),
    PLATFORM_APPROVAL_HOOK_NOT_REGISTERED(HttpStatus.CONFLICT),
    PLATFORM_APPROVAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    ApprovalError(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus httpStatus() {
        return status;
    }
}
