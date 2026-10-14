package com.forcitis.one.platform.identity.application;

import java.time.Instant;
import java.util.Optional;

/** The session that makes the current request. */
public interface SessionContext {

    SessionMode mode();

    /** Empty outside a signed-in request. */
    Optional<SessionMode> modeIfSignedIn();

    Optional<Instant> administratorModeExpiresAt();

    LoginMethod loginMethod();
}
