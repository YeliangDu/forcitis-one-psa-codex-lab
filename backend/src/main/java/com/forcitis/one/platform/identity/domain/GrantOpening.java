package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.RoleId;
import org.jspecify.annotations.Nullable;

/** One grant to open; grantedBy exists exactly for MANUAL and the request only for a MANUAL grant confirmed by a second person. */
public record GrantOpening(AccountRoleGrantId id, RoleId roleId, GrantChannel channel, @Nullable UserId grantedBy, @Nullable AuthorizationRequestId request,
                           AuditEntryId audit) {
}
