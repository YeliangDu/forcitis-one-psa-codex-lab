package com.forcitis.one.platform.approval.infrastructure.archive;

import java.nio.file.Path;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No defaults: a missing value stops the start-up, so the engine history is never kept (or dropped) by accident. */
@ConfigurationProperties("forcitis.platform.approval.process-archive")
record ProcessArchivePolicy(Period onlinePeriod, Period archivePeriod, Path directory) {
    ProcessArchivePolicy {
        require(onlinePeriod, "online-period");
        require(archivePeriod, "archive-period");
        if (directory == null || directory.toString().isBlank() || !directory.isAbsolute()) {
            throw new IllegalArgumentException("forcitis.platform.approval.process-archive.directory is required and must be an absolute path");
        }
        if (archivePeriod.toTotalMonths() <= onlinePeriod.toTotalMonths()) {
            throw new IllegalArgumentException("forcitis.platform.approval.process-archive.archive-period must be longer than online-period");
        }
    }

    private static void require(Period period, String key) {
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("forcitis.platform.approval.process-archive." + key + " is required and must be positive");
        }
    }
}
