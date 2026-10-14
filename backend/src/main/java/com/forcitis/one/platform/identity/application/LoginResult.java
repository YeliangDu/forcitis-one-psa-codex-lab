package com.forcitis.one.platform.identity.application;

import java.time.Instant;

/** Outcome of a login attempt. Rejected carries the coarse error only. */
public sealed interface LoginResult {

    /** The session is established; absoluteExpiresAt is when the 24 h cap (configurable) ends it. */
    record Authenticated(long userId, Instant absoluteExpiresAt) implements LoginResult {
    }

    record Rejected(IdentityError error) implements LoginResult {
    }
}
