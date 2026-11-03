package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.events.EmployeeLegalEntityChanged;
import java.time.LocalDate;
import java.util.List;

/**
 * An org-structure command took effect and changed one employee's cost legal entity (the org-structure commands design note section 5 rule 6). It only carries the event: the envelope is about the
 * employee, so the sequence continues the employee's own event count, which the caller saves after publishing.
 */
public final class EmployeeLegalEntityChange extends AggregateRoot {

    private final EmployeeId id;

    private EmployeeLegalEntityChange(EmployeeId id, long employeeEventSequence) {
        super(0, employeeEventSequence);
        this.id = id;
    }

    public static EmployeeLegalEntityChange of(OrgStructureCommandId command, LocalDate effectiveDate, OrgCountersignBasis basis, long employeeEventSequence) {
        EmployeeLegalEntityChange change = new EmployeeLegalEntityChange(basis.employeeId(), employeeEventSequence);
        change.record((envelopes, sequence) -> new EmployeeLegalEntityChanged(envelopes.wrap(new EventDraft("organization.workforce.EmployeeLegalEntityChanged", 1, "Employee",
                change.id.value(), sequence, basis.next(), effectiveDate, List.of())), null, command, change.id, effectiveDate, basis.previous(), basis.next()));
        return change;
    }
}
