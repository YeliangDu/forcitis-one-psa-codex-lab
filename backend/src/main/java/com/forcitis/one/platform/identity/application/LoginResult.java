package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.IdentityError;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Rejected carries the coarse error only. */
public sealed interface LoginResult {

    record Authenticated(long userId, Instant absoluteExpiresAt, @Nullable Instant administratorModeExpiresAt) implements LoginResult {
    }

    record Rejected(IdentityError error) implements LoginResult {
    }
}
