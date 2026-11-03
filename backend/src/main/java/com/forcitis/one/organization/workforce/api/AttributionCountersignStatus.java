package com.forcitis.one.organization.workforce.api;

/** Where a countersign request stands: open until a finance head decides it or it lapses because its command left the wait. */
public enum AttributionCountersignStatus {
    OPEN,
    APPROVED,
    REJECTED,
    LAPSED
}
