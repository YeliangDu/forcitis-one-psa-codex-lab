package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import java.util.Objects;

/** feeEarner is the source HR classification only; billability is decided by the new system (§9a). */
public record EmployeeProfile(UserId userId, String name, OrgUnitId orgUnitId, WecomUserId wecomUserId,
                              EmploymentStatus employmentStatus, boolean feeEarner) {
    public EmployeeProfile {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(employmentStatus, "employmentStatus");
    }
}
