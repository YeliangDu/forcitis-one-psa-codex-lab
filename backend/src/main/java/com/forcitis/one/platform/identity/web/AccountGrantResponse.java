package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.application.AccountGrantItem;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record AccountGrantResponse(String roleCode, String channel, Instant effectiveFrom, @Nullable Instant effectiveTo, @Nullable String revocationCause,
        @Nullable UUID grantedBy, @Nullable UUID revokedBy) {

    static AccountGrantResponse of(AccountGrantItem row) {
        return new AccountGrantResponse(row.roleCode(), row.channel(), row.effectiveFrom(), row.effectiveTo(), row.revocationCause(), id(row.grantedBy()),
                id(row.revokedBy()));
    }

    private static @Nullable UUID id(@Nullable UserId user) {
        return user == null ? null : user.value();
    }
}
