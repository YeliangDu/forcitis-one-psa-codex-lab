package com.forcitis.one.platform.integration.api;

/** Which kind of own object a source reference points at. A kind whose source id can change over time carries an effective period. */
public enum SourceObjectKind {
    EMPLOYEE(false),
    ORG_UNIT(false),
    POSITION(false),
    LEGAL_ENTITY(true);

    private final boolean hasPeriod;

    SourceObjectKind(boolean hasPeriod) {
        this.hasPeriod = hasPeriod;
    }

    public boolean hasPeriod() {
        return hasPeriod;
    }
}
