package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditError;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The person is the caller, never a parameter: nobody asks for someone else's records through this use case (ADR-0027 section 3). */
@Service
public class ListOwnLoginRecords {

    private final AuditEntryQueries entries;

    ListOwnLoginRecords(AuditEntryQueries entries) {
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public List<OwnLoginRecordView> execute(UserId caller, int limit) {
        if (limit < 1 || limit > AuditQuery.MAX_LIMIT) {
            throw new DomainException(AuditError.PLATFORM_AUDIT_QUERY_INVALID, ErrorKind.INVALID, "limit must be 1.." + AuditQuery.MAX_LIMIT);
        }
        return entries.findOwnLoginRecords(caller, limit).stream().map(OwnLoginRecordView::of).toList();
    }
}
