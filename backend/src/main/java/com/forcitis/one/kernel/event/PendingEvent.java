package com.forcitis.one.kernel.event;

/** An event the aggregate has recorded; the envelope is added when the use case publishes it. */
@FunctionalInterface
public interface PendingEvent {

    DomainEvent seal(EventEnvelopes envelopes, long aggregateVersion);
}
