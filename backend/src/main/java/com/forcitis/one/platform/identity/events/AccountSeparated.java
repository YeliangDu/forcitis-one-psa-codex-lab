package com.forcitis.one.platform.identity.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

/** The account lost every role because its person left the company; what hangs on the account (an emergency account) follows from this fact. */
public record AccountSeparated(EventEnvelope envelope, UserId userId) implements DomainEvent {
}
