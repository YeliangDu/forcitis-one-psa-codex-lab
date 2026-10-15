package com.forcitis.one;

import java.util.UUID;

/** Fixed, readable ids for tests: the same number gives the same UUID, and the UUIDs have the UUIDv7 shape. */
public final class TestIds {
    private static final long HIGH = 0x019A_0000_0000_7000L;
    private static final long LOW = 0x8000_0000_0000_0000L;

    private TestIds() {
    }

    public static UUID uuid(long number) {
        return new UUID(HIGH, LOW | number);
    }
}
