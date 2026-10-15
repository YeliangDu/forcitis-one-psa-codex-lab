package com.forcitis.one.platform.integration.domain;

/** ADR-0037 item 4.4: a sync reads an external database only after its login passed the read-only check again. */
@FunctionalInterface
public interface ReadOnlyConnectionCheck {

    /**
     * Checks the login behind the named external connection again; a failure stops every connection of that login for good (until the next
     * start) and throws {@link com.forcitis.one.kernel.ExternalSystemUnavailableException} naming the unmet items (a)–(f).
     */
    void recheck(String connectionName);
}
