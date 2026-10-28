package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import java.time.LocalDate;
import java.util.List;

/**
 * A transfer took effect (ADR-0068 item 1.9); effectiveDate is the first day in the new assignment and the envelope's businessDate.
 * triggerFieldKinds are the assignment fields it changed (empty when only the manager or the fee-earner flag changed); the departments are
 * those of the day before and of effectiveDate, the same when only the position changed.
 */
public record EmployeeTransferred(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate effectiveDate,
                                  List<TriggerFieldKind> triggerFieldKinds, OrgUnitId previousOrgUnitId, OrgUnitId newOrgUnitId)
        implements DomainEvent {

    public EmployeeTransferred {
        triggerFieldKinds = List.copyOf(triggerFieldKinds);
    }
}
