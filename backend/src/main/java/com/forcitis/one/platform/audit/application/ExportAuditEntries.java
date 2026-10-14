package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditError;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The export is itself an audit entry, written in the same transaction as the read: the file cannot leave without a trace. */
@Service
public class ExportAuditEntries {

    private final AuditEntryQueries entries;
    private final AuditLog audit;
    private final int maxRows;

    ExportAuditEntries(AuditEntryQueries entries, AuditLog audit, @Value("${forcitis.platform.audit.export.max-rows}") int maxRows) {
        this.entries = entries;
        this.audit = audit;
        this.maxRows = maxRows;
    }

    @Transactional
    public AuditExport execute(AuditSearch search, UserId actor) {
        AuditQuery filters;
        try {
            filters = new AuditQuery(search.actorId() == null ? null : new UserId(search.actorId()), blankToNull(search.action()),
                    blankToNull(search.subjectKey()), search.from(), search.to(), 0, AuditQuery.MAX_LIMIT);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(AuditError.PLATFORM_AUDIT_QUERY_INVALID, ErrorKind.INVALID, invalid.getMessage());
        }
        long matching = entries.count(filters);
        if (matching > maxRows) {
            throw new DomainException(AuditError.PLATFORM_AUDIT_EXPORT_TOO_LARGE, ErrorKind.UNPROCESSABLE,
                    matching + " entries match; narrow the filter to at most " + maxRows);
        }
        List<AuditRecord> rows = entries.findAll(filters, maxRows);
        audit.record(new AuditEntry("platform.audit.audit-entry.exported", actor, null, "audit-entries:export",
                "rows=" + rows.size() + " filter=" + describe(search)));
        return new AuditExport(AuditCsv.write(rows).getBytes(StandardCharsets.UTF_8), rows.size());
    }

    private static String describe(AuditSearch search) {
        return Stream.of(part("actorId", search.actorId()), part("action", blankToNull(search.action())),
                part("subjectKey", blankToNull(search.subjectKey())), part("from", search.from()), part("to", search.to()))
                .filter(java.util.Objects::nonNull).collect(Collectors.joining(","));
    }

    private static String part(String name, Object value) {
        return value == null ? null : name + "=" + String.valueOf(value).replaceAll("\\p{Cntrl}", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
