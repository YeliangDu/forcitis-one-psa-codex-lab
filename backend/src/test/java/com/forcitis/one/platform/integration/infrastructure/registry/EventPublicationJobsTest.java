package com.forcitis.one.platform.integration.infrastructure.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

class EventPublicationJobsTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");

    IncompleteEventPublications incomplete = mock(IncompleteEventPublications.class);
    CompletedEventPublications completed = mock(CompletedEventPublications.class);
    EventPublicationStatistics statistics = mock(EventPublicationStatistics.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    EventPublicationJobs jobs;

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("clock", Clock.fixed(NOW, ZoneOffset.UTC));
        jobs = new EventPublicationJobs(incomplete, completed, statistics, meters, beans.getBeanProvider(Clock.class), Duration.ofMinutes(1), 8,
                Duration.ofDays(30), Duration.ofMinutes(10));
    }

    private static EventPublication attempts(int attempts) {
        EventPublication publication = mock(EventPublication.class);
        when(publication.getCompletionAttempts()).thenReturn(attempts);
        when(publication.getIdentifier()).thenReturn(UUID.randomUUID());
        return publication;
    }

    @Test
    @DisplayName("the republish job takes publications older than the minimum age, and only those tried fewer than the maximum times")
    void republishesWithinTheAttemptLimit() {
        jobs.republish();

        ArgumentCaptor<ResubmissionOptions> options = ArgumentCaptor.forClass(ResubmissionOptions.class);
        verify(incomplete).resubmitIncompletePublications(options.capture());
        assertThat(options.getValue().getMinAge()).isEqualTo(Duration.ofMinutes(1));
        assertThat(options.getValue().getFilter().test(attempts(0))).isTrue();
        assertThat(options.getValue().getFilter().test(attempts(7))).isTrue();
        assertThat(options.getValue().getFilter().test(attempts(8))).as("the limit is reached: no more redelivery").isFalse();
        assertThat(options.getValue().getFilter().test(attempts(20))).isFalse();
    }

    @Test
    @DisplayName("publications that used up their attempts, and those incomplete for over ten minutes, show in gauges (the alerts)")
    void gaugesCarryTheAlerts() {
        when(statistics.exhausted(8)).thenReturn(2L);
        when(statistics.incompleteBefore(NOW.minus(Duration.ofMinutes(10)))).thenReturn(3L);

        jobs.republish();

        assertThat(meters.get("forcitis.platform.integration.event.publications.exhausted").gauge().value()).isEqualTo(2.0);
        assertThat(meters.get("forcitis.platform.integration.event.publications.stale").gauge().value()).isEqualTo(3.0);

        when(statistics.exhausted(8)).thenReturn(0L);
        when(statistics.incompleteBefore(any())).thenReturn(0L);
        jobs.republish();
        assertThat(meters.get("forcitis.platform.integration.event.publications.exhausted").gauge().value()).isZero();
    }

    @Test
    @DisplayName("the cleanup job removes completed publications older than the retention of 30 days")
    void cleanupUsesTheRetention() {
        jobs.cleanup();

        verify(completed).deletePublicationsOlderThan(Duration.ofDays(30));
    }
}
