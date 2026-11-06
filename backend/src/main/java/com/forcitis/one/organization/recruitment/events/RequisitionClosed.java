package com.forcitis.one.organization.recruitment.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import java.time.Instant;

/** Ids and the reason kind only, no free text. The envelope's business date is the day of closing. */
public record RequisitionClosed(EventEnvelope envelope, RequisitionId requisitionId, CloseReasonKind closeReasonKind,
                                Instant closedAt) implements DomainEvent {
}
