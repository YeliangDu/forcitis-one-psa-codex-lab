package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
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
        Counter(long version) {
            super(version);
        }

        void change() {
            record((envelopes, version) -> new Changed(envelopes.wrap(new EventDraft("test.counter.Changed", 1, "Counter", "1", version,
                    null, null, new EventSource(SourceSystem.ONE, null), List.of()))));
        }
    }

    static final EventEnvelopes ENVELOPES = draft -> new EventEnvelope(UUID.randomUUID(), draft.eventType(), 1, Instant.EPOCH,
            draft.aggregateType(), draft.aggregateId(), draft.aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, "test",
            null, null, draft.source(), null, List.of());

    @Test
    @DisplayName("events recorded in one unit are numbered contiguously after the loaded version and the version advances by their number")
    void contiguousSequence() {
        Counter counter = new Counter(4);
        counter.change();
        counter.change();
        assertThat(counter.versionAfterSave()).isEqualTo(6);

        List<DomainEvent> events = counter.drainEvents(ENVELOPES);

        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(5L, 6L);
        assertThat(counter.version()).isEqualTo(6);
        assertThat(counter.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("a save without events still moves the optimistic version by one")
    void saveWithoutEventsMovesTheVersion() {
        Counter counter = new Counter(3);
        assertThat(counter.versionAfterSave()).isEqualTo(4);
        assertThat(counter.drainEvents(ENVELOPES)).isEmpty();
        assertThat(counter.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("a negative version is refused")
    void negativeVersion() {
        assertThatThrownBy(() -> new Counter(-1)).isInstanceOf(IllegalArgumentException.class);
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
