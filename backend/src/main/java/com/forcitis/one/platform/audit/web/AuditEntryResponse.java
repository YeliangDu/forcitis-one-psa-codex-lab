package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.application.AuditEntryView;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record AuditEntryResponse(String entryId, Instant createdAt, String action, @Nullable String actorId, @Nullable String onBehalfOfId, String subjectKey,
                          @Nullable String detail) {

    static AuditEntryResponse of(AuditEntryView view) {
        return new AuditEntryResponse(String.valueOf(view.entryId()), view.createdAt(), view.action(), text(view.actorId()),
                text(view.onBehalfOfId()), view.subjectKey(), view.detail());
    }

    private static String text(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
