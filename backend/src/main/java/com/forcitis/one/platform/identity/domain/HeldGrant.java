package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;

/** A grant that is open now. */
public record HeldGrant(AccountRoleGrantId id, RoleId roleId, RoleCode roleCode) {
}
