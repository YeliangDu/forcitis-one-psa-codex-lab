package com.forcitis.one.platform.identity.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** administratorModeExpiresAt is set when the login starts in the administrator mode (emergency login); the client shows the countdown. */
record LoginResponse(String userId, Instant absoluteExpiresAt, @Nullable Instant administratorModeExpiresAt) {
}
