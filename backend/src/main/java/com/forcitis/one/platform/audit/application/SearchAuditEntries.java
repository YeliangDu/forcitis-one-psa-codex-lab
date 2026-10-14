package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchAuditEntries {

    private final AuditEntryRepository entries;

    SearchAuditEntries(AuditEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public AuditEntryPageView execute(AuditSearch search) {
        AuditPage page = entries.findPage(new AuditQuery(search.actorId() == null ? null : new UserId(search.actorId()),
                blankToNull(search.action()), blankToNull(search.subjectKey()), search.from(), search.to(), search.page(), search.limit()));
        return new AuditEntryPageView(page.items().stream().map(AuditEntryView::of).toList(), page.page(), page.limit(), page.matchCount());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
