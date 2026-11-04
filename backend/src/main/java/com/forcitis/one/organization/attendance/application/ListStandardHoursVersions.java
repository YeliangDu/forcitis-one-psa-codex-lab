package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.domain.StandardHoursQueries;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The versions of one scope, newest first, voided ones included; a null legalEntityId is the DEFAULT scope. */
@Service
public class ListStandardHoursVersions {
    private final StandardHoursQueries queries;

    ListStandardHoursVersions(StandardHoursQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public List<StandardHoursVersionView> execute(@Nullable LegalEntityId legalEntityId) {
        return queries.list(legalEntityId).stream().map(StandardHoursVersionView::of).toList();
    }
}
