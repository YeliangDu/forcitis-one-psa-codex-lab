package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.PageRequest;
import java.time.Instant;

public record AuditSearch(Long actorId, String action, String subjectKey, Instant from, Instant to, PageRequest page) {
}
