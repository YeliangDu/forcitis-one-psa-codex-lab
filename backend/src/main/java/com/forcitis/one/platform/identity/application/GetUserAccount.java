package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The version it returns is the one a role replacement, enabling or disabling must be based on. */
@Service
public class GetUserAccount {

    private final AccountRepository accounts;

    GetUserAccount(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public UserAccountView execute(UserId userId) {
        UserAccount account = accounts.find(userId).orElse(null);
        if (account == null) {
            throw IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account " + userId.value());
        }
        return new UserAccountView(userId, account.status().name(),
                accounts.findAssignedRoleCodes(account.userId()).stream().map(RoleCode::value).sorted().toList(), account.version());
    }
}
