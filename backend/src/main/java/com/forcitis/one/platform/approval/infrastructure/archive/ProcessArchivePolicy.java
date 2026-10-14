package com.forcitis.one.platform.approval.infrastructure.archive;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No default: a missing value stops the start-up, so the engine history is never kept (or dropped) by accident. */
@ConfigurationProperties("forcitis.platform.approval.process-archive")
record ProcessArchivePolicy(Period onlinePeriod) {
    ProcessArchivePolicy {
        if (onlinePeriod == null || onlinePeriod.isZero() || onlinePeriod.isNegative()) {
            throw new IllegalArgumentException("forcitis.platform.approval.process-archive.online-period is required and must be positive");
        }
    }
}
