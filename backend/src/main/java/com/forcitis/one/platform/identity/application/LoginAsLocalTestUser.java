package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Card #1478: a developer's own machine only (profile local; the bean does not exist in any other profile). The caller picks one of the accounts that exist and
 * gets an ordinary session for it: the same admission as every other login (employed, account enabled), then the same session start, so the person has exactly
 * the grants and data scope the account holds. Nothing here grants, widens or skips a check.
 */
@Service
@Profile("local")
public class LoginAsLocalTestUser {

    private final AccountRepository accounts;
    private final OrgDirectory directory;
    private final LoginAdmission admission;

    LoginAsLocalTestUser(AccountRepository accounts, OrgDirectory directory, LoginAdmission admission) {
        this.accounts = accounts;
        this.directory = directory;
        this.admission = admission;
    }

    @Transactional
    public LoginResult execute(UUID picked) {
        LoginMethod method = LoginMethod.LOCAL_TEST;
        UserId userId;
        try {
            userId = new UserId(picked);
        } catch (IllegalArgumentException notAnAccountId) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND, method, null, "not an account id");
        }
        Optional<EmployeeProfile> employee = accounts.findEmployee(userId).flatMap(directory::findEmployee);
        if (employee.isEmpty()) {
            return admission.reject(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND, method, null, "no such account or employee");
        }
        Optional<IdentityError> refused = admission.check(employee.get());
        if (refused.isPresent()) {
            return admission.reject(refused.get(), method, userId, refused.get().name());
        }
        return admission.complete(userId, method);
    }
}
