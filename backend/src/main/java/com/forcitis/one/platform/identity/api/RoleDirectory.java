package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

public interface RoleDirectory {

    Set<UserId> findRoleHolders(RoleCode roleCode);
}
