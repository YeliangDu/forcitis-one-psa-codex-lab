package com.forcitis.one.platform.identity.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record StartedSession(Instant absoluteExpiresAt, @Nullable Instant administratorModeExpiresAt) {
}
