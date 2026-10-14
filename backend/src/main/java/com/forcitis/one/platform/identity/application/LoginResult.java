package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.IdentityError;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Rejected carries the coarse error only. */
public sealed interface LoginResult {

    /** browserIdentifier is the new value for the browser's identifier cookie, present only when the login skipped the confirmation. */
    record Authenticated(long userId, Instant absoluteExpiresAt, boolean identityConfirmationRequired, @Nullable String browserIdentifier)
            implements LoginResult {
    }

    record Rejected(IdentityError error) implements LoginResult {
    }
}
