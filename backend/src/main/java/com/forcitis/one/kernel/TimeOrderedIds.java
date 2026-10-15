package com.forcitis.one.kernel;

import java.time.Clock;
import java.util.UUID;

/**
 * UUIDv7 ids that rise strictly within this instance, using a 12-bit counter in the rand_a field (RFC 9562 section 6.2). When the
 * counter of a millisecond is used up, or the clock steps back, the timestamp is carried forward from the last one made instead of
 * starting again, so an id is never smaller than the one before it. Across instances there is no order beyond the timestamp.
 */
public final class TimeOrderedIds implements IdGenerator {
    private static final int COUNTER_LIMIT = 1 << 12;

    private final Clock clock;
    private long lastMillis = -1;
    private int counter;

    public TimeOrderedIds(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized UUID next() {
        long millis = clock.instant().toEpochMilli();
        Uuids.requireInRange(millis);
        if (millis > lastMillis) {
            lastMillis = millis;
            counter = Uuids.randomCounterStart();
        } else if (counter + 1 < COUNTER_LIMIT) {
            counter++;
        } else {
            lastMillis++;
            Uuids.requireInRange(lastMillis);
            counter = Uuids.randomCounterStart();
        }
        return Uuids.of(lastMillis, counter);
    }
}
