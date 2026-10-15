package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import java.util.Objects;

public record StaffEmployee(Employee employee, Assignment assignment) {
    public StaffEmployee {
        Objects.requireNonNull(employee, "employee");
        Objects.requireNonNull(assignment, "assignment");
    }

    public EmployeeId employeeId() {
        return employee.employeeId();
    }
}
