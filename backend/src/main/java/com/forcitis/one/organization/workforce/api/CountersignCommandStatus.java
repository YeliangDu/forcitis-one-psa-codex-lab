package com.forcitis.one.organization.workforce.api;

/** The status of the command a legal-entity countersign belongs to: the personnel command statuses and the org-structure command statuses together. */
public enum CountersignCommandStatus {
    PENDING,
    HELD,
    IN_PROGRESS,
    EFFECTIVE,
    WITHDRAWN,
    AWAITING_ATTRIBUTION_COUNTERSIGN
}
