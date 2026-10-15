package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.EmployeeId;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** authorities are empty unless ACTIVE; employeeId is the employee behind the account, null only when there is no account. */
public record SessionPrincipal(Eligibility eligibility, Set<String> authorities, @Nullable EmployeeId employeeId) {

    public SessionPrincipal {
        authorities = Set.copyOf(authorities);
    }

    public boolean isActive() {
        return eligibility == Eligibility.ACTIVE;
    }
}
