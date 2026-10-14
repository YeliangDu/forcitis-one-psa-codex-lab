package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import java.time.Instant;
import java.util.UUID;

public record AuditRecord(AuditEntryId entryId, Instant createdAt, String action, UserId actorId, UserId onBehalfOfId,
                          String subjectKey, String detail, String sourceAddress, AuditOutcome outcome, String loginMethod,
                          String changeReason, String beforeText, String afterText, UUID traceNumber) {
}
