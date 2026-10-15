package com.forcitis.one.kernel;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** RFC 9562 version 7 with random bits after the millisecond timestamp; ids made in the same millisecond have no defined order. */
public final class Uuids {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID timeOrdered(Clock clock) {
        return timeOrdered(clock.instant());
    }

    public static UUID timeOrdered(Instant at) {
        long millis = at.toEpochMilli();
        requireInRange(millis);
        return of(millis, RANDOM.nextInt(1 << 12));
    }

    /** Version 7 id of the given millisecond with a 12-bit counter in the rand_a field (RFC 9562 section 6.2, method 1). */
    static UUID of(long millis, int counter) {
        long high = (millis << 16) | 0x7000L | counter;
        long low = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    static void requireInRange(long millis) {
        if (millis < 0 || millis >= 1L << 48) {
            throw new IllegalArgumentException("clock outside the UUIDv7 range: " + millis);
        }
    }

    static int randomCounterStart() {
        return RANDOM.nextInt(1 << 11);
    }

    public static Instant instantOf(UUID id) {
        if (id.version() != 7) {
            throw new IllegalArgumentException("not a time-ordered UUID: " + id);
        }
        return Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16);
    }

    /** Only the lower-case canonical form 8-4-4-4-12 is accepted: UUID.fromString also takes short and upper-case forms, so one id would have several spellings. */
    public static UUID parseCanonical(String text) {
        UUID id;
        try {
            id = UUID.fromString(text);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("not a UUID: " + text, malformed);
        }
        if (!id.toString().equals(text)) {
            throw new IllegalArgumentException("not the canonical form of a UUID: " + text);
        }
        return id;
    }

    /** A key of this system: never the all-zero UUID, which would read as "no key". */
    public static UUID requireKey(UUID id, String what) {
        if (id == null) {
            throw new NullPointerException(what);
        }
        if (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) {
            throw new IllegalArgumentException(what + " must not be the nil UUID");
        }
        return id;
    }
}
