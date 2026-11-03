package com.forcitis.one.kernel;

import java.util.Set;

/** Lists the enabled accounts holding a system role by its code, implemented by whoever owns roles (a port, like {@link AccountRoles}); whether the holders are employed is not checked here (separation closes a person's roles, unpaid leave does not). */
public interface RoleHolderAccounts {

    Set<UserId> enabledHolders(String roleCode);
}
