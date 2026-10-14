package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

public record AuditRecord(AuditEntryId entryId, Instant createdAt, String action, UserId actorId, UserId onBehalfOfId,
                               String subjectKey, String detail, String sourceAddress, String result, String changeReason, String beforeText,
                          String afterText, java.util.UUID traceNumber) {
}
