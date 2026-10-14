package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** super_admin holds every permission; for everyone else a permission nobody links is denied. */
public final class AccessProfile {

    private final Set<RoleCode> roleCodes;
    private final Set<String> permissionCodes;
    private final boolean superAdmin;

    private AccessProfile(Set<RoleCode> roleCodes, Set<String> permissionCodes, boolean superAdmin) {
        this.roleCodes = Collections.unmodifiableSet(roleCodes);
        this.permissionCodes = Collections.unmodifiableSet(permissionCodes);
        this.superAdmin = superAdmin;
    }

    /** enabledRoles must already be filtered to enabled ones; disabled roles passed in are ignored anyway. */
    public static AccessProfile of(List<Role> enabledRoles) {
        Set<RoleCode> codes = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        boolean superAdmin = false;
        for (Role role : enabledRoles) {
            if (!role.isEnabled()) {
                continue;
            }
            codes.add(role.code());
            permissions.addAll(role.permissionCodes());
            superAdmin |= Role.isReserved(role.code());
        }
        if (superAdmin) {
            permissions.addAll(Arrays.stream(Permission.values()).map(Permission::code).collect(Collectors.toSet()));
        }
        return new AccessProfile(codes, permissions, superAdmin);
    }

    public Set<RoleCode> roleCodes() {
        return roleCodes;
    }

    public Set<String> permissionCodes() {
        return permissionCodes;
    }

    public boolean hasPermission(String permissionCode) {
        return permissionCodes.contains(permissionCode);
    }

    /** Any of the codes; an empty list passes; super_admin always passes. */
    public boolean hasAnyRole(Collection<RoleCode> codes) {
        return superAdmin || codes.isEmpty() || codes.stream().anyMatch(roleCodes::contains);
    }

    public boolean isAdministrator() {
        return superAdmin;
    }
}
