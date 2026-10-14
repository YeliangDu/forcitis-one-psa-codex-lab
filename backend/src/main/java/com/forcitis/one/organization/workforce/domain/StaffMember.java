package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;

/** An employee together with the assignment (org unit, position, direct leader): the unit the sync compares and writes. */
public record StaffMember(Employee employee, Assignment assignment) {

    public StaffMember {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(assignment, "assignment");
    }

    public UserId userId() {
        return employee.userId();
    }
}
