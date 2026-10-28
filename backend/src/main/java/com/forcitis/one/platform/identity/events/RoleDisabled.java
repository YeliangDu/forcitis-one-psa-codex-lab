package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;

/** roleVersionId: the version current after the change. */
public record RoleDisabled(EventEnvelope envelope, RoleId roleId, RoleVersionId roleVersionId) implements DomainEvent {
}
