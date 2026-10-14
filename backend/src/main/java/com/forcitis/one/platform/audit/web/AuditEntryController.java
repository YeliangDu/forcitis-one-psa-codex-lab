package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.application.AuditSearch;
import com.forcitis.one.platform.audit.application.SearchAuditEntries;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/audit-entries")
class AuditEntryController {

    private final SearchAuditEntries search;

    AuditEntryController(SearchAuditEntries search) {
        this.search = search;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + AuditPermissions.ENTRY_READ + "')")
    ResponseEntity<?> list(@RequestParam(required = false) Long actorId, @RequestParam(required = false) String action,
                           @RequestParam(required = false) String subjectKey,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int limit) {
        try {
            return ResponseEntity.ok(AuditEntryPageResponse.of(search.execute(new AuditSearch(actorId, action, subjectKey, from, to, page, limit))));
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest().body(invalid.getMessage());
        }
    }
}
