package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.Permission;
import java.util.Set;
import java.util.stream.Collectors;

/** Granting any of these, or changing a role to carry one, needs a second person (ADR-0026 section 6, ADR-0027 section 3). */
public final class SensitivePermissions {

    public static final Set<String> CODES = Set.of(
            Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
            Permission.Codes.PLATFORM_AUDIT_ENTRY_READ,
            Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT,
            Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE,
            Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE);

    private SensitivePermissions() {
    }

    public static Set<String> within(Set<String> permissionCodes) {
        return permissionCodes.stream().filter(CODES::contains).collect(Collectors.toUnmodifiableSet());
    }

    public static boolean roleIsSensitive(Role role) {
        return role.administrative() || Role.isReserved(role.code()) || !within(role.permissionCodes()).isEmpty();
    }
}
