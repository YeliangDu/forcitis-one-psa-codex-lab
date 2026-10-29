package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.kernel.CurrentUser;
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
    private final CachedSessionPrincipals principals;

    SessionCurrentUser(EmployeeLegalEntities employeeLegalEntities, AccountEmployees accountEmployees, BusinessZone zone, Clock clock,
            CachedSessionPrincipals principals) {
        this.principals = principals;
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

    /** In a session the employee was resolved with the session principal; a caller authenticated otherwise is looked up through identity. */
    @Override
    public EmployeeId employeeId() {
        UserId account = userId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof IdentityAuthentication identity) {
            EmployeeId employee = principals.get(account, identity.kindAt(clock.instant())).employeeId();
            if (employee == null) {
                throw new DomainException(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN, ErrorKind.UNAUTHENTICATED,
                        "the session names account " + account.value() + ", which does not exist");
            }
            return employee;
        }
        return accountEmployees.employeeOf(account).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_NOT_SIGNED_IN,
                ErrorKind.UNAUTHENTICATED, "the session names account " + account.value() + ", which does not exist"));
    }

    @Override
    public LegalEntityId legalEntityId() {
        return employeeLegalEntities.on(employeeId(), zone.today(clock));
    }
}
