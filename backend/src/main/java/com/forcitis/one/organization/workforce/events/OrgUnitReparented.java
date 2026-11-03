package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.time.LocalDate;

/**
 * An org unit moved under a new parent (ADR-0068 item 4); effectiveDate is the first day of the new state and the envelope's businessDate. The envelope is about the
 * command (aggregateId is the command id, sequence 1): org units and positions keep no event counter, and the command entry is audited by its own record.
 */
@NotAudited(reason = "the org-structure command's entry is audited by its own record in the same transaction (organization.workforce.org-structure-command.entered)")
public record OrgUnitReparented(EventEnvelope envelope, OrgStructureCommandId orgStructureCommandId, OrgUnitId orgUnitId, OrgUnitId newParentOrgUnitId, LocalDate effectiveDate) implements DomainEvent {
}
