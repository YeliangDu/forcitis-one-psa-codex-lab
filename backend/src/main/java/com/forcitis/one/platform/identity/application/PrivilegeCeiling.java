package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Nobody hands out more than they hold. An administrator may also hand out permissions they do not hold, but never a sensitive one
 * or an administrative role: those wait for a second person (AuthorizationGate), so no one grants themselves more alone.
 */
@Component
class PrivilegeCeiling {

    private final CurrentUser currentUser;
    private final AccountRepository accounts;

    PrivilegeCeiling(CurrentUser currentUser, AccountRepository accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    /** Whether this person may even ask for a sensitive change: only an administrator asks for what they do not hold, and only they ask for an administrative role. */
    boolean mayRequest(Set<String> permissionCodes, boolean administrativeRole) {
        AccessProfile caller = AccessProfile.of(accounts.findEnabledRoles(currentUser.userId()));
        return caller.isAdministrator() || (!administrativeRole && caller.permissionCodes().containsAll(permissionCodes));
    }

    boolean mayGrant(Set<String> permissionCodes, boolean administrativeRole) {
        AccessProfile caller = AccessProfile.of(accounts.findEnabledRoles(currentUser.userId()));
        if (administrativeRole) {
            return false;
        }
        return caller.permissionCodes().containsAll(permissionCodes)
                || (caller.isAdministrator() && SensitivePermissions.within(permissionCodes).isEmpty());
    }
}
