package com.forcitis.one.platform.identity.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** administrationExpiresAt is set only for a break-glass login: the administration mode ends then, the page shows the countdown. */
record LoginResponse(String userId, Instant absoluteExpiresAt, @Nullable Instant administrationExpiresAt) {
}
