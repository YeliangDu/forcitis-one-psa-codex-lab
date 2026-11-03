package com.forcitis.one.kernel;

import java.util.Set;

/** Lists the enabled accounts holding a system role by its code, implemented by whoever owns roles (a port, like {@link AccountRoles}); employment is the caller's to check. */
public interface RoleHolders {

    Set<UserId> enabledHolders(String roleCode);
}
