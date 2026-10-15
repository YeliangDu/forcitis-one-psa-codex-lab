package com.forcitis.one.fixture.infrastructure.stray;

/** Deliberately wrong: carries the name of the exempt session store clock in another package. Only TimeSourceGuardTest loads it. */
public final class SessionStoreClock {
    public long millis() {
        return System.currentTimeMillis();
    }
}
