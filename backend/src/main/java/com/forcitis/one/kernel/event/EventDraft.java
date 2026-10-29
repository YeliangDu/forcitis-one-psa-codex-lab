package com.forcitis.one.kernel.event;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What an aggregate knows when it records an event. eventType is <subdomain>.<bc>.<EventName>. legalEntityId is null only
 * for platform-level aggregates; businessDate is null only for events that are not a dated business milestone.
 */
public record EventDraft(String eventType, int schemaVersion, String aggregateType, UUID aggregateId, long aggregateVersion,
                         @Nullable LegalEntityId legalEntityId, @Nullable LocalDate businessDate,
                         List<AggregateReference> relatedAggregates) {

    public EventDraft {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        relatedAggregates = List.copyOf(relatedAggregates);
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion starts at 1");
        }
    }
}
