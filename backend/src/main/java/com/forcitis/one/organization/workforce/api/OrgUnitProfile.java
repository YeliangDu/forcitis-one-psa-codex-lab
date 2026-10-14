package com.forcitis.one.organization.workforce.api;

import java.util.Objects;

/** Snapshot of one org unit. parentOrgUnitId is null for the root. */
public record OrgUnitProfile(OrgUnitId orgUnitId, String name, OrgUnitId parentOrgUnitId, boolean active) {

    public OrgUnitProfile {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
    }
}
