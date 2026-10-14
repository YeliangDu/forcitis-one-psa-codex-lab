package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.Reasoned;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.SyncRunId;

/** releasedByRunId is the run that executed in its place; reason is what the administrator gave, null when the next run released it. */
public record SyncRunReleased(EventEnvelope envelope, SyncRunId syncRunId, SyncRunId releasedByRunId, Reason reason)
        implements DomainEvent, Reasoned {
}
