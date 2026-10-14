package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flowable.engine.ManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadLetterJobMetricsTest {
    static final String GAUGE = "forcitis.platform.approval.dead.letter.jobs";

    @Test
    @DisplayName("the gauge reads the number of dead letter jobs from the engine each time it is scraped")
    void gaugeFollowsTheEngine() {
        ManagementService management = mock(ManagementService.class, RETURNS_DEEP_STUBS);
        when(management.createDeadLetterJobQuery().count()).thenReturn(0L, 3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new DeadLetterJobMetrics(management, registry);

        assertThat(registry.get(GAUGE).gauge().value()).isZero();
        assertThat(registry.get(GAUGE).gauge().value()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("an engine that cannot be queried reads as NaN, so the alert sees missing data instead of a healthy zero")
    void unreadableEngineIsNotZero() {
        ManagementService management = mock(ManagementService.class, RETURNS_DEEP_STUBS);
        when(management.createDeadLetterJobQuery().count()).thenThrow(new IllegalStateException("engine down"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new DeadLetterJobMetrics(management, registry);

        assertThat(registry.get(GAUGE).gauge().value()).isNaN();
    }
}
