package com.forcitis.one.platform.integration.api;

import java.time.Period;
import java.util.List;

/**
 * The way a module archives rows before it removes them: one immutable object per call, written through the object store and verified,
 * with the newest copy of each kind kept on the local disk. archive returns only when the store confirmed the object; any failure
 * throws, so the caller removes nothing.
 */
public interface ArchiveVault {
    /** lines are NDJSON records; kind names the archive (lower-case, e.g. process, audit). */
    ArchivedObject archive(String kind, List<String> lines);

    /** Writes and removes a local probe file and probes the object store. */
    void probe(String kind);

    /**
     * Fails unless the time online plus the days the bucket is locked (WORM) equal the one total keep period; call it when a job starts, so
     * changing one of the three alone stops the start-up.
     */
    void requireTotalKeepPeriod(String what, Period online);
}
