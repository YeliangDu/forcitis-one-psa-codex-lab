package com.forcitis.one.organization.workforce.domain;

/**
 * ACTIVE and DISABLED come from the A8 flags (RULE-355: IS_DELETED = 1, IS_INTERNAL != 1 or IS_ENABLE != 1 disables;
 * a NULL flag counts as valid). REVOKED means A8 no longer returns the unit - disabled and revoked are separate states.
 */
public enum OrgUnitStatus {
    ACTIVE,
    DISABLED,
    REVOKED
}
