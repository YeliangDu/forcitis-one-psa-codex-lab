package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Stale is the sync lag the operations alert OneWorkforceSyncLagging fires on: no successful run for longer than staleAfter. It must leave
 * room for at least two sync runs and stay within an hour, so one missed run is not stale and a broken source is noticed the same day.
 */
@Component
class SnapshotFreshness {
    static final Duration LONGEST = Duration.ofHours(1);

    private final SyncRunQueries runs;
    private final Clock clock;
    private final Duration staleAfter;

    SnapshotFreshness(SyncRunQueries runs, Clock clock, @Value("${forcitis.organization.workforce.sync.stale-after}") Duration staleAfter,
            @Value("${forcitis.organization.workforce.sync.cron}") String syncCron) {
        Duration twoRuns = cycleOf(syncCron).multipliedBy(2);
        if (staleAfter.compareTo(twoRuns) < 0 || staleAfter.compareTo(LONGEST) > 0) {
            throw new IllegalArgumentException("forcitis.organization.workforce.sync.stale-after is " + staleAfter + "; it must be at least two sync runs ("
                    + twoRuns + ") and at most " + LONGEST);
        }
        this.runs = runs;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    static Duration cycleOf(String cron) {
        CronExpression expression = CronExpression.parse(cron);
        LocalDateTime first = expression.next(LocalDateTime.of(2026, 1, 1, 0, 0));
        LocalDateTime second = expression.next(first);
        return Duration.between(first.toInstant(ZoneOffset.UTC), second.toInstant(ZoneOffset.UTC));
    }

    boolean isCurrent() {
        return runs.findLastSucceededAt().map(succeeded -> !succeeded.plus(staleAfter).isBefore(clock.instant())).orElse(false);
    }
}
