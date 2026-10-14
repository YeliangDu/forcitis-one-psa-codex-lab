package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;

public record AuthorizationRejected(EventEnvelope envelope, AuthorizationRequestId requestId, UserId rejectedBy) implements DomainEvent {
}
