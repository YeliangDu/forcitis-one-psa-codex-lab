package com.forcitis.one.platform.identity.infrastructure.persistence;

/** Spring Session 4.1.1 has no clock injection point: the session table's times are written by the framework from the system clock. */
final class SessionStoreClock {

    long millis() {
        return System.currentTimeMillis();
    }
}
