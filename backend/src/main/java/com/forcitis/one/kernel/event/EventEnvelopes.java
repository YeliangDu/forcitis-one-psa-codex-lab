package com.forcitis.one.kernel.event;

/** Completes a draft with id, clock time, correlation / causation and actor of the current unit of work. */
public interface EventEnvelopes {

    EventEnvelope wrap(EventDraft draft);
}
