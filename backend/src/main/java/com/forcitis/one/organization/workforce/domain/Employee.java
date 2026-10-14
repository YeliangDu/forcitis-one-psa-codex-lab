package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.Objects;

/** Empty loginName means separated; no birthday (sensitive, not in the view). */
public record Employee(UserId userId, String employeeCode, String displayName, String loginName, String email,
                       Gender gender, WecomUserId wecomUserId, EmploymentStatus employmentStatus, boolean feeEarner) {
    public Employee {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(employeeCode, "employeeCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(employmentStatus, "employmentStatus");
    }

    public Employee withEmploymentStatus(EmploymentStatus newStatus) {
        return new Employee(userId, employeeCode, displayName, loginName, email, gender, wecomUserId, newStatus, feeEarner);
    }
}
