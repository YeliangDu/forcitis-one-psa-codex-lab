package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Structure;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetOrgStructureCommand {
    private final CommandReadQueries queries;

    GetOrgStructureCommand(CommandReadQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrgStructureCommandService.PERMISSION + "')")
    public Structure execute(OrgStructureCommandId id) {
        CommandReadQueries.Structure row = queries.structure(id).orElseThrow(CommandReadViews::structureNotFound);
        return Structure.of(row, queries.countersigns(row.orgStructureCommandId()));
    }
}
