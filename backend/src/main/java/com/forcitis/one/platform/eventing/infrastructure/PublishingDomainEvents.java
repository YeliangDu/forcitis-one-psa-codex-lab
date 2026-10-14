package com.forcitis.one.platform.eventing.infrastructure;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.EventEnvelopes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishing inside the use case's transaction is what writes the event publication registry row with the business change. */
@Component
class PublishingDomainEvents implements DomainEvents {

    private final EventEnvelopes envelopes;
    private final ApplicationEventPublisher publisher;

    PublishingDomainEvents(EventEnvelopes envelopes, ApplicationEventPublisher publisher) {
        this.envelopes = envelopes;
        this.publisher = publisher;
    }

    @Override
    public void publish(AggregateRoot aggregate) {
        aggregate.drainEvents(envelopes).forEach(publisher::publishEvent);
    }
}
