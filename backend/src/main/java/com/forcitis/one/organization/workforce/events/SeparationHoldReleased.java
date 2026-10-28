package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A held separation was released (ADR-0068 item 2.3) and is carried out at its original effectiveDate, which is the envelope's businessDate.
 * releasedBy is the account that released it in the interface; null when the two-person operations command released it, whose operator and
 * witness are only audited (ADR-0068 item 2.4).
 */
public record SeparationHoldReleased(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
                                     SeparationHoldId separationHoldId, @Nullable UserId releasedBy) implements DomainEvent {
}
