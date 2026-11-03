package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import org.springframework.stereotype.Component;

/** Whether an account holds a sensitive role now, the premise of every sensitive-role review (ADR-0032 decision 5, ADR-0044 decision 2 item 3). */
@Component
class HoldsSensitiveRole {

    private final AccountRepository accounts;
    private final RoleRepository roles;

    HoldsSensitiveRole(AccountRepository accounts, RoleRepository roles) {
        this.accounts = accounts;
        this.roles = roles;
    }

    boolean test(UserId user) {
        return accounts.openGrants(user).stream().anyMatch(grant -> roles.findById(grant.roleId()).map(SensitivePermissions::roleIsSensitive).orElse(false));
    }
}
