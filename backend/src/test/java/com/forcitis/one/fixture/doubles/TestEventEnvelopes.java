package com.forcitis.one.fixture.doubles;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import java.time.Instant;
import java.util.UUID;

/** Test stand-in for the real envelope factory: fresh ids, the system clock, a fixed test actor. */
public class TestEventEnvelopes implements EventEnvelopes {

    @Override
    public EventEnvelope wrap(EventDraft unchecked) {
        EventDraft draft = CatalogueBusinessDates.check(unchecked);
        return new EventEnvelope(UUID.randomUUID(), draft.eventType(), draft.schemaVersion(), BusinessDay.instant(), draft.aggregateType(),
                draft.aggregateId(), draft.aggregateVersion(), UUID.randomUUID(), null, ActorType.USER, TestAccounts.account(1).value(), draft.legalEntityId(),
                draft.businessDate(), null, draft.relatedAggregates());
    }
}
