package com.forcitis.one.kernel.event;

import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * eventId is a UUIDv7 and the consumers' idempotency key. accountingPeriod is set only by the posting pipeline
 * (accounting.period decides it); producers never fill it. actorId never carries a name.
 */
public record EventEnvelope(UUID eventId, String eventType, int schemaVersion, Instant occurredAt, Instant recordedAt,
                            String aggregateType, String aggregateId, long aggregateVersion, UUID correlationId,
                            UUID causationId, ActorType actorType, String actorId, LegalEntityId legalEntityId,
                            LocalDate businessDate, EventSource source, AccountingPeriod accountingPeriod) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(source, "source");
    }
}
