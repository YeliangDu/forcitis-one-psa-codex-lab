package com.forcitis.one.cost.timesheet.web;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.application.SubmissionResult;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheet;
import com.forcitis.one.cost.timesheet.application.SubmitTimesheetCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{version}/cost/timesheets")
class TimesheetController {

    private final SubmitTimesheet submitTimesheet;

    TimesheetController(SubmitTimesheet submitTimesheet) {
        this.submitTimesheet = submitTimesheet;
    }

    @PostMapping(path = "/{id}:submit", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.TIMESHEET_SUBMIT + "')")
    ResponseEntity<SubmitTimesheetResponse> submit(@PathVariable long id, @RequestBody SubmitTimesheetRequest request) {
        SubmissionResult result = submitTimesheet.execute(new SubmitTimesheetCommand(
                new TimesheetId(id), new WorkPackageId(request.workPackageId()), new Hours(request.hours()), new UserId(request.projectManagerId())));
        return switch (result) {
            case SubmissionResult.Submitted s -> ResponseEntity.ok(new SubmitTimesheetResponse("SUBMITTED", null, s.approvalInstanceId().value(), null));
            case SubmissionResult.ExceedsRemainingHours e -> ResponseEntity.unprocessableContent()
                    .body(new SubmitTimesheetResponse("EXCEEDS_REMAINING_HOURS", e.error().code(), null, e.remaining()));
            case SubmissionResult.UnknownWorkPackage u -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new SubmitTimesheetResponse("UNKNOWN_WORK_PACKAGE", u.error().code(), null, null));
        };
    }
}
