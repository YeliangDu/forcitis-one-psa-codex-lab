package com.forcitis.one.platform.identity.application;

import java.time.Instant;

/** The moment inside a list cursor. A forged one outside the years a stored moment can have must not reach the database or overflow a conversion. */
final class CursorMoment {

    private static final Instant EARLIEST = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59Z");

    private CursorMoment() {
    }

    /** @throws java.time.DateTimeException when the text is no moment; @throws IllegalArgumentException when it lies outside 1970..9999 */
    static Instant parse(String text) {
        Instant moment = Instant.parse(text);
        if (moment.isBefore(EARLIEST) || moment.isAfter(LATEST)) {
            throw new IllegalArgumentException("cursor moment out of range");
        }
        return moment;
    }
}
