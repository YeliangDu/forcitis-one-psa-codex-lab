package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * code is used on create only; an update replaces the whole definition. customOrgUnits null means "none" on create and "keep the stored
 * units" on update (one-coordinator ruling for #728); it is a list so that a unit named twice reaches the role and is refused there.
 */
public record SaveRoleCommand(String code, String name, boolean enabled, AccessScopeKind accessScope, Set<String> permissionCodes,
                              @Nullable List<CustomOrgUnit> customOrgUnits) {

    public SaveRoleCommand {
        permissionCodes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        customOrgUnits = customOrgUnits == null ? null : List.copyOf(customOrgUnits);
    }
}
