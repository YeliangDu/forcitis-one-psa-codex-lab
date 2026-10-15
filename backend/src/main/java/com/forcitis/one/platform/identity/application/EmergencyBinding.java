package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** An emergency account is bound to a person whose account is enabled and who holds super_admin now (ADR-0027 section 1). */
@Component
class EmergencyBinding {

    private final AccountRepository accounts;
    private final EmergencyAdminRepository admins;

    EmergencyBinding(AccountRepository accounts, EmergencyAdminRepository admins) {
        this.accounts = accounts;
        this.admins = admins;
    }

    void requireValid(UserId person) {
        Optional<UserAccount> account = accounts.find(person);
        if (account.isEmpty() || !account.get().isEnabled() || !AccessProfile.of(accounts.findEnabledRoles(person)).isAdministrator()) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID, ErrorKind.UNPROCESSABLE,
                    "user " + person.value() + " has no enabled account that holds super_admin");
        }
    }

    /** The account of the employee an operations command names, when the command is about an emergency account that must exist already. */
    UserId boundAccount(EmployeeId employee) {
        return accounts.findByEmployee(employee).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND,
                ErrorKind.NOT_FOUND, "employee " + employee.value() + " has no account and so no emergency account"));
    }

    EmergencyAdmin require(UserId person) {
        return admins.findByUserId(person).orElseThrow(() -> new DomainException(IdentityError.PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND,
                ErrorKind.NOT_FOUND, "user " + person.value() + " has no emergency account"));
    }

    int usableCount() {
        return (int) admins.findAll().stream().filter(EmergencyAdmin::enabled).filter(a -> {
            Optional<UserAccount> account = accounts.find(a.userId());
            return account.isPresent() && account.get().isEnabled()
                    && AccessProfile.of(accounts.findEnabledRoles(a.userId())).isAdministrator();
        }).count();
    }
}
