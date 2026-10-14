package com.forcitis.one.organization.workforce.domain;

/** Position state synced from A8 (RULE-367): disabled and deleted positions follow A8, so "position must be active" checks stay meaningful. */
public enum PositionStatus {
    ACTIVE,
    DISABLED,
    DELETED
}
