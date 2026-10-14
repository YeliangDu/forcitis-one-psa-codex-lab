package com.forcitis.one.fixture.web;

import java.time.Instant;
import java.time.LocalDate;

/** Deliberately wrong: a web class that asks the machine for the time. Only TimeSourceGuardTest loads it. */
public final class AmbientTimeWebSample {
    public String stamp() {
        return Instant.now() + " " + LocalDate.now();
    }
}
