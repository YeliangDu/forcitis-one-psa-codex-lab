package com.forcitis.one.cost.timesheet.web;

import com.forcitis.one.cost.timesheet.application.TimesheetOperationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Permission failures stay Spring Security's 403. */
@RestControllerAdvice(assignableTypes = TimesheetController.class)
class TimesheetExceptionAdvice {

    @ExceptionHandler(TimesheetOperationException.class)
    ResponseEntity<TimesheetErrorResponse> refused(TimesheetOperationException e) {
        return ResponseEntity.status(e.error().httpStatus()).body(new TimesheetErrorResponse(e.error().code(), e.getMessage()));
    }
}
