package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Nobody may hand out more than they hold: someone who can edit roles must not be able to give themselves (or anybody)
 * permissions they lack, or an administrator role. Administrators are exempt.
 */
@Component
class PrivilegeCeiling {

    private final CurrentUser currentUser;
    private final AccountRepository accounts;

    PrivilegeCeiling(CurrentUser currentUser, AccountRepository accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    boolean mayGrant(Set<String> permissionCodes, boolean administrativeRole) {
        AccessProfile caller = AccessProfile.of(accounts.findEnabledRoles(currentUser.userId()));
        if (caller.isAdministrator()) {
            return true;
        }
        return !administrativeRole && caller.permissionCodes().containsAll(permissionCodes);
    }
}
