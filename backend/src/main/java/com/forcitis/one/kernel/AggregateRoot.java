package com.forcitis.one.kernel;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.kernel.event.PendingEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Two counters, never mixed: version is the optimistic lock and moves by one per save; eventSequence moves by one per event
 * and is the envelope's aggregateVersion (EVENT_ANALYTICS_FIELDS §6.3), so it has no gaps. Both are persisted.
 */
public abstract class AggregateRoot {

    private long version;
    private long eventSequence;
    private final List<PendingEvent> pending = new ArrayList<>();

    protected AggregateRoot(long version, long eventSequence) {
        if (version < 0 || eventSequence < 0) {
            throw new IllegalArgumentException("version and eventSequence must not be negative");
        }
        this.version = version;
        this.eventSequence = eventSequence;
    }

    /** Transitional: aggregates that do not persist eventSequence yet; remove once every aggregate stores it. */
    @Deprecated(forRemoval = true)
    protected AggregateRoot(long version) {
        this(version, version);
    }

    public final long version() {
        return version;
    }

    public final long eventSequence() {
        return eventSequence;
    }

    public final long versionAfterSave() {
        return version + 1;
    }

    public final long eventSequenceAfterSave() {
        return eventSequence + pending.size();
    }

    protected final void record(PendingEvent event) {
        pending.add(event);
    }

    public final boolean hasPendingEvents() {
        return !pending.isEmpty();
    }

    public final List<DomainEvent> drainEvents(EventEnvelopes envelopes) {
        List<DomainEvent> sealed = new ArrayList<>(pending.size());
        for (PendingEvent event : pending) {
            sealed.add(event.seal(envelopes, ++eventSequence));
        }
        version = version + 1;
        pending.clear();
        return sealed;
    }
}
