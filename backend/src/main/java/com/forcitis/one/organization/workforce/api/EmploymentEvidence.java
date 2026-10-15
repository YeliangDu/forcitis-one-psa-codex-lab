package com.forcitis.one.organization.workforce.api;

/**
 * What the organisation snapshot can say about one employee's employment (ADR-0033 section 3 item 7). SEPARATED is said even by a stale
 * snapshot; NOT_LISTED only by a current one; SNAPSHOT_UNAVAILABLE when the snapshot has never synced or is stale and does not list the
 * person as separated.
 */
public enum EmploymentEvidence {
    EMPLOYED,
    SEPARATED,
    NOT_LISTED,
    SNAPSHOT_UNAVAILABLE
}
