package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.UserId;

public record AccountEnabled(EventEnvelope envelope, UserId userId) implements DomainEvent {
}
