package com.forcitis.one.organization.workforce.api;

/** Where a profile change request stands: it waits, was applied by a second person, was refused (or withdrawn by its requester), or can no longer be applied. */
public enum ProfileChangeStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    VOIDED
}
