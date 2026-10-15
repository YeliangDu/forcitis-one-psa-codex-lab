package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.application.AuditEntryView;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record AuditEntryResponse(UUID entryId, Instant createdAt, String action, @Nullable UUID actorId, @Nullable UUID onBehalfOfId,
                          String subjectKey, @Nullable String detail, @Nullable String sourceAddress, @Nullable AuditOutcome outcome,
                          @Nullable String loginMethod, @Nullable String changeReason, @Nullable String beforeText,
                          @Nullable String afterText, @Nullable UUID traceNumber) {

    static AuditEntryResponse of(AuditEntryView view) {
        return new AuditEntryResponse(view.entryId(), view.createdAt(), view.action(), view.actorId(),
                view.onBehalfOfId(), view.subjectKey(), view.detail(), view.sourceAddress(), view.outcome(), view.loginMethod(),
                view.changeReason(), view.beforeText(), view.afterText(), view.traceNumber());
    }
}
