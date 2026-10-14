package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.platform.identity.api.RoleId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleTest {

    static Role custom(String code, Set<String> permissions) {
        return Role.restore(new RoleId(7), new RoleCode(code), code, "CUSTOM", "ENABLED", AccessScopeKind.SELF, false, permissions, 2, 2);
    }

    static Role system(String code, Set<String> permissions) {
        return Role.restore(new RoleId(1), new RoleCode(code), code, "SYSTEM", "ENABLED", AccessScopeKind.ALL, true, permissions, 0, 0);
    }

    @Test
    @DisplayName("RULE-345 a new role defaults to the least-privilege scope SELF, is enabled, custom and not administrative")
    void newRoleDefaults() {
        Role role = Role.newCustom(new RoleCode("analyst"), " Analyst ", Set.of(Permission.Codes.PLATFORM_APPROVAL_TASK_READ));

        assertThat(role.accessScope()).isEqualTo(AccessScopeKind.SELF);
        assertThat(role.kind()).isEqualTo(RoleKind.CUSTOM);
        assertThat(role.isEnabled()).isTrue();
        assertThat(role.administrative()).isFalse();
        assertThat(role.name()).isEqualTo("Analyst");
    }

    @Test
    @DisplayName("RULE-345 super_admin is reserved in any case; codes and names must be well formed; permissions must exist")
    void creationRules() {
        assertThatThrownBy(() -> Role.newCustom(new RoleCode("super_admin"), "x", Set.of())).isInstanceOfSatisfying(RoleRuleException.class,
                e -> assertThat(e.violation()).isEqualTo(RoleViolation.RESERVED_CODE));
        assertThatThrownBy(() -> Role.newCustom(new RoleCode("Super_Admin"), "x", Set.of())).isInstanceOfSatisfying(RoleRuleException.class,
                e -> assertThat(e.violation()).isEqualTo(RoleViolation.RESERVED_CODE));
        assertThatThrownBy(() -> Role.newCustom(new RoleCode("has space"), "x", Set.of())).isInstanceOfSatisfying(RoleRuleException.class,
                e -> assertThat(e.violation()).isEqualTo(RoleViolation.INVALID_CODE));
        assertThatThrownBy(() -> Role.newCustom(new RoleCode("ok"), " ", Set.of())).isInstanceOfSatisfying(RoleRuleException.class,
                e -> assertThat(e.violation()).isEqualTo(RoleViolation.BLANK_NAME));
        assertThatThrownBy(() -> Role.newCustom(new RoleCode("ok"), "x", Set.of("no:such:permission")))
                .isInstanceOfSatisfying(RoleRuleException.class, e -> assertThat(e.violation()).isEqualTo(RoleViolation.UNKNOWN_PERMISSION));
        assertThat(Role.newCustom(new RoleCode("reviewer"), "Reviewer", Set.of()).code().value()).isEqualTo("reviewer");
    }

    @Test
    @DisplayName("RULE-345 system roles are frozen; custom roles can be revised and keep their code")
    void frozenSystemRoles() {
        assertThatThrownBy(() -> system("employee", Set.of()).revise("x", AccessScopeKind.SELF, Set.of()))
                .isInstanceOfSatisfying(RoleRuleException.class, e -> assertThat(e.violation()).isEqualTo(RoleViolation.SYSTEM_FROZEN));
        assertThatThrownBy(() -> system("employee", Set.of()).requireModifiable()).isInstanceOf(RoleRuleException.class);

        Role revised = custom("analyst", Set.of());
        revised.revise("Senior", AccessScopeKind.OWN_ORG_UNIT, Set.of(Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE));
        revised.disable();
        assertThat(revised.code().value()).isEqualTo("analyst");
        assertThat(revised.isEnabled()).isFalse();
        assertThat(revised.accessScope()).isEqualTo(AccessScopeKind.OWN_ORG_UNIT);
        assertThat(revised.permissionCodes()).containsExactly(Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE);
    }

    @Test
    @DisplayName("RULE-341/343 permissions come from enabled roles only; a person without roles has none")
    void effectivePermissions() {
        Role enabled = custom("a", Set.of(Permission.Codes.TIMESHEET_SUBMIT));
        Role disabled = Role.restore(new RoleId(8), new RoleCode("b"), "b", "CUSTOM", "DISABLED", AccessScopeKind.SELF, false,
                Set.of(Permission.Codes.TIMESHEET_APPROVE), 0, 0);

        AccessProfile profile = AccessProfile.of(List.of(enabled, disabled));

        assertThat(profile.hasPermission(Permission.Codes.TIMESHEET_SUBMIT)).isTrue();
        assertThat(profile.hasPermission(Permission.Codes.TIMESHEET_APPROVE)).as("disabled role").isFalse();
        assertThat(profile.hasPermission("never:linked:read")).as("RULE-341: unlinked permission is denied").isFalse();
        assertThat(AccessProfile.of(List.of()).permissionCodes()).isEmpty();
    }

    @Test
    @DisplayName("RULE-340 an enabled super_admin holds every permission and counts as administrator; a disabled one does not")
    void superAdmin() {
        AccessProfile admin = AccessProfile.of(List.of(system("super_admin", Set.of())));

        assertThat(admin.isAdministrator()).isTrue();
        assertThat(admin.permissionCodes()).hasSize(Permission.values().length);
        assertThat(admin.hasPermission(Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE)).isTrue();

        Role disabledAdmin = Role.restore(new RoleId(1), new RoleCode("super_admin"), "x", "SYSTEM", "DISABLED", AccessScopeKind.ALL, true, Set.of(), 0, 0);
        assertThat(AccessProfile.of(List.of(disabledAdmin)).isAdministrator()).isFalse();
    }

    @Test
    @DisplayName("RULE-342 role-code check: any of the codes; an empty list passes; super_admin always passes")
    void roleCodeCheck() {
        AccessProfile profile = AccessProfile.of(List.of(custom("reviewer", Set.of())));

        assertThat(profile.hasAnyRole(List.of(new RoleCode("reviewer"), new RoleCode("PMO")))).isTrue();
        assertThat(profile.hasAnyRole(List.of(new RoleCode("PMO")))).isFalse();
        assertThat(profile.hasAnyRole(List.of())).isTrue();
        assertThat(AccessProfile.of(List.of(system("super_admin", Set.of()))).hasAnyRole(List.of(new RoleCode("PMO")))).isTrue();
    }
}
