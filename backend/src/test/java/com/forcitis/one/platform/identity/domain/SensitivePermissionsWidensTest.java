package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1244 gap (5): which changes of a two-person role take effect for more than before (ADR-0032 decision 1 item 3). */
class SensitivePermissionsWidensTest {
    private static final Set<String> CODES = Set.of("account-role:update");

    private static CustomOrgUnit unit(UUID id) {
        return new CustomOrgUnit(new com.forcitis.one.organization.workforce.api.OrgUnitId(id), false);
    }

    private static RoleDefinition of(RoleStatus status, AccessScopeKind scope, CustomOrgUnit... units) {
        return new RoleDefinition("role", status, scope, CODES, Set.of(units));
    }

    @Test
    @DisplayName("#1244: a broader scope kind, a unit the old ones do not cover, and a disabled role enabled again widen; the reverse and the same do not")
    void widening() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        RoleStatus on = RoleStatus.ENABLED;
        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.SELF), of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)))).isTrue();
        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)), of(on, AccessScopeKind.ALL))).isTrue();
        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)), of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a), unit(b)))).isTrue();
        assertThat(SensitivePermissions.isBroader(of(RoleStatus.DISABLED, AccessScopeKind.SELF), of(on, AccessScopeKind.SELF))).isTrue();

        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.ALL), of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)))).isFalse();
        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a), unit(b)), of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)))).isFalse();
        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)), of(on, AccessScopeKind.CUSTOM_ORG_UNITS, unit(a)))).isFalse();
        assertThat(SensitivePermissions.isBroader(of(on, AccessScopeKind.SELF), of(RoleStatus.DISABLED, AccessScopeKind.SELF))).isFalse();
    }
}
