package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.application.RoleView;
import java.util.List;

record RoleResponse(String roleId, String code, String name, String kind, boolean enabled, AccessScopeKind accessScope,
                    boolean administrative, List<String> permissionCodes) {

    static RoleResponse of(RoleView view) {
        return new RoleResponse(String.valueOf(view.roleId()), view.code(), view.name(), view.kind(), view.enabled(), view.accessScope(),
                view.administrative(), view.permissionCodes());
    }
}
