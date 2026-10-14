package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.platform.audit.api.AuditEntry;
import java.time.Instant;
import java.util.UUID;

public interface AuditEntryRepository {

    void save(AuditEntry entry, Instant createdAt);

    /** Idempotent by the event id: false when the event was written before. */
    boolean saveForEvent(AuditEntry entry, Instant createdAt, UUID eventId, UUID correlationId);
}
