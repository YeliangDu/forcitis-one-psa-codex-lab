package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import org.jspecify.annotations.Nullable;

/** What shows an item was adjusted: exactly one of an authorization request or an audit entry of the grant (F-1 card invariant 28). */
public record AdjustmentReference(@Nullable AuthorizationRequestId authorizationRequest, @Nullable AuditEntryId auditEntry) {

    public AdjustmentReference {
        if ((authorizationRequest == null) == (auditEntry == null)) {
            throw new InvalidRequestException("an item is marked adjusted with exactly one of authorizationRequestId and auditEntryId");
        }
    }
}
