package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;

public interface EmployeeExistence {
    /** True when the employees table has a row for the id: no assignment and no employment are required, unlike {@link OrgDirectory#findEmployee}. */
    boolean exists(EmployeeId employeeId);
}
