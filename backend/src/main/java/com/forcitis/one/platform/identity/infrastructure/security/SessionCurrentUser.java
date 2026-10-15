package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.time.Clock;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SessionCurrentUser implements CurrentUser {
    private final EmployeeLegalEntities employeeLegalEntities;
    private final AccountEmployees accountEmployees;
    private final BusinessZone zone;
    private final Clock clock;

    SessionCurrentUser(EmployeeLegalEntities employeeLegalEntities, AccountEmployees accountEmployees, BusinessZone zone, Clock clock) {
        this.employeeLegalEntities = employeeLegalEntities;
        this.accountEmployees = accountEmployees;
        this.zone = zone;
        this.clock = clock;
    }

    @Override
    public UserId userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN, ErrorKind.UNAUTHENTICATED, "no signed-in user");
        }
        try {
            return UserId.parse(authentication.getName());
        } catch (IllegalArgumentException broken) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN, ErrorKind.UNAUTHENTICATED,
                    "the session principal is not an account id");
        }
    }

    @Override
    public EmployeeId employeeId() {
        UserId account = userId();
        return accountEmployees.employeeOf(account).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN,
                ErrorKind.UNAUTHENTICATED, "the session names account " + account.value() + ", which does not exist"));
    }

    @Override
    public LegalEntityId legalEntityId() {
        return employeeLegalEntities.on(employeeId(), zone.today(clock));
    }
}
