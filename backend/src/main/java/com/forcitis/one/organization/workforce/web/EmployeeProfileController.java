package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.EmployeeProfiles;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current profile of an employee, changed in place by the personnel specialist; the caller is the signed-in user. */
@RestController
@RequestMapping("/api/v1/organization/employees")
class EmployeeProfileController {
    private final EmployeeProfiles profiles;

    EmployeeProfileController(EmployeeProfiles profiles) {
        this.profiles = profiles;
    }

    @PostMapping("/{employeeId}:change-profile")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')")
    void change(@PathVariable UUID employeeId, @RequestBody ChangeEmployeeProfileRequest request) {
        profiles.change(request.toCommand(employeeId));
    }
}
