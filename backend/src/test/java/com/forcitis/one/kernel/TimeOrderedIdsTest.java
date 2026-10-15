package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeOrderedIdsTest {
    private static final Instant NOON = Instant.parse("2026-10-14T12:00:00.500Z");

    private static final class SteppedClock extends Clock {
        Instant now;

        SteppedClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
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

    private static boolean ascending(List<UUID> ids) {
        for (int i = 1; i < ids.size(); i++) {
            if (compare(ids.get(i - 1), ids.get(i)) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** The order PostgreSQL gives a uuid column: byte by byte, unsigned. */
    private static int compare(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    @Test
    @DisplayName("ids of one millisecond rise in the order they were made and keep that millisecond")
    void sameMillisecondRisesInOrder() {
        TimeOrderedIds ids = new TimeOrderedIds(new SteppedClock(NOON));
        List<UUID> made = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            made.add(ids.next());
        }

        assertThat(ascending(made)).isTrue();
        assertThat(made).allSatisfy(id -> {
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            assertThat(Uuids.instantOf(id)).isEqualTo(NOON);
        });
    }

    @Test
    @DisplayName("more than 4096 ids in one millisecond: the timestamp is carried forward, the order is never broken")
    void counterOverflowCarriesIntoTheTimestamp() {
        TimeOrderedIds ids = new TimeOrderedIds(new SteppedClock(NOON));
        List<UUID> made = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            made.add(ids.next());
        }

        assertThat(ascending(made)).isTrue();
        assertThat(Uuids.instantOf(made.getLast())).isAfter(NOON);
        assertThat(Uuids.instantOf(made.getLast()).toEpochMilli() - NOON.toEpochMilli()).isLessThan(10);
    }

    @Test
    @DisplayName("a clock that steps back does not give a smaller id: the last timestamp is kept and the counter goes on")
    void clockStepsBack() {
        SteppedClock clock = new SteppedClock(NOON);
        TimeOrderedIds ids = new TimeOrderedIds(clock);
        UUID before = ids.next();
        clock.now = NOON.minusSeconds(5);
        UUID after = ids.next();
        UUID afterAgain = ids.next();

        assertThat(compare(before, after)).isNegative();
        assertThat(compare(after, afterAgain)).isNegative();
        assertThat(Uuids.instantOf(after)).isEqualTo(NOON);
    }

    @Test
    @DisplayName("a clock that moves on starts a new millisecond, still above the ids before it")
    void clockMovesOn() {
        SteppedClock clock = new SteppedClock(NOON);
        TimeOrderedIds ids = new TimeOrderedIds(clock);
        UUID first = ids.next();
        clock.now = NOON.plusMillis(1);
        UUID second = ids.next();

        assertThat(compare(first, second)).isNegative();
        assertThat(Uuids.instantOf(second)).isEqualTo(NOON.plusMillis(1));
    }

    @Test
    @DisplayName("threads sharing one generator get distinct ids, each thread's own ids rise, and the whole run is one rising sequence")
    void concurrentCallers() throws Exception {
        TimeOrderedIds ids = new TimeOrderedIds(new SteppedClock(NOON));
        int threads = 8;
        int perThread = 5000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<UUID>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Callable<List<UUID>> work = () -> {
                    List<UUID> mine = new ArrayList<>();
                    for (int i = 0; i < perThread; i++) {
                        mine.add(ids.next());
                    }
                    return mine;
                };
                futures.add(pool.submit(work));
            }
            Set<UUID> all = new HashSet<>();
            for (Future<List<UUID>> future : futures) {
                List<UUID> mine = future.get();
                assertThat(ascending(mine)).isTrue();
                all.addAll(mine);
            }
            assertThat(all).hasSize(threads * perThread);
            List<UUID> sorted = new ArrayList<>(all);
            sorted.sort(TimeOrderedIdsTest::compare);
            assertThat(Uuids.instantOf(sorted.getLast()).toEpochMilli() - NOON.toEpochMilli()).isLessThan(100);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the default generator of the application is this one, driven by the injected clock")
    void theDefaultGeneratorIsThisOne() {
        IdGenerator generator = new KernelConfiguration().idGenerator(Clock.fixed(NOON, ZoneId.of("UTC")));

        assertThat(generator).isInstanceOf(TimeOrderedIds.class);
        assertThat(Uuids.instantOf(generator.next())).isEqualTo(NOON);
    }
}
