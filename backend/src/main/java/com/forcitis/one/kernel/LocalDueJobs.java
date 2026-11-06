package com.forcitis.one.kernel;

/** Card #1478: the daily jobs of a module, run once on demand. Implemented only under the profile local, where the settable clock is moved to a pretend date and nothing would fall due on a real-time schedule. */
public interface LocalDueJobs {

    /** Runs the daily jobs once for the date the application's Clock shows now, by the very methods the scheduler calls. */
    void runOnce();
}
