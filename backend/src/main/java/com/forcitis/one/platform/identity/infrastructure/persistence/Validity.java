package com.forcitis.one.platform.identity.infrastructure.persistence;

/** The one spelling of "valid now" for the versioned identity tables (ADR-0034 decision 4 items 1 and 2; guard 0034-G3-03). Prefix it with the table alias where tables are joined. */
final class Validity {

    static final String CURRENT = "effective_to_at is null";

    private Validity() {
    }
}
