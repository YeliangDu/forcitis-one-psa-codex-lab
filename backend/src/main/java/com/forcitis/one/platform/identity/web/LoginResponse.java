package com.forcitis.one.platform.identity.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * identityConfirmationRequired: a ticket from another site started this session; the page asks the person to confirm it is theirs (C-M7).
 * administratorModeExpiresAt is set when the login starts in the administrator mode (emergency login); the client shows the countdown.
 */
record LoginResponse(UUID userId, Instant absoluteExpiresAt, @Nullable Instant administratorModeExpiresAt, boolean identityConfirmationRequired) {
}
