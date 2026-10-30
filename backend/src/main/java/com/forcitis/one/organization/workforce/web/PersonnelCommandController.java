package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommands;
import com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The entry of personnel commands (ADR-0068 item 1); the caller is the signed-in user, never a request field. */
@RestController
@RequestMapping("/api/v1/organization/personnel-commands")
class PersonnelCommandController {
    private final PersonnelCommands commands;

    PersonnelCommandController(PersonnelCommands commands) {
        this.commands = commands;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')")
    PersonnelCommandResponse enter(@RequestBody EnterPersonnelCommandRequest request) {
        return new PersonnelCommandResponse(commands.enter(request.toCommand()).value());
    }

    @PostMapping("/{personnelCommandId}:withdraw")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')")
    void withdraw(@PathVariable UUID personnelCommandId, @RequestBody ReasonRequest request) {
        commands.withdraw(new WithdrawPersonnelCommand(new PersonnelCommandId(personnelCommandId), request.reason()));
    }
}
