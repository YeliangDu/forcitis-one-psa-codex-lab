package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Objects;
import java.util.Set;

/**
 * What a role version holds (ADR-0034 decision 1 item 2): the name, the status, the scope kind and the whole permission set. Two
 * definitions are equal when all four are; a change that leaves the definition equal adds no version (decision 1 item 4).
 */
@ValueObject
public record RoleDefinition(String name, RoleStatus status, AccessScopeKind accessScope, Set<String> permissionCodes) {

    public RoleDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(accessScope, "accessScope");
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
