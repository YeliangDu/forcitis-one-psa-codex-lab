package com.forcitis.one.platform.numbering.api;

/**
 * Writes the "original number" search structure. Phase 1a delivers the structure only; the numbers of each document type are
 * imported in the stage that switches that document over.
 */
public interface RegisterLegacyNumbers {

    /**
     * Records the link. Registering the same link again is a no-op (imports can be re-run); a legacy number that already
     * belongs to another document is refused with {@link IllegalStateException}.
     */
    void register(LegacyNumberEntry entry);
}
