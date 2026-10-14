package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Set;

/** code is used on create only; an update replaces the whole definition. */
public record SaveRoleCommand(String code, String name, boolean enabled, AccessScopeKind accessScope, Set<String> permissionCodes) {

    public SaveRoleCommand {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }
}
