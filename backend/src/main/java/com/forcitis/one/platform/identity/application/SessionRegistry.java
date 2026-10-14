package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;

public interface SessionRegistry {

    /** New session id (no fixation) and new CSRF token. An emergency login starts in the administrator mode. */
    StartedSession start(UserId userId, LoginMethod method, boolean identityConfirmed);

    /** False for a session that a ticket from another site started, until the person confirms it is theirs. */
    boolean identityConfirmed();

    /** Gives the session a new id and a new CSRF token: the level of trust has changed. */
    void confirmIdentity();

    /** Switches the current session to the administrator mode (new session id); the mode never extends itself. Returns its end. */
    Instant beginAdministratorMode();

    /** Back to the ordinary mode; the login time and the absolute cap are unchanged. */
    void endAdministratorMode();

    void end();

    void revokeAll(UserId userId);
}
