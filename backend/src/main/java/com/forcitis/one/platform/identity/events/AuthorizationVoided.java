package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;

/** A confirmed request was not applied: its base (the account version or a related role version) had moved since the request was made, or applying it was refused. */
@NotAudited(reason = "authorization.voided 同事务直写（#938）")
public record AuthorizationVoided(EventEnvelope envelope, AuthorizationRequestId requestId) implements DomainEvent {
}
