package com.forcitis.one.platform.audit.application;

import java.time.Instant;

public record AuditSearch(Long actorId, String action, String subjectKey, Instant from, Instant to, int page, int limit) {
}
