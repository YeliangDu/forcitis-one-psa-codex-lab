package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;

/** The root a HIRE of a new person writes: the person is employed from the start, whatever the day the hire takes effect (the versions say when). */
public record NewEmployee(EmployeeId employeeId, String employeeCode, PersonalProfile profile, boolean feeEarner) {

    public EmploymentStatus employmentStatus() {
        return EmploymentStatus.EMPLOYED;
    }
}
