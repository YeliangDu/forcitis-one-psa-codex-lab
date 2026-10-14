package com.forcitis.one.platform.audit.domain;

import com.forcitis.one.platform.audit.api.AuditOutcome;
import java.time.Instant;

/** What a person may see of their own logins: time, action, outcome, source address and login method; nothing from the free-text detail. */
public record OwnLoginRecord(Instant occurredAt, String action, AuditOutcome outcome, String sourceAddress, String loginMethod) {
}
