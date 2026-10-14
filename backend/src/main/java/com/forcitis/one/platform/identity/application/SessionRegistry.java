package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

public interface SessionRegistry {

    /** New session id (no fixation) and new CSRF token. */
    Instant start(UserId userId, LoginMethod method);

    void end();

    void revokeAll(UserId userId);
}
