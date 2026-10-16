package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import java.util.List;

/** Reading side of the audit trail. Every list is ordered by created_at desc, id desc. */
public interface AuditEntryQueries {

    AuditPage findPage(AuditFilter filter, AuditPosition after, int limit);

    /** The newest limit entries that match; no paging, no count. */
    List<AuditRecord> findLatest(AuditFilter filter, int limit);

    /** Only entries about this person, only the login and session actions, only the structured columns. */
    OwnLoginRecordPage findOwnLoginRecords(UserId subject, AuditPosition after, int limit);
}
