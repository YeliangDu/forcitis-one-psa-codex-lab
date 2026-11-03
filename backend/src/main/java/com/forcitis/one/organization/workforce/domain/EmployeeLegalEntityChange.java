package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.workforce.events.EmployeeLegalEntityChanged;
import java.util.List;

/**
 * An org-structure command took effect and changed one employee's cost legal entity (note of #910 section 5 rule 6). It only carries the event: the envelope is about the
 * employee, so the sequence continues the employee's own event count, which the caller saves after publishing.
 */
public final class EmployeeLegalEntityChange extends AggregateRoot {

    private EmployeeLegalEntityChange(long employeeEventSequence) {
        super(0, employeeEventSequence);
    }

    public static EmployeeLegalEntityChange of(OrgStructureCommand command, OrgCountersignBasis basis, long employeeEventSequence) {
        EmployeeLegalEntityChange change = new EmployeeLegalEntityChange(employeeEventSequence);
        EmployeeId employee = basis.employeeId();
        change.record((envelopes, sequence) -> new EmployeeLegalEntityChanged(envelopes.wrap(new EventDraft("organization.workforce.EmployeeLegalEntityChanged", 1, "Employee",
                employee.value(), sequence, basis.next(), command.effectiveDate(), List.of())), null, command.id(), employee, command.effectiveDate(), basis.previous(),
                basis.next()));
        return change;
    }
}
