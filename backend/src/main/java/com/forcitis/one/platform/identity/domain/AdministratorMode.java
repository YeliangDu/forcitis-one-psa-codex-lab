package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.Permission;
import java.util.Set;

/** The only permissions an administrator mode session has: platform authorisation, never business, sensitive reads or audit (I-11). */
public final class AdministratorMode {

    public static final Set<String> PERMISSIONS = Set.of(
            Permission.Codes.PLATFORM_IDENTITY_ROLE_READ,
            Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE);

    private AdministratorMode() {
    }
}
