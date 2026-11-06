package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.GetEmployee;
import com.forcitis.one.organization.workforce.application.ListEmployees;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/employees")
class EmployeeController {
    private final ListEmployees list;
    private final GetEmployee get;

    EmployeeController(ListEmployees list, GetEmployee get) {
        this.list = list;
        this.get = get;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.EMPLOYEE_READ + "')")
    CursorPage<EmployeeRosterResponse> list(@RequestParam(required = false) @Nullable UUID orgUnitId,
                                           @RequestParam(defaultValue = "EMPLOYED") EmploymentFilter employmentStatus,
                                           @RequestParam(required = false) @Nullable Integer limit, @RequestParam(required = false) @Nullable String cursor) {
        EmploymentStatus status = switch (employmentStatus) {
            case EMPLOYED, ON_UNPAID_LEAVE -> EmploymentStatus.EMPLOYED;
            case SEPARATED -> EmploymentStatus.SEPARATED;
            case ALL -> null;
        };
        var page = list.execute(orgUnitId == null ? null : new OrgUnitId(orgUnitId), status, employmentStatus == EmploymentFilter.ON_UNPAID_LEAVE, PageRequest.of(limit, cursor, "employees"));
        return CursorPage.of(page.items().stream().map(EmployeeRosterResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.EMPLOYEE_READ + "')")
    EmployeeDetailResponse get(@PathVariable UUID employeeId) {
        return get.execute(new EmployeeId(employeeId)).map(EmployeeDetailResponse::of).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND, ErrorKind.NOT_FOUND, "no employee " + employeeId));
    }

    enum EmploymentFilter {
        EMPLOYED, ALL, SEPARATED, ON_UNPAID_LEAVE
    }
}
