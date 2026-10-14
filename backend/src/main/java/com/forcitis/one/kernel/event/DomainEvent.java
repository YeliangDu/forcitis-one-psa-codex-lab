package com.forcitis.one.kernel.event;

/** Implemented by every domain event record (in a module's events package). */
public interface DomainEvent {

    EventEnvelope envelope();
}
