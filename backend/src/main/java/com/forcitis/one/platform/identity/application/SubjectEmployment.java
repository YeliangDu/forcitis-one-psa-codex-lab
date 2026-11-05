package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import org.springframework.stereotype.Component;

/**
 * Whether the person behind an account is employed today, read from the directory as the other identity checks read it (#1322). It looks at the
 * directory, not at the roles: the person separated and the separation event was published, but the listener has not yet closed the grants, so the
 * account may still look as before; and a person without roles has nothing to close and would otherwise never show as separated.
 */
@Component
class SubjectEmployment {

    private final OrgDirectory directory;
    private final AccountRepository accounts;

    SubjectEmployment(OrgDirectory directory, AccountRepository accounts) {
        this.accounts = accounts;
        this.directory = directory;
    }

    boolean isEmployed(UserAccount account) {
        return directory.employmentOf(account.employeeId()) == EmploymentVerdict.EMPLOYED;
    }

    /** False also for a user without an account: nobody who cannot be placed in the directory confirms or has a request confirmed. */
    boolean isEmployed(UserId user) {
        return accounts.find(user).map(this::isEmployed).orElse(false);
    }
}
