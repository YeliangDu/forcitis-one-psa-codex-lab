package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.Permission;
import java.util.Set;

/**
 * The only permissions an administrator mode session has: platform authorisation and lifting the hold that separating the last
 * administrators puts on a personnel command (ADR-0028); never business, sensitive reads or audit (I-11).
 */
public final class AdministratorMode {

    public static final Set<String> PERMISSIONS = Set.of(
            Permission.Codes.PLATFORM_IDENTITY_ROLE_READ,
            Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE,
            Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE);

    /** What only the administrator mode may hold: no role carries these into an ordinary session (separation-hold:approve may, ADR-0028). */
    public static final Set<String> PLATFORM_ONLY = Set.of(
            Permission.Codes.PLATFORM_IDENTITY_ROLE_READ,
            Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE);

    private AdministratorMode() {
    }
}
