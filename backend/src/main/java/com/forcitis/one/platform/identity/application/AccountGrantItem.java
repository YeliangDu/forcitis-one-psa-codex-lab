package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** One stored grant of an account, open or closed. The ids of people only: no name, login name or authorization request. */
public record AccountGrantItem(String roleCode, String channel, Instant effectiveFrom, @Nullable Instant effectiveTo, @Nullable String revocationCause,
        @Nullable UserId grantedBy, @Nullable UserId revokedBy) {
}
