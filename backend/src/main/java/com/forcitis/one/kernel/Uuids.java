package com.forcitis.one.kernel;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * RFC 9562 version 7: 48-bit Unix milliseconds from the injected clock, then a 12-bit counter and random bits. Sorts by time; ids
 * made in the same millisecond by one process also sort in the order they were made, so ordering by id keeps insertion order.
 */
public final class Uuids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int COUNTER_START_BOUND = 1 << 11;
    private static final int COUNTER_LIMIT = 1 << 12;
    private static long lastMillis = -1;
    private static int lastCounter;

    private Uuids() {
    }

    public static UUID timeOrdered(Clock clock) {
        return timeOrdered(clock.instant());
    }

    public static synchronized UUID timeOrdered(Instant at) {
        long millis = at.toEpochMilli();
        if (millis < 0 || millis >= 1L << 48) {
            throw new IllegalArgumentException("clock outside the UUIDv7 range: " + millis);
        }
        int counter;
        if (millis == lastMillis && lastCounter + 1 < COUNTER_LIMIT) {
            counter = lastCounter + 1;
        } else {
            counter = RANDOM.nextInt(COUNTER_START_BOUND);
        }
        lastMillis = millis;
        lastCounter = counter;
        long high = (millis << 16) | 0x7000L | counter;
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
