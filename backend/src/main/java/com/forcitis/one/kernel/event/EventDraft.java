package com.forcitis.one.kernel.event;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What an aggregate knows when it records an event. eventType is <subdomain>.<bc>.<EventName>. legalEntityId is null only
 * for platform-level aggregates; businessDate is null only for events that never become a business fact.
 */
public record EventDraft(String eventType, int schemaVersion, String aggregateType, String aggregateId, long aggregateVersion,
                         LegalEntityId legalEntityId, LocalDate businessDate) {

    public EventDraft {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion starts at 1");
        }
    }
}
