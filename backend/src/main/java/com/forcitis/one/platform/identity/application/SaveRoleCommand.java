package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Set;

/**
 * Create or update a role. code is used on create only (a code never changes); accessScope null means the least-privilege
 * default SELF on create and "unchanged" is not possible on update - an update replaces the whole definition (RULE-346).
 */
public record SaveRoleCommand(String code, String name, boolean enabled, AccessScopeKind accessScope, Set<String> permissionCodes) {

    public SaveRoleCommand {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }
}
