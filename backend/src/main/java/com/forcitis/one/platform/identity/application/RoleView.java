package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.Role;
import java.util.List;

public record RoleView(RoleId roleId, String code, String name, String kind, boolean enabled, AccessScopeKind accessScope,
                       boolean administrative, List<String> permissionCodes, long version) {

    public RoleView {
        permissionCodes = List.copyOf(permissionCodes);
    }

    static RoleView of(Role role) {
        return new RoleView(role.roleId(), role.code().value(), role.name(), role.kind().name(), role.isEnabled(),
                role.accessScope(), role.administrative(), role.permissionCodes().stream().sorted().toList(), role.version());
    }
}
