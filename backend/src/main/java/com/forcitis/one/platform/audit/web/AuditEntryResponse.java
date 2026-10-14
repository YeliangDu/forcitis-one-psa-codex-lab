package com.forcitis.one.platform.audit.web;

import com.forcitis.one.platform.audit.application.AuditEntryView;
import java.time.Instant;

/** Ids are strings: they are signed 64-bit numbers that JavaScript cannot hold exactly. */
record AuditEntryResponse(String entryId, Instant createdAt, String action, String actorId, String onBehalfOfId, String subjectKey,
                          String detail) {

    static AuditEntryResponse of(AuditEntryView view) {
        return new AuditEntryResponse(String.valueOf(view.entryId()), view.createdAt(), view.action(), text(view.actorId()),
                text(view.onBehalfOfId()), view.subjectKey(), view.detail());
    }

    private static String text(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
