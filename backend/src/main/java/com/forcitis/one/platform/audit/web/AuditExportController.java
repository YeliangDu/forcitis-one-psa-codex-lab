package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.application.AuditExport;
import com.forcitis.one.platform.audit.application.AuditSearch;
import com.forcitis.one.platform.audit.application.ExportAuditEntries;
import java.nio.charset.StandardCharsets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    AuditExportController(ExportAuditEntries export) {
        this.export = export;
    }

    @GetMapping(path = "/audit-entries:export")
    @Operation(description = "Takes the same filter parameters as the list and answers every match in the same fixed order, created_at desc, id desc, without paging. Answers 422 PLATFORM_AUDIT_EXPORT_TOO_LARGE when more entries match than the export limit allows.")
    @PreAuthorize("hasAuthority('" + AuditPermissions.ENTRY_EXPORT + "')")
    ResponseEntity<byte[]> export(@RequestParam(required = false) UUID actorId, @RequestParam(required = false) UUID onBehalfOfId,
                                  @RequestParam(required = false) String action, @RequestParam(required = false) String actionPrefix,
                                  @RequestParam(required = false) String sourceAddress, @RequestParam(required = false) AuditOutcome outcome,
                                  @RequestParam(required = false) AuditSubjectKind subjectKind, @Parameter(description = "Id of the audited object. Must be given together with subjectKind: subjectId alone answers 400, because one id can name several kinds of object; kinds without an object (REQUEST, OPERATION, LOGIN_ATTEMPT) take no subjectId.") @RequestParam(required = false) UUID subjectId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        AuditExport result = export.execute(new AuditSearch(actorId, onBehalfOfId, action, actionPrefix, subjectKind, subjectId, sourceAddress, outcome, from, to));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("audit-entries.csv").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(result.csv());
    }
}
