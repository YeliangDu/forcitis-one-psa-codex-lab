package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.ApprovalOperationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the typed refusals of the approval use cases to their status and error code. Permission failures stay Spring Security's 403. */
@RestControllerAdvice(assignableTypes = ApprovalTaskController.class)
class ApprovalExceptionAdvice {

    @ExceptionHandler(ApprovalOperationException.class)
    ResponseEntity<ApprovalErrorResponse> refused(ApprovalOperationException e) {
        return ResponseEntity.status(e.error().httpStatus()).body(new ApprovalErrorResponse(e.error().code(), e.getMessage()));
    }
}
