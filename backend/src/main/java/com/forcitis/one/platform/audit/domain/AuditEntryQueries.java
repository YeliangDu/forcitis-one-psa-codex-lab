package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import java.util.List;

/** Reading side of the audit trail. */
public interface AuditEntryQueries {

    AuditPage findPage(AuditQuery query);

    long count(AuditQuery filters);

    /** Newest first, at most maxRows; the paging fields of the query are ignored. */
    List<AuditRecord> findAll(AuditQuery filters, int maxRows);

    /** Newest first; only entries about this person, only the login and session actions, only the structured columns. */
    List<OwnLoginRecord> findOwnLoginRecords(UserId subject, int limit);
}
