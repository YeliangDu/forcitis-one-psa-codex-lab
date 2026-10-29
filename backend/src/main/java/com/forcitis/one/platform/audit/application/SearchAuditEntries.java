package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditPage;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looking into the audit trail is itself audited (intent 152), in the transaction of the read. */
@Service
public class SearchAuditEntries {
    private static final String LIST = "audit-entries";

    private final AuditEntryQueries entries;
    private final Clock clock;
    private final AuditLog audit;
    private final CurrentUser currentUser;

    SearchAuditEntries(AuditEntryQueries entries, AuditLog audit, Clock clock, CurrentUser currentUser) {
        this.entries = entries;
        this.clock = clock;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public AuditEntryPageView execute(AuditSearch search, PageRequest paging) {
        AuditPage page = entries.findPage(search.toFilter(), AuditCursor.decode(paging, clock.instant()), paging.limit());
        audit.record(new AuditEntry("platform.audit.audit-entry.searched", currentUser.userId(), null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "returned=" + page.items().size() + " filter=" + search.describe()));
        return new AuditEntryPageView(page.items().stream().map(AuditEntryView::of).toList(),
                page.next() == null ? null : AuditCursor.encode(LIST, page.next()));
    }
}
