package com.forcitis.one.platform.identity.api;

/**
 * Marks of the session itself, next to the permissions its roles give. ORDINARY is given only to a session that has been confirmed
 * and is not in the administrator mode (nor an emergency session): what is for the ordinary working day, such as handling approvals,
 * asks for it (I-11).
 */
public final class SessionAuthorities {

    public static final String ORDINARY = "SESSION_ORDINARY";

    private SessionAuthorities() {
    }
}
