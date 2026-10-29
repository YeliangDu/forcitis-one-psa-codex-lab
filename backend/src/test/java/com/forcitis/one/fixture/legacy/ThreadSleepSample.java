package com.forcitis.one.fixture.legacy;

import java.time.Duration;

/** Banned in tests: Thread.sleep in each of its forms. Only LegacyApiGuardTest loads it. */
public final class ThreadSleepSample {
    public void pause() throws InterruptedException {
        Thread.sleep(10);
        Thread.sleep(10, 0);
        Thread.sleep(Duration.ofMillis(10));
    }
}
