package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reserved code super_admin; new roles start at the least-privilege scope SELF. */
public record Role(RoleId roleId, RoleCode code, String name, RoleKind kind, RoleStatus status, AccessScopeKind accessScope,
                   boolean administrative, Set<String> permissionCodes) {

    public static final RoleCode SUPER_ADMIN = new RoleCode("super_admin");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Set<String> KNOWN_PERMISSIONS = Arrays.stream(Permission.values())
            .map(Permission::code).collect(Collectors.toUnmodifiableSet());

    public Role {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(accessScope, "accessScope");
        permissionCodes = Set.copyOf(permissionCodes);
    }

    public static boolean isReserved(RoleCode code) {
        return code.value().equalsIgnoreCase(SUPER_ADMIN.value());
    }

    public static Role newCustom(RoleCode code, String name, Set<String> permissionCodes) {
        if (isReserved(code)) {
            throw new RoleRuleException(RoleViolation.RESERVED_CODE);
        }
        if (!CODE.matcher(code.value()).matches()) {
            throw new RoleRuleException(RoleViolation.INVALID_CODE);
        }
        requireName(name);
        requireKnown(permissionCodes);
        return new Role(null, code, name.strip(), RoleKind.CUSTOM, RoleStatus.ENABLED, AccessScopeKind.SELF, false, permissionCodes);
    }

    public Role revise(String newName, RoleStatus newStatus, AccessScopeKind newScope, Set<String> newPermissionCodes) {
        requireModifiable();
        requireName(newName);
        requireKnown(newPermissionCodes);
        return new Role(roleId, code, newName.strip(), kind, newStatus, newScope, administrative, newPermissionCodes);
    }

    public void requireModifiable() {
        if (kind == RoleKind.SYSTEM) {
            throw new RoleRuleException(RoleViolation.SYSTEM_FROZEN);
        }
    }

    public boolean isEnabled() {
        return status == RoleStatus.ENABLED;
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new RoleRuleException(RoleViolation.BLANK_NAME);
        }
    }

    private static void requireKnown(Set<String> permissionCodes) {
        if (!KNOWN_PERMISSIONS.containsAll(permissionCodes)) {
            throw new RoleRuleException(RoleViolation.UNKNOWN_PERMISSION);
        }
    }
}
