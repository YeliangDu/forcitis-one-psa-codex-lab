package com.forcitis.one.platform.audit.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.CallerAccount;
import com.forcitis.one.platform.audit.api.AuditError;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.application.AuditEntryPageView;
import com.forcitis.one.platform.audit.application.AuditSearch;
import com.forcitis.one.platform.audit.application.SearchAuditEntries;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/audit-entries")
class AuditEntryController {

    private final SearchAuditEntries search;
    private final CallerAccount callers;

    AuditEntryController(SearchAuditEntries search, CallerAccount callers) {
        this.search = search;
        this.callers = callers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + AuditPermissions.ENTRY_READ + "')")
    CursorPage<AuditEntryResponse> list(@RequestParam(required = false) UUID actorId, @RequestParam(required = false) UUID onBehalfOfId,
                           @RequestParam(required = false) String action, @RequestParam(required = false) String actionPrefix,
                           @RequestParam(required = false) String sourceAddress, @RequestParam(required = false) AuditOutcome outcome,
                           @RequestParam(required = false) String subjectKey,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                           @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        AuditEntryPageView page;
        try {
            page = search.execute(new AuditSearch(actorId, onBehalfOfId, action, actionPrefix, subjectKey, sourceAddress, outcome, from, to,
                    PageRequest.of(limit, cursor, "audit-entries")), callers.userId());
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(AuditError.PLATFORM_AUDIT_QUERY_INVALID, ErrorKind.INVALID, invalid.getMessage());
        }
        return CursorPage.of(page.items().stream().map(AuditEntryResponse::of).toList(), page.nextCursor());
    }
}
