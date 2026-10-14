package com.forcitis.one.platform.identity.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** identityConfirmationRequired: a ticket from another site started this session; the page asks the person to confirm it is theirs (C-M7). administrationExpiresAt is set only for a break-glass login: the administration mode ends then, the page shows the countdown. */
record LoginResponse(String userId, Instant absoluteExpiresAt, @Nullable Instant administrationExpiresAt, boolean identityConfirmationRequired) {
}
