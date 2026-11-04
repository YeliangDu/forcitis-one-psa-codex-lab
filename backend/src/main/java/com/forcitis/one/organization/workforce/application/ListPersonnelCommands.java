package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Filter;
import com.forcitis.one.organization.workforce.application.CommandReadViews.Personnel;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPersonnelCommands {
    public static final String LIST = "personnel-commands";
    private final CommandReadQueries queries;
    private final PersonnelCommandReadScope scope;

    ListPersonnelCommands(CommandReadQueries queries, PersonnelCommandReadScope scope) {
        this.queries = queries;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PersonnelCommandReadScope.PERMISSION + "')")
    public CursorPage<Personnel> execute(Filter filter, @Nullable EmployeeId employee, PageRequest page) {
        List<CommandReadQueries.Personnel> found = queries.personnel(scope.resolve(), filter.status(), filter.commandKind(), employee,
                CommandReadViews.position(page.cursorValue()), page.limit() + 1);
        List<CommandReadQueries.Personnel> shown = found.stream().limit(page.limit()).toList();
        String next = found.size() > shown.size() ? PageRequest.encode(LIST, shown.getLast().createdAt() + "|" + shown.getLast().personnelCommandId().value()) : null;
        return CursorPage.of(shown.stream().map(Personnel::of).toList(), next);
    }
}
