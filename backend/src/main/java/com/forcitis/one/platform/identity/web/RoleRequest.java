package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.List;
import java.util.Set;

/**
 * accessScope null means the least-privilege default SELF on create, "keep" on update. customOrgUnits are listed when accessScope is
 * CUSTOM_ORG_UNITS (at least one, each once; otherwise 400); null means none on create and "keep" on update, an empty array is no unit.
 */
record RoleRequest(String code, String name, Boolean enabled, AccessScopeKind accessScope, List<CustomOrgUnitRequest> customOrgUnits, Set<String> permissionCodes) {
}
