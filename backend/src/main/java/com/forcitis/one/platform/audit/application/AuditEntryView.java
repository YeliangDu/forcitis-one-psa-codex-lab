package com.forcitis.one.platform.audit.application;

import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.time.Instant;
import java.util.UUID;

public record AuditEntryView(UUID entryId, Instant createdAt, String action, UUID actorId, UUID onBehalfOfId, String subjectKey,
                             String detail, String sourceAddress, AuditOutcome outcome, String loginMethod, String changeReason,
                             String beforeText, String afterText, UUID traceNumber) {

    static AuditEntryView of(AuditRecord entry) {
        return new AuditEntryView(entry.entryId().value(), entry.createdAt(), entry.action(),
                entry.actorId() == null ? null : entry.actorId().value(),
                entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), entry.subjectKey(), entry.detail(),
                entry.sourceAddress(), entry.outcome(), entry.loginMethod(), entry.changeReason(), entry.beforeText(), entry.afterText(),
                entry.traceNumber());
    }
}
