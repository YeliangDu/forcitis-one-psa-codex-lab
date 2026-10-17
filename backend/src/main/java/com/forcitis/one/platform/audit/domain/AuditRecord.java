package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import java.time.Instant;
import java.util.UUID;

public record AuditRecord(AuditEntryId entryId, Instant createdAt, String action, UserId actorId, UserId onBehalfOfId,
                          AuditSubject subject, String detail, String sourceAddress, AuditOutcome outcome, String loginMethod,
                          String changeReason, String beforeText, String afterText, UUID traceNumber) {
}
