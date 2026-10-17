package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.util.UUID;

public interface AuditEntryRepository {

    AuditEntryId save(AuditEntry entry, Instant createdAt);

    /** Idempotent by the event id: false when the event was written before. */
    boolean saveForEvent(AuditEntry entry, Instant createdAt, UUID eventId, UUID correlationId);
}
