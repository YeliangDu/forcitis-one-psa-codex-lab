package com.forcitis.one.platform.eventing.infrastructure;

import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.platform.identity.api.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** A job or a handled event acts as SYSTEM; otherwise the signed-in user acts; with neither there is no actor and publishing fails. */
@Component
class ContextualEventEnvelopes implements EventEnvelopes {

    private final Clock clock;
    private final ObjectProvider<CurrentUser> currentUser;

    ContextualEventEnvelopes(Clock clock, ObjectProvider<CurrentUser> currentUser) {
        this.clock = clock;
        this.currentUser = currentUser;
    }

    @Override
    public EventEnvelope wrap(EventDraft draft) {
        Clock now = clock;
        Instant occurredAt = now.instant();
        UUID correlationId = WorkContext.correlationId().orElseGet(() -> Uuids.timeOrdered(now));
        ActorType actorType;
        String actorId;
        var system = WorkContext.systemActor();
        if (system.isPresent()) {
            actorType = ActorType.SYSTEM;
            actorId = SystemActors.idOf(system.get()).toString();
        } else {
            actorType = ActorType.USER;
            actorId = signedInUser().value().toString();
        }
        return new EventEnvelope(Uuids.timeOrdered(now), draft.eventType(), draft.schemaVersion(), occurredAt, draft.aggregateType(),
                draft.aggregateId(), draft.aggregateVersion(), correlationId, WorkContext.causationId().orElse(null),
                actorType, actorId, draft.legalEntityId(), draft.businessDate(), draft.source(), null, draft.relatedAggregates());
    }

    private UserId signedInUser() {
        CurrentUser user = currentUser.getIfAvailable();
        if (user == null) {
            throw new IllegalStateException("an event needs an actor: publish inside a signed-in request, a job or an event handler");
        }
        try {
            return user.userId();
        } catch (RuntimeException noSession) {
            throw new IllegalStateException("an event needs an actor: publish inside a signed-in request, a job or an event handler",
                    noSession);
        }
    }
}
