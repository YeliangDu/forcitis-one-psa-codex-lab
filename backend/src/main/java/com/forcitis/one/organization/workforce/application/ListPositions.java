package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionQueries;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPositions {
    public enum Status {
        ACTIVE, DISABLED
    }

    private final PositionQueries queries;

    ListPositions(PositionQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public CursorPage<PositionView> execute(@Nullable Status status, PageRequest page) {
        UUID after = page.cursorUuid();
        var rows = queries.list(status == null ? null : status.name(), after == null ? null : new PositionId(after), page.limit() + 1);
        boolean more = rows.size() > page.limit();
        var shown = more ? rows.subList(0, page.limit()) : rows;
        return CursorPage.of(shown.stream().map(PositionView::of).toList(),
                more ? PageRequest.encode("positions", shown.getLast().positionId().value().toString()) : null);
    }
}
