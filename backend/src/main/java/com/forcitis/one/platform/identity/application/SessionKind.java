package com.forcitis.one.platform.identity.application;

import java.util.Objects;

/** What every request is checked against: the mode, and whether the session came from the emergency login. */
public record SessionKind(SessionMode mode, boolean emergency) {

    public SessionKind {
        Objects.requireNonNull(mode, "mode");
        if (emergency && mode != SessionMode.ADMINISTRATOR) {
            throw new IllegalArgumentException("an emergency session is always in the administrator mode");
        }
    }

    public static SessionKind ordinary() {
        return new SessionKind(SessionMode.ORDINARY, false);
    }
}
