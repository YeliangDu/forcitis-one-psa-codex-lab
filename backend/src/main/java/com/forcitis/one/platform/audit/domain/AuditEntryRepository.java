package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.platform.audit.api.AuditEntry;
import java.time.Instant;

public interface AuditEntryRepository {

    void save(AuditEntry entry, Instant createdAt);

    AuditPage findPage(AuditQuery query);
}
