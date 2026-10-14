package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.SyncRunId;

public record SyncRunHeld(EventEnvelope envelope, SyncRunId syncRunId) implements DomainEvent {
}
