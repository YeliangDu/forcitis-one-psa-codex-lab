package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

/** Who holds a role; only enabled accounts of employed people (RULE-343/349). Used by approver resolution. */
public interface RoleDirectory {

    Set<UserId> findRoleHolders(RoleCode roleCode);
}
