package com.forcitis.one.kernel;

import java.util.UUID;

/** The id of a user account, made by identity; not the id of the employee behind it (ADR-0033 section 3). */
@ValueObject
public record UserId(UUID value) {

    public UserId {
        value = Uuids.requireKey(value, "UserId");
    }

    /** Only the lower-case canonical form, as a session principal or a stored text carries it. */
    public static UserId parse(String text) {
        return new UserId(Uuids.parseCanonical(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
