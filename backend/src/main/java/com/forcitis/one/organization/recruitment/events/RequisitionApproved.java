package com.forcitis.one.organization.recruitment.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.Instant;

/** Ids only, no free text. The envelope's business date is the day of approval. */
public record RequisitionApproved(EventEnvelope envelope, RequisitionId requisitionId, OrgUnitId orgUnitId, PositionId positionId,
                                  Instant approvedAt) implements DomainEvent {
}
