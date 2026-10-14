package com.forcitis.one.platform.approval.infrastructure.flowable;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.flowable.engine.ManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Flowable moves an async or timer job to the dead letter table once its retries are used up; nothing runs it again until an operator does. */
@Component
class DeadLetterJobMetrics {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterJobMetrics.class);

    private final ManagementService management;

    DeadLetterJobMetrics(ManagementService management, MeterRegistry registry) {
        this.management = management;
        Gauge.builder("forcitis.platform.approval.dead.letter.jobs", this, DeadLetterJobMetrics::deadLetterJobs)
                .description("Process engine jobs that used up their retries and wait for an operator")
                .register(registry);
    }

    double deadLetterJobs() {
        try {
            return management.createDeadLetterJobQuery().count();
        } catch (RuntimeException unreadable) {
            log.warn("platform.approval dead-letter job metric unreadable: {}", unreadable.toString());
            return Double.NaN;
        }
    }
}
