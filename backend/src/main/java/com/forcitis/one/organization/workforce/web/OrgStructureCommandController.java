package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommands;
import com.forcitis.one.organization.workforce.api.WithdrawOrgStructureCommand;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The entry of org-structure commands (ADR-0068 item 4); the caller is the signed-in user, never a request field. Entry is company-wide: no data-scope check on the target. */
@RestController
@RequestMapping("/api/v1/organization/org-structure-commands")
class OrgStructureCommandController {
    private final OrgStructureCommands commands;

    OrgStructureCommandController(OrgStructureCommands commands) {
        this.commands = commands;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE + "')")
    OrgStructureCommandResponse enter(@RequestBody EnterOrgStructureCommandRequest request) {
        return new OrgStructureCommandResponse(commands.enter(request.toCommand()).value());
    }

    @PostMapping("/{orgStructureCommandId}:withdraw")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE + "')")
    void withdraw(@PathVariable UUID orgStructureCommandId, @RequestBody ReasonRequest request) {
        commands.withdraw(new WithdrawOrgStructureCommand(new OrgStructureCommandId(orgStructureCommandId), request.reason()));
    }
}
