package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

public interface SessionRegistry {

    /** New session id (no fixation) and new CSRF token. */
    Instant start(UserId userId, LoginMethod method, boolean identityConfirmed);

    /** False for a session that a ticket from another site started, until the person confirms it is theirs. */
    boolean identityConfirmed();

    /** Gives the session a new id and a new CSRF token: the level of trust has changed. */
    void confirmIdentity();

    void end();

    void revokeAll(UserId userId);
}
