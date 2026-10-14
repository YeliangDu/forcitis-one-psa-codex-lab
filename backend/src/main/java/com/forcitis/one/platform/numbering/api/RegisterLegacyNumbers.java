package com.forcitis.one.platform.numbering.api;

public interface RegisterLegacyNumbers {
    /** Re-registering the same link is a no-op; a legacy number owned by another document throws IllegalStateException. */
    void register(LegacyNumberEntry entry);
}
