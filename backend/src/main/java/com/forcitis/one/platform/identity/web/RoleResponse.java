package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.application.RoleView;
import java.util.List;
import java.util.UUID;

/** customOrgUnits is empty unless accessScope is CUSTOM_ORG_UNITS. */
record RoleResponse(UUID roleId, String code, String name, String kind, boolean enabled, AccessScopeKind accessScope,
                    List<CustomOrgUnitResponse> customOrgUnits, boolean administrative, List<String> permissionCodes) {

    static RoleResponse of(RoleView view) {
        return new RoleResponse(view.roleId().value(), view.code(), view.name(), view.kind(), view.enabled(), view.accessScope(),
                view.customOrgUnits().stream().map(CustomOrgUnitResponse::of).toList(), view.administrative(), view.permissionCodes());
    }
}
