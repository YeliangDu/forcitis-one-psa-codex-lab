package com.forcitis.one.organization.workforce.domain;

/** Position state synced from the source (RULE-367): disabled and deleted positions follow the source, so "position must be active" checks stay meaningful. */
public enum PositionStatus {
    ACTIVE,
    DISABLED,
    DELETED
}
