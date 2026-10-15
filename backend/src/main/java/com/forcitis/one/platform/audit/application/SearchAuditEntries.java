package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.domain.AuditEntryId;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looking into the audit trail is itself audited (intent 152), in the transaction of the read. */
@Service
public class SearchAuditEntries {
    private static final String LIST = "audit-entries";

    private final AuditEntryQueries entries;
    private final AuditLog audit;

    SearchAuditEntries(AuditEntryQueries entries, AuditLog audit) {
        this.entries = entries;
        this.audit = audit;
    }

    @Transactional
    public AuditEntryPageView execute(AuditSearch search, UserId actor) {
        AuditPage page = entries.findPage(new AuditQuery(search.actorId() == null ? null : new UserId(search.actorId()),
                search.onBehalfOfId() == null ? null : new UserId(search.onBehalfOfId()), AuditSearch.blankToNull(search.action()),
                AuditSearch.blankToNull(search.actionPrefix()), AuditSearch.blankToNull(search.subjectKey()),
                AuditSearch.blankToNull(search.sourceAddress()), search.outcome(), search.from(), search.to(), position(search.page()),
                search.page().limit()));
        audit.record(new AuditEntry("platform.audit.audit-entry.searched", actor, null, "audit-entries:search",
                "returned=" + page.items().size() + " filter=" + search.describe()));
        return new AuditEntryPageView(page.items().stream().map(AuditEntryView::of).toList(),
                page.next() == null ? null : PageRequest.encode(LIST, ChronoUnit.MICROS.between(Instant.EPOCH, page.next().createdAt()) + "|"
                        + page.next().entryId().value()));
    }

    private static AuditQuery.Position position(PageRequest page) {
        if (page.cursorValue() == null) {
            return null;
        }
        String[] parts = page.cursorValue().split("\\|");
        try {
            return new AuditQuery.Position(Instant.EPOCH.plus(Long.parseLong(parts[0]), ChronoUnit.MICROS), new AuditEntryId(Uuids.parseCanonical(parts[1])));
        } catch (RuntimeException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }
}
