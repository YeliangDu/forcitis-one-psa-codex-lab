package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Filter;
import com.forcitis.one.organization.workforce.application.GetPersonnelCommand;
import com.forcitis.one.organization.workforce.application.ListPersonnelCommands;
import com.forcitis.one.organization.workforce.web.CommandReadResponses.PersonnelReadResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/personnel-commands")
class PersonnelCommandReadController {
    private final ListPersonnelCommands list;
    private final GetPersonnelCommand get;

    PersonnelCommandReadController(ListPersonnelCommands list, GetPersonnelCommand get) {
        this.list = list;
        this.get = get;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')")
    CursorPage<PersonnelReadResponse> list(@RequestParam(required = false) String status, @RequestParam(required = false) String commandKind,
            @RequestParam(required = false) String employeeId, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        var page = list.execute(Filter.of(status, commandKind), employee(employeeId), PageRequest.of(limit, cursor, ListPersonnelCommands.LIST));
        return CursorPage.of(page.items().stream().map(PersonnelReadResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{personnelCommandId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')")
    PersonnelReadResponse get(@PathVariable UUID personnelCommandId) {
        return PersonnelReadResponse.of(get.execute(new PersonnelCommandId(personnelCommandId)));
    }

    private static @Nullable EmployeeId employee(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return new EmployeeId(Uuids.parseCanonical(value));
        } catch (IllegalArgumentException invalid) {
            throw new InvalidRequestException("employeeId must be a UUID");
        }
    }
}
