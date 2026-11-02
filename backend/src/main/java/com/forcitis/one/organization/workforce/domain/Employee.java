package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.Gender;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Empty loginName means separated; no birthday (sensitive, not in the view). */
public record Employee(EmployeeId employeeId, String employeeCode, String displayName, @Nullable String loginName, @Nullable String email,
                       @Nullable Gender gender, @Nullable WecomUserId wecomUserId, EmploymentStatus employmentStatus, boolean feeEarner) {
    public Employee {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(employeeCode, "employeeCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(employmentStatus, "employmentStatus");
    }

    public Employee withEmployeeCode(String newCode) {
        return new Employee(employeeId, newCode, displayName, loginName, email, gender, wecomUserId, employmentStatus, feeEarner);
    }

    public Employee withEmploymentStatus(EmploymentStatus newStatus) {
        return new Employee(employeeId, employeeCode, displayName, loginName, email, gender, wecomUserId, newStatus, feeEarner);
    }
}
