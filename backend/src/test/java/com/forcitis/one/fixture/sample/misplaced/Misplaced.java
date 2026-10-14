package com.forcitis.one.fixture.sample.misplaced;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

/** A domain event outside any events package. */
public record Misplaced(EventEnvelope envelope) implements DomainEvent {
}
