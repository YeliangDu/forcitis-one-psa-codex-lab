package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.attendance.domain.StandardHoursQueries;
import com.forcitis.one.organization.attendance.domain.StandardHoursVersionRecord;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The versions of one scope, newest first, voided ones included; a null legalEntityId is the DEFAULT scope. The cursor is the version number the previous page ended with. */
@Service
public class ListStandardHoursVersions {
    private final StandardHoursQueries queries;

    ListStandardHoursVersions(StandardHoursQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public CursorPage<StandardHoursVersionView> execute(@Nullable LegalEntityId legalEntityId, PageRequest page) {
        List<StandardHoursVersionRecord> found = queries.list(legalEntityId, page.cursorNumber(), page.limit() + 1);
        List<StandardHoursVersionView> items = found.stream().limit(page.limit()).map(StandardHoursVersionView::of).toList();
        return CursorPage.of(items, found.size() > items.size() ? PageRequest.encode("standard-hours-versions", String.valueOf(found.get(items.size() - 1).versionNumber())) : null);
    }
}
