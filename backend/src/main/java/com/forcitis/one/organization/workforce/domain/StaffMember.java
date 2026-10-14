package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Objects;

public record StaffMember(Employee employee, Assignment assignment) {
    public StaffMember {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(assignment, "assignment");
    }

    public UserId userId() {
        return employee.userId();
    }
}
