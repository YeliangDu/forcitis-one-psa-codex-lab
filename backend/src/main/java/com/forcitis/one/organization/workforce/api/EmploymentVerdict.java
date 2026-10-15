package com.forcitis.one.organization.workforce.api;

/**
 * The organisation snapshot's verdict on whether one employee is employed (ADR-0033 section 3 item 7). SEPARATED is said even by a stale
 * snapshot; NOT_LISTED only by a current one; SNAPSHOT_UNAVAILABLE when the snapshot has never synced or is stale and does not list the
 * person as separated.
 */
public enum EmploymentVerdict {
    EMPLOYED,
    SEPARATED,
    NOT_LISTED,
    SNAPSHOT_UNAVAILABLE
}
