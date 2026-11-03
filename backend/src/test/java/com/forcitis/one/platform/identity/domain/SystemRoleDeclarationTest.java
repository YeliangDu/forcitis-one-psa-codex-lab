package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemRoleDeclarationTest {

    static final RoleDefinition DECLARED = new RoleDefinition("员工", RoleStatus.ENABLED, AccessScopeKind.SELF, Set.of(Permission.Codes.PLATFORM_FILE_FILE_READ));
    static final RoleDefinition OTHER = new RoleDefinition("员工", RoleStatus.ENABLED, AccessScopeKind.SELF, Set.of());

    static DeclaredRole declared(int revision) {
        return new DeclaredRole(new RoleId(TestIds.uuid(102)), new RoleCode("employee"), false, revision, DECLARED);
    }

    @Test
    @DisplayName("ADR-0034 1.7 the reconciliation decides by the revision: none stored creates, a higher one revises, the same one with the same definition changes nothing, a lower one is kept, the same one with another definition is refused")
    void theRevisionDecides() {
        assertThat(declared(1).outcome(null, null, null)).isEqualTo(DeclarationOutcome.CREATED);
        assertThat(declared(1).outcome(OTHER, null, null)).as("a role with versions but no SEED version").isEqualTo(DeclarationOutcome.REVISED);
        assertThat(declared(2).outcome(DECLARED, 1, 1)).isEqualTo(DeclarationOutcome.REVISED);
        assertThat(declared(1).outcome(DECLARED, 1, 1)).isEqualTo(DeclarationOutcome.UNCHANGED);
        assertThat(declared(1).outcome(DECLARED, 2, 2)).isEqualTo(DeclarationOutcome.OLDER_IN_CODE);
        assertThat(declared(1).outcome(OTHER, 1, 1)).isEqualTo(DeclarationOutcome.CONFLICTING);
        assertThat(declared(1).outcome(OTHER, null, 1)).as("the current version is not a SEED one: the declaration replaces it").isEqualTo(DeclarationOutcome.REVISED);
    }

    @Test
    @DisplayName("card 7.2 / 0034-G8-02 the declaration holds the eight system roles with the fixed ids 019a…101 to …108 at revision 1, super_admin the only administrative one")
    void theEightSystemRoles() {
        assertThat(SystemRoleDeclaration.CURRENT.roles()).extracting(role -> role.roleId().value().toString()).containsExactly(
                "019a0000-0000-7000-8000-000000000101", "019a0000-0000-7000-8000-000000000102", "019a0000-0000-7000-8000-000000000103",
                "019a0000-0000-7000-8000-000000000104", "019a0000-0000-7000-8000-000000000105", "019a0000-0000-7000-8000-000000000106",
                "019a0000-0000-7000-8000-000000000107", "019a0000-0000-7000-8000-000000000108");
        // org_data_admin's codes changed in #934 (sync-run:read/execute -> org-reconciliation:execute) and in #907 (org-structure-command:create added), so its revision is 3, then 4 with permission-adjustment:read and :update (#730 S3); security_auditor is at 2 since it reads permission adjustments; business_admin's is 2 since #895 gave it legal-entity-attribution:approve; every other role is still at the first declaration
        assertThat(SystemRoleDeclaration.CURRENT.roles()).allSatisfy(role -> assertThat(role.revisionNumber()).as(role.code().value()).isEqualTo(switch (role.code().value()) { case "org_data_admin" -> 4; case "business_admin", "security_auditor" -> 2; default -> 1; }));
        assertThat(SystemRoleDeclaration.CURRENT.roles()).filteredOn(DeclaredRole::administrative).extracting(role -> role.code().value())
                .containsExactly("super_admin");
        assertThat(SystemRoleDeclaration.CURRENT.roles()).allSatisfy(role -> assertThat(role.definition().permissionCodes()).allMatch(Permission::isLive));
    }

    @Test
    @DisplayName("ADR-0034 1.4 a role knows whether its definition changed since it was loaded: only then does saving it add a version")
    void definitionChanged() {
        Role role = Role.restore(new RoleId(TestIds.uuid(7)), new RoleCode("analyst"), "CUSTOM", false, new RoleVersionId(TestIds.uuid(70)), "Analyst", "ENABLED",
                AccessScopeKind.SELF, Set.of(Permission.Codes.PLATFORM_FILE_FILE_READ), Set.of(), 1, 1);
        role.revise("Analyst", AccessScopeKind.SELF, Set.of(Permission.Codes.PLATFORM_FILE_FILE_READ));
        role.declareEnabled(true);
        assertThat(role.definitionChanged()).as("the same name, scope, codes and status").isFalse();

        role.disable();
        assertThat(role.definitionChanged()).isTrue();
        assertThat(Role.newCustom(new RoleId(TestIds.uuid(8)), new RoleCode("new"), "New", Set.of()).definitionChanged()).as("a new role has no stored version").isTrue();
    }
}
