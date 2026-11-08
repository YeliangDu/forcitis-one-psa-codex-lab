package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.GetActingDuties;
import com.forcitis.one.organization.workforce.application.GetCoveredActingDuties;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Employee-scoped duties of a stand-in and duties naming a covered person. */
@RestController
@RequestMapping("/api/v1/organization")
class ActingDutyController {
    private final GetActingDuties duties;
    private final GetCoveredActingDuties covered;

    ActingDutyController(GetActingDuties duties, GetCoveredActingDuties covered) {
        this.duties = duties;
        this.covered = covered;
    }

    @GetMapping("/acting-duties/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.EMPLOYEE_READ + "')")
    ActingDutyHistoryResponse list(@PathVariable UUID employeeId) {
        return duties.execute(new EmployeeId(employeeId)).map(ActingDutyHistoryResponse::of).orElseThrow(() -> notFound(employeeId));
    }

    @GetMapping("/covered-acting-duties/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.EMPLOYEE_READ + "')")
    ActingDutyHistoryResponse listCovered(@PathVariable UUID employeeId) {
        return covered.execute(new EmployeeId(employeeId)).map(ActingDutyHistoryResponse::of).orElseThrow(() -> notFound(employeeId));
    }

    private static DomainException notFound(UUID employeeId) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND, ErrorKind.NOT_FOUND, "no employee " + employeeId);
    }
}
