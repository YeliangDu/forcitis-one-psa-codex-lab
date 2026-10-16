package com.forcitis.one.kernel.event;

import com.forcitis.one.fixture.doubles.CatalogueBusinessDates;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.forcitis.one.kernel.SystemActors;

/** Envelopes for unit tests of aggregates: fixed time, SYSTEM actor "test.envelope". */
public final class TestEnvelopes {
    public static final EventEnvelopes INSTANCE = checked -> {
        EventDraft draft = CatalogueBusinessDates.check(checked);
        return new EventEnvelope(UUID.randomUUID(), draft.eventType(),
            draft.schemaVersion(), Instant.parse("2026-10-14T02:00:00Z"), draft.aggregateType(), draft.aggregateId(),
            draft.aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, SystemActors.idOf("test.envelope"), draft.legalEntityId(),
            draft.businessDate(), null, draft.relatedAggregates());
    };

    /** A bare envelope for building an event by hand; unlike the aggregates' drafts it is not held to the catalogue's businessDate. */
    public static EventEnvelope of(String eventType, String aggregateType, UUID aggregateId) {
        return new EventEnvelope(UUID.randomUUID(), eventType, 1, Instant.parse("2026-10-14T02:00:00Z"), aggregateType, aggregateId, 1,
                UUID.randomUUID(), null, ActorType.SYSTEM, SystemActors.idOf("test.envelope"), null, null, null,
                List.of());
    }

    private TestEnvelopes() {
    }
}
