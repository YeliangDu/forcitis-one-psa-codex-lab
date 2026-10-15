package com.forcitis.one.fixture.infrastructure.stray;

/** Deliberately wrong: depends on a session store clock without being its one permitted user. Only TimeSourceGuardTest loads it. */
public final class StrayClockUser {
    public long ask() {
        return new SessionStoreClock().millis();
    }
}
