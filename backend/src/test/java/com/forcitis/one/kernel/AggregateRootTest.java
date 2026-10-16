package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AggregateRootTest {

    record Changed(EventEnvelope envelope) implements DomainEvent {
    }

    static final class Counter extends AggregateRoot {
        Counter(long version, long eventSequence) {
            super(version, eventSequence);
        }

        void change() {
            record((envelopes, version) -> new Changed(envelopes.wrap(new EventDraft("test.counter.Changed", 1, "Counter", TestIds.uuid(1), version,
                    null, null, List.of()))));
        }
    }

    static final EventEnvelopes ENVELOPES = draft -> new EventEnvelope(UUID.randomUUID(), draft.eventType(), 1, Instant.EPOCH,
            draft.aggregateType(), draft.aggregateId(), draft.aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, SystemActors.idOf("test.envelope"),
            null, null, null, List.of());

    @Test
    @DisplayName("events recorded in one unit are numbered contiguously after the loaded event sequence; the lock version moves by one per save")
    void contiguousSequence() {
        Counter counter = new Counter(9, 4);
        counter.change();
        counter.change();
        assertThat(counter.versionAfterSave()).isEqualTo(10);
        assertThat(counter.eventSequenceAfterSave()).isEqualTo(6);

        List<DomainEvent> events = counter.drainEvents(ENVELOPES);

        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(5L, 6L);
        assertThat(counter.version()).isEqualTo(10);
        assertThat(counter.eventSequence()).isEqualTo(6);
        assertThat(counter.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("a save without events moves the optimistic version by one and leaves the event sequence alone")
    void saveWithoutEventsMovesTheVersion() {
        Counter counter = new Counter(3, 2);
        assertThat(counter.versionAfterSave()).isEqualTo(4);
        assertThat(counter.drainEvents(ENVELOPES)).isEmpty();
        assertThat(counter.version()).isEqualTo(4);
        assertThat(counter.eventSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("a negative version is refused")
    void negativeVersion() {
        assertThatThrownBy(() -> new Counter(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("business dates are cut in the business zone, not in UTC")
    void businessDateIsCutInShanghai() {
        BusinessZone zone = new BusinessZone(ZoneId.of("Asia/Shanghai"));
        assertThat(zone.dateOf(Instant.parse("2026-09-30T16:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(zone.dateOf(Instant.parse("2026-09-30T15:59:59Z"))).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(zone.today(Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"), ZoneOffset.UTC))).isEqualTo(LocalDate.of(2027, 1, 1));
    }
}
