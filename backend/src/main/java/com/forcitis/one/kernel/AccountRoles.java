package com.forcitis.one.kernel;

import java.util.Set;

/**
 * Asks whether an enabled account holds a system role by its code, implemented by whoever owns roles (like {@link CallerPermissions}, a port so that
 * organization.workforce needs no edge to platform.identity). For the rare use case whose rule names a role and not only a permission.
 */
public interface AccountRoles {

    boolean holds(UserId account, String roleCode);

    /** The enabled accounts holding the role; employment is the caller's to check. */
    Set<UserId> enabledHolders(String roleCode);
}
