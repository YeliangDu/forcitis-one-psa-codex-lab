package com.forcitis.one.platform.integration.infrastructure.registry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The event publication registry only guarantees at-least-once if somebody retries: this job redelivers what is not completed at a
 * fixed interval, up to a number of attempts, and alerts for what is left. Completed publications sit in the archive table for the
 * archivePeriod period and are removed by the cleanup job.
 */
@Component
@ConditionalOnProperty(prefix = "forcitis.platform.integration.event-publication", name = "jobs-enabled", havingValue = "true")
class EventPublicationJobs {
    private static final Logger LOG = LoggerFactory.getLogger(EventPublicationJobs.class);

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;
    private final EventPublicationStatistics statistics;
    private final Clock clock;
    private final Duration minimumAge;
    private final int maximumAttempts;
    private final Duration archivePeriod;
    private final Duration staleAfter;
    private final AtomicLong exhaustedNow = new AtomicLong();
    private final AtomicLong staleNow = new AtomicLong();

    EventPublicationJobs(IncompleteEventPublications incomplete, CompletedEventPublications completed, EventPublicationStatistics statistics,
                         MeterRegistry meters, Clock clock,
                         @Value("${forcitis.platform.integration.event-publication.minimum-age:PT1M}") Duration minimumAge,
                         @Value("${forcitis.platform.integration.event-publication.maximum-attempts:8}") int maximumAttempts,
                         @Value("${forcitis.platform.integration.event-publication.archive-period:P30D}") Duration archivePeriod,
                         @Value("${forcitis.platform.integration.event-publication.stale-after:PT10M}") Duration staleAfter) {
        this.incomplete = incomplete;
        this.completed = completed;
        this.statistics = statistics;
        this.clock = clock;
        this.minimumAge = minimumAge;
        this.maximumAttempts = maximumAttempts;
        this.archivePeriod = archivePeriod;
        this.staleAfter = staleAfter;
        Gauge.builder("forcitis.platform.integration.event.publications.exhausted", exhaustedNow, AtomicLong::get)
                .description("Incomplete event publications that used up their redelivery attempts").register(meters);
        Gauge.builder("forcitis.platform.integration.event.publications.stale", staleNow, AtomicLong::get)
                .description("Incomplete event publications older than the alert threshold").register(meters);
    }

    @Scheduled(fixedDelayString = "${forcitis.platform.integration.event-publication.republish-interval:PT1M}")
    @SchedulerLock(name = "platform.integration.event.republish", lockAtMostFor = "PT5M")
    void republish() {
        incomplete.resubmitIncompletePublications(ResubmissionOptions.defaults().withMinAge(minimumAge)
                .withFilter(publication -> publication.getCompletionAttempts() < maximumAttempts));
        long exhausted = statistics.exhausted(maximumAttempts);
        long stale = statistics.incompleteBefore(clock.instant().minus(staleAfter));
        exhaustedNow.set(exhausted);
        staleNow.set(stale);
        if (exhausted > 0) {
            LOG.error("ALERT platform.integration {} event publication(s) failed {} times and are no longer redelivered", exhausted,
                    maximumAttempts);
        }
        if (stale > 0) {
            LOG.error("ALERT platform.integration {} event publication(s) have been incomplete for more than {}", stale, staleAfter);
        }
    }

    @Scheduled(cron = "${forcitis.platform.integration.event-publication.cleanup-cron:0 30 4 * * *}")
    @SchedulerLock(name = "platform.integration.event.cleanup", lockAtMostFor = "PT30M")
    void cleanup() {
        completed.deletePublicationsOlderThan(archivePeriod);
    }
}
