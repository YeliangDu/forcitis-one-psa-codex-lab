package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.SyncRunId;

/** releasedByRunId is the run that executed in its place. */
public record SyncRunReleased(EventEnvelope envelope, SyncRunId syncRunId, SyncRunId releasedByRunId) implements DomainEvent {
}
