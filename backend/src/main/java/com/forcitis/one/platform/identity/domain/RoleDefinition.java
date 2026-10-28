package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import java.util.Objects;
import java.util.Set;

/**
 * What a role version holds (ADR-0034 decision 1 item 2, section 5): the name, the status, the scope kind, the whole permission set and,
 * for CUSTOM_ORG_UNITS, the whole set of custom org units. Two definitions are equal when all five are; a change that leaves the definition
 * equal adds no version (decision 1 item 4), and a changed unit or "with descendants" choice is a changed definition.
 */
@ValueObject
public record RoleDefinition(String name, RoleStatus status, AccessScopeKind accessScope, Set<String> permissionCodes, Set<CustomOrgUnit> customOrgUnits) {

    public RoleDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(accessScope, "accessScope");
        permissionCodes = Set.copyOf(permissionCodes);
        customOrgUnits = Set.copyOf(customOrgUnits);
    }

    /** A definition that lists no custom org unit. */
    public RoleDefinition(String name, RoleStatus status, AccessScopeKind accessScope, Set<String> permissionCodes) {
        this(name, status, accessScope, permissionCodes, Set.of());
    }
}
