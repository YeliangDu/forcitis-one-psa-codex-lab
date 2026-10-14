package com.forcitis.one.organization.workforce.api;

/** Why a sync run waits for a person (ADR-0028): each kind is lifted by its own permission, and the run stays held until all are. */
public enum HoldKind {
    SHRINK_GUARD,
    ADMINISTRATOR_SEPARATION
}
