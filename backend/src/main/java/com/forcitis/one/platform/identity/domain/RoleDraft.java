package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Set;

/** A role definition waiting for the second person; accessScope is empty to keep the role's own. */
public record RoleDraft(String code, String name, boolean enabled, AccessScopeKind accessScope, Set<String> permissionCodes) {

    public RoleDraft {
        permissionCodes = Set.copyOf(permissionCodes);
    }
}
