package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListEmployees {
    private static final String LIST = "employees";
    private final EmployeeRosterQueries queries;
    private final EmployeeVisibility visibility;

    ListEmployees(EmployeeRosterQueries queries, EmployeeVisibility visibility) {
        this.queries = queries;
        this.visibility = visibility;
    }

    @Transactional(readOnly = true)
    public CursorPage<EmployeeRosterView> execute(@Nullable OrgUnitId orgUnit, @Nullable EmploymentStatus status, PageRequest page) {
        UUID after = page.cursorUuid();
        List<EmployeeRosterQueries.Row> rows = queries.list(visibility.forList(), orgUnit, status, after == null ? null : new EmployeeId(after), page.limit() + 1);
        boolean more = rows.size() > page.limit();
        var shown = more ? rows.subList(0, page.limit()) : rows;
        return CursorPage.of(shown.stream().map(EmployeeRosterView::of).toList(),
                more ? PageRequest.encode(LIST, shown.getLast().employeeId().value().toString()) : null);
    }
}
