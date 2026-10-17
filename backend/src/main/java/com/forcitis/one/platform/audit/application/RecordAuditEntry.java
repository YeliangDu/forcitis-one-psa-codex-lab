package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class RecordAuditEntry implements AuditLog {

    private final AuditEntryRepository entries;
    private final Clock clock;

    RecordAuditEntry(AuditEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEntryId record(AuditEntry entry) {
        return entries.save(entry, clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEntry entry) {
        entries.save(entry, clock.instant());
    }
}
