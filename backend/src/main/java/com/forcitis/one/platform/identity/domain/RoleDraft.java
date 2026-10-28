package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A role definition waiting for the second person; accessScope is empty to keep the role's own. customOrgUnitIds are the units of a
 * CUSTOM_ORG_UNITS definition, each without descendants: only a two-person role waits here, and such a role lists its units one by one
 * (ADR-0044 section 1, F-1 card constraint 8).
 */
public record RoleDraft(String code, String name, boolean enabled, AccessScopeKind accessScope, Set<String> permissionCodes, Set<UUID> customOrgUnitIds) {

    public RoleDraft {
        permissionCodes = Set.copyOf(permissionCodes);
        customOrgUnitIds = Set.copyOf(customOrgUnitIds);
    }

    /** The ids of units chosen without descendants; a unit with descendants never reaches a request (the scope check refuses it first). */
    public static Set<UUID> unitIdsOf(Set<CustomOrgUnit> units) {
        if (units.stream().anyMatch(CustomOrgUnit::includeDescendants)) {
            throw new IllegalStateException("an authorization request names its custom org units without descendants (ADR-0044 section 1)");
        }
        return units.stream().map(unit -> unit.orgUnitId().value()).collect(Collectors.toUnmodifiableSet());
    }

    /** The units of the draft as a role lists them: each without descendants; empty when the draft names none. */
    public List<CustomOrgUnit> customOrgUnits() {
        return customOrgUnitIds.stream().sorted().map(id -> CustomOrgUnit.only(new OrgUnitId(id))).toList();
    }
}
