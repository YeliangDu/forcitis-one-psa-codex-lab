package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;

/**
 * A position was created (ADR-0068 item 4); effectiveDate is the first day of the new state and the envelope's businessDate. The envelope is about the
 * command (aggregateId is the command id, sequence 1): org units and positions keep no event counter, and the command entry is audited by its own record.
 */
@NotAudited(reason = "the org-structure command's entry is audited by its own record in the same transaction (organization.workforce.org-structure-command.entered)")
public record PositionCreated(EventEnvelope envelope, OrgStructureCommandId orgStructureCommandId, PositionId positionId, LocalDate effectiveDate) implements DomainEvent {
}
