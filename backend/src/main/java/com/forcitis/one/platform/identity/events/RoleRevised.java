package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.RoleId;

public record RoleRevised(EventEnvelope envelope, RoleId roleId) implements DomainEvent {
}
