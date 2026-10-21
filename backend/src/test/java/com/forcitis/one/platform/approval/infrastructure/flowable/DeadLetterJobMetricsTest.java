package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.ref.Reference;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.flowable.engine.ManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DeadLetterJobMetricsTest {
    static final String GAUGE = "forcitis.platform.approval.dead.letter.jobs";
    static final Clock NOW = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZoneOffset.UTC);

    private final Logger logger = (Logger) LoggerFactory.getLogger(DeadLetterJobMetrics.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void capture() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logged);
    }

    @Test
    @DisplayName("the gauge reads the number of dead letter jobs from the engine each time it is scraped")
    void gaugeFollowsTheEngine() {
        ManagementService management = mock(ManagementService.class, RETURNS_DEEP_STUBS);
        when(management.createDeadLetterJobQuery().count()).thenReturn(0L, 3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeadLetterJobMetrics metrics = new DeadLetterJobMetrics(management, registry, NOW);
        System.gc(); // Micrometer holds the gauge's object weakly: without a live reference the gauge reads NaN after a collection

        assertThat(registry.get(GAUGE).gauge().value()).isZero();
        assertThat(registry.get(GAUGE).gauge().value()).isEqualTo(3.0);
        Reference.reachabilityFence(metrics);
    }

    @Test
    @DisplayName("an engine that cannot be queried reads as NaN, so the alert sees missing data instead of a healthy zero")
    void unreadableEngineIsNotZero() {
        ManagementService management = mock(ManagementService.class, RETURNS_DEEP_STUBS);
        when(management.createDeadLetterJobQuery().count()).thenThrow(new IllegalStateException("engine down"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DeadLetterJobMetrics metrics = new DeadLetterJobMetrics(management, registry, NOW);
        System.gc(); // Micrometer holds the gauge's object weakly: without a live reference the gauge reads NaN after a collection

        assertThat(registry.get(GAUGE).gauge().value()).isNaN();
        assertThat(logged.list).as("NaN came from the unreadable engine, not from a collected gauge").anyMatch(e -> e.getLevel() == Level.WARN);
        Reference.reachabilityFence(metrics);
    }

    @Test
    @DisplayName("scraping an unreadable engine over and over writes one warning per five minutes, and another once they have passed")
    void warningIsRateLimited() {
        ManagementService management = mock(ManagementService.class, RETURNS_DEEP_STUBS);
        when(management.createDeadLetterJobQuery().count()).thenThrow(new IllegalStateException("engine down"));
        MutableClock clock = new MutableClock(Instant.parse("2026-10-14T04:00:00Z"));
        DeadLetterJobMetrics metrics = new DeadLetterJobMetrics(management, new SimpleMeterRegistry(), clock);

        for (int scrape = 0; scrape < 20; scrape++) {
            metrics.deadLetterJobs();
        }
        assertThat(warnings()).isEqualTo(1);

        clock.advance(DeadLetterJobMetrics.WARN_EVERY.minusSeconds(1));
        metrics.deadLetterJobs();
        assertThat(warnings()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        metrics.deadLetterJobs();
        assertThat(warnings()).isEqualTo(2);
    }

    private long warnings() {
        return logged.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
