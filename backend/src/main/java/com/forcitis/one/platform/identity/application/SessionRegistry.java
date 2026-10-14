package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

/** Port to the server-side session of the current request, and to all sessions of a user (Spring Session in infrastructure). */
public interface SessionRegistry {

    /** Starts the session of the current request: new session id (no fixation), new CSRF token; returns when the absolute cap ends it. */
    Instant start(UserId userId, LoginMethod method);

    /** Ends the current request's session. */
    void end();

    /** Deletes every session of the user (RULE-338). */
    void revokeAll(UserId userId);
}
