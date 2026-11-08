package com.forcitis.one.acceptance.platform.identity.p1;

import static com.forcitis.one.acceptance.platform.identity.p1.PermissionP1Fixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.AdministratorMode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionCatalogueP1Test {
    static final String CODE = "organization.workforce:employee:read";

    @Test
    void facadeAndOneExactOwnerAreOneEntryIncludingDeprecatedMetadata() throws Exception {
        Object active = declaration(CODE, "INDIVIDUAL", false);
        Object retired = declaration("organization.workforce:sync-run:read", "INDIVIDUAL", true);
        Object result = assemble(List.of(contribution("organization.workforce", List.of(active, retired))));
        assertThat(snapshot(result)).isEqualTo(frozen());
        for (Object conflict : List.of(declaration(CODE, "TWO_PERSON_INDIVIDUAL", false),
                declaration(CODE, "INDIVIDUAL", true),
                declaration("organization.workforce:sync-run:read", "INDIVIDUAL", false))) {
            assertThatThrownBy(() -> assemble(List.of(contribution("organization.workforce", List.of(conflict)))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void duplicateInsideOrAcrossContributionsFailsEvenWhenIdentical() throws Exception {
        Object item = declaration(CODE, "INDIVIDUAL", false);
        assertThatThrownBy(() -> assemble(List.of(contribution("organization.workforce", List.of(item, item)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assemble(List.of(contribution("organization.workforce", List.of(item)),
                contribution("organization.workforce", List.of(item))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assemble(List.of(contribution("organization.workforce", List.of(item)),
                contribution("organization.payroll", List.of(item))))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownershipAndG43FormatRejectRatherThanManufactureCatalogueEntries() throws Exception {
        type(CATALOGUE);
        for (String owner : List.of("platform.identity", "organization", "Organization.workforce")) {
            assertThatThrownBy(() -> assemble(List.of(contribution(owner, List.of(declaration(CODE, "INDIVIDUAL", false))))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String code : List.of("organization.workforce:employee:invented", "organization.workforce:unknown-resource:read",
                "organization.workforce:Employee:read", "employee:read", " organization.workforce:employee:read")) {
            assertThatThrownBy(() -> assemble(List.of(contribution("organization.workforce", List.of(declaration(code, "INDIVIDUAL", false))))))
                    .isInstanceOfAny(IllegalArgumentException.class, java.lang.reflect.InvocationTargetException.class);
        }
    }

    @Test
    void unknownAnnotationRoleAdministratorAndSensitiveReferencesAllFail() throws Exception {
        List<Object> legacy = legacy();
        String unknown = "organization.workforce:employee:delete";
        for (int channel = 0; channel < 4; channel++) {
            Set<String> sensitive = new TreeSet<>(SENSITIVE);
            if (channel == 0) sensitive.add(unknown);
            Set<String> references = channel == 1 ? Set.of(unknown) : Set.of();
            Set<String> grants = channel == 2 ? Set.of(unknown) : Set.of();
            Set<String> admins = channel == 3 ? Set.of(unknown) : Set.of();
            assertThatThrownBy(() -> assemble(legacy, List.of(), sensitive, references, grants, admins))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> Role.newCustom(new RoleId(UUID.randomUUID()), new RoleCode("synthetic"), "Synthetic", Set.of(unknown)))
                .isInstanceOfSatisfying(RoleRuleException.class, error -> assertThat(error.violation()).isEqualTo(RoleViolation.UNKNOWN_PERMISSION));
    }

    @Test
    void completeMixedCatalogueKeepsExactlyNineSensitiveCodesAndRejectsDowngrade() throws Exception {
        assertThat(SensitivePermissions.CODES).isEqualTo(SENSITIVE).hasSize(9);
        assertThat(twoPerson(snapshot(assemble(List.of())))).isEqualTo(SENSITIVE);
        for (String omitted : SENSITIVE) {
            Set<String> missing = new TreeSet<>(SENSITIVE);
            missing.remove(omitted);
            assertThatThrownBy(() -> assemble(legacy(), List.of(), missing, Set.of(), Set.of(), Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            List<Object> downgraded = new ArrayList<>();
            for (var item : frozen().entrySet()) {
                String[] flags = item.getValue().split(":");
                downgraded.add(declaration(item.getKey(), item.getKey().equals(omitted) ? "INDIVIDUAL" : flags[0], Boolean.parseBoolean(flags[1])));
            }
            assertThatThrownBy(() -> assemble(downgraded, List.of(), SENSITIVE, Set.of(), Set.of(), Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        Set<String> extra = new TreeSet<>(SENSITIVE);
        extra.add(CODE);
        assertThatThrownBy(() -> assemble(legacy(), List.of(), extra, Set.of(), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void threeRetiredCodesRemainCataloguedAbsentFromConstantsAndNeverGrantable() throws Exception {
        var mixed = snapshot(assemble(List.of()));
        var retired = mixed.entrySet().stream().filter(e -> e.getValue().endsWith(":true")).map(java.util.Map.Entry::getKey).toList();
        assertThat(retired).containsExactlyInAnyOrder("organization.workforce:sync-run:read",
                "organization.workforce:sync-run:execute", "organization.workforce:org-reconciliation:execute");
        Set<String> constants = new TreeSet<>();
        for (var field : Permission.Codes.class.getFields()) constants.add((String) field.get(null));
        for (String code : retired) {
            assertThat(constants).doesNotContain(code);
            assertThat(Permission.isLive(code)).isFalse();
            assertThatThrownBy(() -> Role.newCustom(new RoleId(UUID.randomUUID()), new RoleCode("synthetic"), "Synthetic", Set.of(code)))
                    .isInstanceOfSatisfying(RoleRuleException.class, error -> assertThat(error.violation()).isEqualTo(RoleViolation.UNKNOWN_PERMISSION));
            assertThatThrownBy(() -> assemble(legacy(), List.of(), SENSITIVE, Set.of(), Set.of(code), Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void administrativeAndReservedRoleSensitivityDoesNotDependOnSensitiveCodes() throws Exception {
        type(CATALOGUE);
        for (boolean administrative : List.of(false, true)) {
            Role role = Role.restore(new RoleId(UUID.randomUUID()), new RoleCode("synthetic"), "CUSTOM", administrative,
                    new RoleVersionId(UUID.randomUUID()), "Synthetic", "ENABLED", AccessScopeKind.SELF, Set.of(), Set.of(), 0, 0);
            assertThat(SensitivePermissions.roleIsSensitive(role)).isEqualTo(administrative);
        }
        Role reserved = Role.restore(new RoleId(UUID.randomUUID()), new RoleCode("super_admin"), "SYSTEM", false,
                new RoleVersionId(UUID.randomUUID()), "Synthetic reserved", "ENABLED", AccessScopeKind.ALL, Set.of(), Set.of(), 0, 0);
        assertThat(SensitivePermissions.roleIsSensitive(reserved)).isTrue();
        assertThatThrownBy(() -> Role.newCustom(new RoleId(UUID.randomUUID()), new RoleCode("Super_Admin"), "Synthetic", Set.of()))
                .isInstanceOfSatisfying(RoleRuleException.class, error -> assertThat(error.violation()).isEqualTo(RoleViolation.RESERVED_CODE));
        assertThat(AdministratorMode.PERMISSIONS).containsExactlyInAnyOrder("platform.identity:role:read", "platform.identity:role:update",
                "platform.identity:account-role:update", "platform.identity:session:delete", "organization.workforce:separation-hold:approve");
        assertThat(AdministratorMode.PLATFORM_ONLY).hasSize(4).doesNotContain("organization.workforce:separation-hold:approve");
    }

    @Test
    void allNineCurrentSystemRolesKeepIdentityScopeRevisionAndGrantSets() throws Exception {
        type(CATALOGUE);
        assertThat(roleSnapshot()).containsExactlyInAnyOrderElementsOf(lines("roles.tsv"));
    }

    static List<String> roleSnapshot() {
        return SystemRoleDeclaration.CURRENT.roles().stream().map(role -> String.join("\t", role.code().value(),
                role.roleId().value().toString(), role.definition().name(), role.definition().accessScope().name(),
                Boolean.toString(role.administrative()), Integer.toString(role.revisionNumber()),
                String.join(",", new TreeSet<>(role.definition().permissionCodes()))).stripTrailing()).sorted().toList();
    }
}
