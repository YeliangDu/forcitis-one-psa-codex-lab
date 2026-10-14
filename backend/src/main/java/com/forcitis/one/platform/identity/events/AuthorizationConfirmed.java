package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;

/** The second person agreed; the change itself is applied by a listener, in its own transaction. */
public record AuthorizationConfirmed(EventEnvelope envelope, AuthorizationRequestId requestId, UserId confirmedBy) implements DomainEvent {
}
