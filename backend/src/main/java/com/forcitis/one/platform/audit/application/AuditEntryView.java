package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.time.Instant;

public record AuditEntryView(long entryId, Instant createdAt, String action, Long actorId, Long onBehalfOfId, String subjectKey,
                             String detail) {

    static AuditEntryView of(AuditRecord entry) {
        return new AuditEntryView(entry.entryId().value(), entry.createdAt(), entry.action(),
                entry.actorId() == null ? null : entry.actorId().value(),
                entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), entry.subjectKey(), entry.detail());
    }
}
