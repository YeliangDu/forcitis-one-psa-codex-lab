package com.forcitis.one.platform.audit.application;

import com.forcitis.one.kernel.PageRequest;
import java.time.Instant;

public record AuditSearch(Long actorId, Long onBehalfOfId, String action, String actionPrefix, String subjectKey, String sourceAddress,
                          String result, Instant from, Instant to, PageRequest page) {
}
