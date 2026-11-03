package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.RoleHolderAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ListRoleHolders implements RoleHolderAccounts {

    private final AccountRepository accounts;

    ListRoleHolders(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserId> enabledHolders(String roleCode) {
        return accounts.findEnabledHolders(new RoleCode(roleCode));
    }
}
