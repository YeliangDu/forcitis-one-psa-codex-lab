package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Structure;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListOrgStructureCommands {
    public static final String LIST = "org-structure-commands";
    private final CommandReadQueries queries;

    ListOrgStructureCommands(CommandReadQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrgStructureCommandService.PERMISSION + "')")
    public CursorPage<Structure> execute(PageRequest page) {
        List<CommandReadQueries.Structure> found = queries.structures(CommandReadViews.position(page.cursorValue()), page.limit() + 1);
        List<CommandReadQueries.Structure> shown = found.stream().limit(page.limit()).toList();
        String next = found.size() > shown.size() ? PageRequest.encode(LIST, shown.getLast().createdAt() + "|" + shown.getLast().orgStructureCommandId().value()) : null;
        return CursorPage.of(shown.stream().map(row -> Structure.of(row, queries.countersigns(row.orgStructureCommandId()))).toList(), next);
    }
}
