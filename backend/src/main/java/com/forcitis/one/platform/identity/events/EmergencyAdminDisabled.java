package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause;

public record EmergencyAdminDisabled(EventEnvelope envelope, UserId userId, EmergencyAdminDisableCause cause) implements DomainEvent {
}
