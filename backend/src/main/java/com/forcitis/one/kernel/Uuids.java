package com.forcitis.one.kernel;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** RFC 9562 version 7: 48-bit Unix milliseconds from the injected clock, then random bits. Sorts by time. */
public final class Uuids {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID timeOrdered(Clock clock) {
        return timeOrdered(clock.instant());
    }

    public static UUID timeOrdered(Instant at) {
        long millis = at.toEpochMilli();
        if (millis < 0 || millis >= 1L << 48) {
            throw new IllegalArgumentException("clock outside the UUIDv7 range: " + millis);
        }
        long high = (millis << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
        long low = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    public static Instant instantOf(UUID id) {
        if (id.version() != 7) {
            throw new IllegalArgumentException("not a time-ordered UUID: " + id);
        }
        return Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16);
    }
}
