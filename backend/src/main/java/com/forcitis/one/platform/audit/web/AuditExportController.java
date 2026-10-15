package com.forcitis.one.platform.audit.web;

import com.forcitis.one.kernel.CallerAccount;
import com.forcitis.one.platform.audit.application.AuditExport;
import com.forcitis.one.platform.audit.application.AuditSearch;
import com.forcitis.one.platform.audit.application.ExportAuditEntries;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
class AuditExportController {

    private final ExportAuditEntries export;
    private final CallerAccount callers;

    AuditExportController(ExportAuditEntries export, CallerAccount callers) {
        this.export = export;
        this.callers = callers;
    }

    @GetMapping(path = "/audit-entries:export")
    @PreAuthorize("hasAuthority('" + AuditPermissions.ENTRY_EXPORT + "')")
    ResponseEntity<byte[]> export(@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
                                  @RequestParam(required = false) String subjectKey,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        AuditExport result = export.execute(new AuditSearch(actorId, null, action, null, subjectKey, null, null, from, to, null), callers.userId());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-entries.csv").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(result.csv());
    }
}
