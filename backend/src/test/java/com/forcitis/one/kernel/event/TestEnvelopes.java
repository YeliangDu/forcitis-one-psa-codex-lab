package com.forcitis.one.kernel.event;

import java.time.Instant;
import java.util.UUID;

/** Envelopes for unit tests of aggregates: fixed time, SYSTEM actor "test". */
public final class TestEnvelopes {
    public static final EventEnvelopes INSTANCE = draft -> new EventEnvelope(UUID.randomUUID(), draft.eventType(),
            draft.schemaVersion(), Instant.parse("2026-10-14T02:00:00Z"), draft.aggregateType(), draft.aggregateId(),
            draft.aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, "test", draft.legalEntityId(),
            draft.businessDate(), draft.source(), null, draft.relatedAggregates());

    public static EventEnvelope of(String eventType, String aggregateType, String aggregateId) {
        return INSTANCE.wrap(new EventDraft(eventType, 1, aggregateType, aggregateId, 1, null, null,
                new EventSource(SourceSystem.ONE, null), java.util.List.of()));
    }

    private TestEnvelopes() {
    }
}
