package com.forcitis.one.platform.identity.application;

import java.time.Instant;
import java.util.Optional;

/** The session that makes the current request. */
public interface SessionContext {

    SessionMode mode();

    Optional<Instant> administratorModeExpiresAt();

    LoginMethod loginMethod();
}
