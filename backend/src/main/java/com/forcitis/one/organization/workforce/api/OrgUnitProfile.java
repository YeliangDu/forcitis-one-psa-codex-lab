package com.forcitis.one.organization.workforce.api;

import java.util.Objects;

public record OrgUnitProfile(OrgUnitId orgUnitId, String name, OrgUnitId parentOrgUnitId, boolean active) {
    public OrgUnitProfile {
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        Objects.requireNonNull(name, "name");
    }
}
