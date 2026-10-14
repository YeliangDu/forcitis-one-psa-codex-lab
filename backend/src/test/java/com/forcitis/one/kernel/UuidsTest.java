package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UuidsTest {

    @Test
    void timeOrderedIdIsVersion7WithTheClockMillisecondsAndSortsByTime() {
        Instant first = Instant.parse("2026-10-14T10:00:00.123Z");
        UUID early = Uuids.timeOrdered(Clock.fixed(first, ZoneOffset.UTC));
        UUID late = Uuids.timeOrdered(Clock.fixed(first.plusMillis(1), ZoneOffset.UTC));

        assertThat(early.version()).isEqualTo(7);
        assertThat(early.variant()).isEqualTo(2);
        assertThat(Uuids.instantOf(early)).isEqualTo(first);
        assertThat(early.toString().compareTo(late.toString())).isNegative();
    }

    @Test
    void idsOfTheSameMillisecondDiffer() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
        assertThat(Uuids.timeOrdered(clock)).isNotEqualTo(Uuids.timeOrdered(clock));
    }

    @Test
    void idsOfTheSameMillisecondSortInTheOrderTheyWereMade() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00.500Z"), ZoneOffset.UTC);
        List<UUID> made = IntStream.range(0, 1000).mapToObj(i -> Uuids.timeOrdered(clock)).toList();

        assertThat(made).isSorted();
        assertThat(made).doesNotHaveDuplicates();
        assertThat(made).allSatisfy(id -> assertThat(Uuids.instantOf(id)).isEqualTo(Instant.parse("2026-10-14T10:00:00.500Z")));
    }

    @Test
    void theDefaultGeneratorMakesTimeOrderedIdsFromTheInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
        UUID id = new KernelConfiguration().idGenerator(clock).next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(Uuids.instantOf(id)).isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
    }

    @Test
    void anotherVersionHasNoInstant() {
        assertThatThrownBy(() -> Uuids.instantOf(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }
}
