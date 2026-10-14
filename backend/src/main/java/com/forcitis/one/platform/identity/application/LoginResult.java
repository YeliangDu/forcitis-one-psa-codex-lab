package com.forcitis.one.platform.identity.application;

import java.time.Instant;

/** Rejected carries the coarse error only. */
public sealed interface LoginResult {

    record Authenticated(long userId, Instant absoluteExpiresAt) implements LoginResult {
    }

    record Rejected(IdentityError error) implements LoginResult {
    }
}
