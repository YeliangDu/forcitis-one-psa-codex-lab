package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class RecordAuditEntry implements AuditLog {

    private final AuditEntryRepository entries;
    private final Clock clock;

    RecordAuditEntry(AuditEntryRepository entries, ObjectProvider<Clock> clock) {
        this.entries = entries;
        this.clock = Clocks.from(clock);
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        entries.save(entry, clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditEntry entry) {
        entries.save(entry, clock.instant());
    }
}
