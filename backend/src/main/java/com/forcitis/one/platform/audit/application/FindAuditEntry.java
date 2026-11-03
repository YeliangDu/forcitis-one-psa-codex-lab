package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditEntryLookup;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class FindAuditEntry implements AuditEntryLookup {

    private final AuditEntryQueries entries;

    FindAuditEntry(AuditEntryQueries entries) {
        this.entries = entries;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(AuditEntryId id) {
        return entries.exists(id);
    }
}
