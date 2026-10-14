package com.forcitis.one.kernel.event;

import com.forcitis.one.kernel.AggregateRoot;

/** Hands what an aggregate recorded to the event publication registry, inside the use case's transaction. */
public interface DomainEvents {

    void publish(AggregateRoot aggregate);
}
