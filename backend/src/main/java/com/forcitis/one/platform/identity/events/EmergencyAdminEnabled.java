package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

public record EmergencyAdminEnabled(EventEnvelope envelope, UserId userId) implements DomainEvent {
}
