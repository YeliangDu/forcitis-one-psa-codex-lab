package com.forcitis.one.kernel.event;

import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * eventId is a UUIDv7 and the consumers' idempotency key. aggregateVersion is the per-aggregate event sequence kept on the
 * aggregate. businessDate is cut in Asia/Shanghai; AsyncAPI declares which date an event type uses. accountingPeriod is set
 * only by the posting pipeline. actorId never carries a name. relatedAggregates lists the other aggregates changed in the
 * same transaction.
 */
public record EventEnvelope(UUID eventId, String eventType, int schemaVersion, Instant occurredAt,
                            String aggregateType, UUID aggregateId, long aggregateVersion, UUID correlationId,
                            @Nullable UUID causationId, ActorType actorType, UUID actorId, @Nullable LegalEntityId legalEntityId,
                            @Nullable LocalDate businessDate, @Nullable AccountingPeriod accountingPeriod,
                            List<AggregateReference> relatedAggregates) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(actorId, "actorId");
        relatedAggregates = List.copyOf(relatedAggregates);
    }
}
