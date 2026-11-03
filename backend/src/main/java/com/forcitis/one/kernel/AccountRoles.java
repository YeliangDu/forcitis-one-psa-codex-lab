package com.forcitis.one.kernel;

/**
 * Asks whether an enabled account holds a system role by its code, implemented by whoever owns roles (like {@link CallerPermissions}, a port so that
 * organization.workforce needs no edge to platform.identity). For the rare use case whose rule names a role and not only a permission.
 */
public interface AccountRoles {

    boolean holds(UserId account, String roleCode);
}
