package com.forcitis.one.organization.workforce.domain;

/** Disabled and revoked are separate states: REVOKED means the source no longer returns the unit. */
public enum OrgUnitStatus {
    ACTIVE,
    DISABLED,
    REVOKED
}
