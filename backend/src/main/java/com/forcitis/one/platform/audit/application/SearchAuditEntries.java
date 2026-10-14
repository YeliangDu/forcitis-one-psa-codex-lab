package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looking into the audit trail is itself audited (intent 152), in the transaction of the read. */
@Service
public class SearchAuditEntries {

    private final AuditEntryQueries entries;
    private final AuditLog audit;

    SearchAuditEntries(AuditEntryQueries entries, AuditLog audit) {
        this.entries = entries;
        this.audit = audit;
    }

    @Transactional
    public AuditEntryPageView execute(AuditSearch search, UserId actor) {
        AuditPage page = entries.findPage(new AuditQuery(search.actorId() == null ? null : new UserId(search.actorId()),
                AuditSearch.blankToNull(search.action()), AuditSearch.blankToNull(search.subjectKey()), search.from(), search.to(), search.page(),
                search.limit()));
        audit.record(new AuditEntry("platform.audit.audit-entry.searched", actor, null, "audit-entries:search",
                "matches=" + page.matchCount() + " page=" + page.page() + " filter=" + search.describe()));
        return new AuditEntryPageView(page.items().stream().map(AuditEntryView::of).toList(), page.page(), page.limit(), page.matchCount());
    }
}
