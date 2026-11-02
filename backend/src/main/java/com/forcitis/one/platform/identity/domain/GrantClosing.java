package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import org.jspecify.annotations.Nullable;

/** One open grant to close; revokedBy exists exactly for MANUAL, the separation command exactly for SEPARATION. */
public record GrantClosing(AccountRoleGrantId id, RevocationCause cause, @Nullable UserId revokedBy, @Nullable AuthorizationRequestId request,
                           @Nullable PersonnelCommandId separationCommand, AuditEntryId audit) {
}
