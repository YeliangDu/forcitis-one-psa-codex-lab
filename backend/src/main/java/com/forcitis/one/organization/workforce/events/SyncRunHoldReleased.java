package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.HoldKind;
import com.forcitis.one.organization.workforce.api.SyncRunId;

/** One kind of hold was lifted; the run stays held while another kind is still there. */
public record SyncRunHoldReleased(EventEnvelope envelope, SyncRunId syncRunId, HoldKind holdKind, UserId releasedBy) implements DomainEvent {
}
