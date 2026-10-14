package com.forcitis.one.kernel;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.kernel.event.PendingEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * version is the optimistic-lock counter and doubles as the per-aggregate event sequence: an event recorded on the n-th place after
 * the loaded version carries aggregateVersion = loaded + n, and a save moves the stored version to loaded + max(1, events).
 */
public abstract class AggregateRoot {

    private long version;
    private final List<PendingEvent> pending = new ArrayList<>();

    protected AggregateRoot(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public final long version() {
        return version;
    }

    public final long versionAfterSave() {
        return version + Math.max(1, pending.size());
    }

    protected final void record(PendingEvent event) {
        pending.add(event);
    }

    public final boolean hasPendingEvents() {
        return !pending.isEmpty();
    }

    /** Seals and clears the recorded events and advances the version to what the save stored. */
    public final List<DomainEvent> drainEvents(EventEnvelopes envelopes) {
        List<DomainEvent> sealed = new ArrayList<>(pending.size());
        long sequence = version;
        for (PendingEvent event : pending) {
            sealed.add(event.seal(envelopes, ++sequence));
        }
        version = version + Math.max(1, pending.size());
        pending.clear();
        return sealed;
    }
}
