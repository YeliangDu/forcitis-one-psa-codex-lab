package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.application.GetOrgStructureCommand;
import com.forcitis.one.organization.workforce.application.ListOrgStructureCommands;
import com.forcitis.one.organization.workforce.web.CommandReadResponses.StructureReadResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/org-structure-commands")
class OrgStructureCommandReadController {
    private final ListOrgStructureCommands list;
    private final GetOrgStructureCommand get;

    OrgStructureCommandReadController(ListOrgStructureCommands list, GetOrgStructureCommand get) {
        this.list = list;
        this.get = get;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE + "')")
    CursorPage<StructureReadResponse> list(@RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        var page = list.execute(PageRequest.of(limit, cursor, ListOrgStructureCommands.LIST));
        return CursorPage.of(page.items().stream().map(StructureReadResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{orgStructureCommandId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE + "')")
    StructureReadResponse get(@PathVariable UUID orgStructureCommandId) {
        return StructureReadResponse.of(get.execute(new OrgStructureCommandId(orgStructureCommandId)));
    }
}
