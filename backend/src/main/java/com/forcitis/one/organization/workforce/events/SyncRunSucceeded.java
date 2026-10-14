package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.organization.workforce.api.SyncRunId;

@NotAudited(reason = "a run every five minutes would bury the audit trail; sync_runs is its record, and held, failed and released runs are audited")
public record SyncRunSucceeded(EventEnvelope envelope, SyncRunId syncRunId) implements DomainEvent {
}
