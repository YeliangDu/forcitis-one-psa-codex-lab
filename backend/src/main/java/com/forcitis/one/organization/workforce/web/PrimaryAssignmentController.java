package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.GetPrimaryAssignmentHistory;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/primary-assignments")
class PrimaryAssignmentController {
    private final GetPrimaryAssignmentHistory history;

    PrimaryAssignmentController(GetPrimaryAssignmentHistory history) {
        this.history = history;
    }

    /** The primary assignment versions of one employee that are in force as facts (voided versions and empty same-day ranges left out), newest first; readable by whoever may read that employee. */
    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.EMPLOYEE_READ + "')")
    PrimaryAssignmentHistoryResponse list(@PathVariable UUID employeeId) {
        return history.execute(new EmployeeId(employeeId)).map(PrimaryAssignmentHistoryResponse::of).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND, ErrorKind.NOT_FOUND, "no employee " + employeeId));
    }
}
