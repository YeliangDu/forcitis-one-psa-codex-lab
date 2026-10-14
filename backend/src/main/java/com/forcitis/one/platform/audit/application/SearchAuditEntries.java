package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchAuditEntries {
    private static final String LIST = "audit-entries";

    private final AuditEntryQueries entries;

    SearchAuditEntries(AuditEntryQueries entries) {
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public AuditEntryPageView execute(AuditSearch search) {
        AuditPage page = entries.findPage(new AuditQuery(search.actorId() == null ? null : new UserId(search.actorId()),
                search.onBehalfOfId() == null ? null : new UserId(search.onBehalfOfId()), blankToNull(search.action()),
                blankToNull(search.actionPrefix()), blankToNull(search.subjectKey()), blankToNull(search.sourceAddress()),
                blankToNull(search.result()), search.from(), search.to(), position(search.page()), search.page().limit()));
        return new AuditEntryPageView(page.items().stream().map(AuditEntryView::of).toList(),
                page.next() == null ? null : PageRequest.encode(LIST, java.time.temporal.ChronoUnit.MICROS.between(Instant.EPOCH, page.next().createdAt()) + "|" + page.next().entryId().value()));
    }

    private static AuditQuery.Position position(PageRequest page) {
        if (page.cursorValue() == null) {
            return null;
        }
        String[] parts = page.cursorValue().split("\\|");
        try {
            return new AuditQuery.Position(Instant.EPOCH.plus(Long.parseLong(parts[0]), java.time.temporal.ChronoUnit.MICROS), new com.forcitis.one.platform.audit.domain.AuditEntryId(Long.parseLong(parts[1])));
        } catch (RuntimeException malformed) {
            throw new InvalidCursorException("the cursor is not one this server issued");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
