package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ListLimits;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditError;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The export is itself an audit entry, written in the same transaction as the read: the file cannot leave without a trace. */
@Service
public class ExportAuditEntries {

    private final AuditEntryQueries entries;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final int maxRows;

    ExportAuditEntries(AuditEntryQueries entries, AuditLog audit, CurrentUser currentUser,
            @Value("${forcitis.platform.audit.export.max-rows}") int maxRows) {
        this.entries = entries;
        this.audit = audit;
        this.currentUser = currentUser;
        if (maxRows < 1 || maxRows > ListLimits.EXPORT_MAX) {
            throw new IllegalArgumentException("forcitis.platform.audit.export.max-rows must be between 1 and " + ListLimits.EXPORT_MAX);
        }
        this.maxRows = maxRows;
    }

    @Transactional
    public AuditExport execute(AuditSearch search) {
        List<AuditRecord> rows = entries.findLatest(search.toFilter(), maxRows + 1);
        if (rows.size() > maxRows) {
            throw new DomainException(AuditError.PLATFORM_AUDIT_EXPORT_TOO_LARGE, ErrorKind.UNPROCESSABLE,
                    "more than " + maxRows + " entries match; narrow the filter");
        }
        audit.record(new AuditEntry("platform.audit.audit-entry.exported", currentUser.userId(), null, AuditSubject.without(AuditSubjectKind.OPERATION),
                "rows=" + rows.size() + " filter=" + search.describe()));
        return new AuditExport(AuditCsv.write(rows).getBytes(StandardCharsets.UTF_8), rows.size());
    }
}
