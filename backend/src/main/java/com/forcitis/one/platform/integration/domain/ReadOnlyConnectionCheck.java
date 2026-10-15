package com.forcitis.one.platform.integration.domain;

/** ADR-0037 item 4.4: a sync reads an external database only after its login passed the read-only check again. */
@FunctionalInterface
public interface ReadOnlyConnectionCheck {

    /**
     * Checks the login behind the named external connection again and throws {@link com.forcitis.one.kernel.ExternalSystemUnavailableException}
     * naming the unmet items (a)–(g) unless it passes. A violation stops every connection of that login for good (until the next start); a
     * check that cannot be determined fails only this read, until three in a row stop the login as well (intent 2026-10-15-120940).
     */
    void recheck(String connectionName);
}
