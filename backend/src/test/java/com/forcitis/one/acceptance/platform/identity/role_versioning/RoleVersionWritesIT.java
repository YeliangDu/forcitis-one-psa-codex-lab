package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ADR-0034 decision 1 items 2, 4, 5, 6 and guard rules 5 (角色版本同理) and 7 (UpdateRole 每次修改恰好新增一个版本，内容不变时不新增；删除仍被
 * 持有的角色返回 409); RULE-345 revision 0034-Q3 (删除角色关闭最后一个版本、编码不得复用); ADR-0034-IMPL card rows 0034-G7-03 and 0034-G5-03.
 * Driven through the role management HTTP paths with the fixed, movable clock; checked on platform_identity.roles and role_versions.
 */
class RoleVersionWritesIT extends RoleVersioningSupport {

    static final TestDatabase postgres = TestPostgres.database(RoleVersionWritesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static String newCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Definition definition(String name, boolean enabled, String scope, String... codes) {
        return new Definition(name, enabled, scope, List.of(codes));
    }

    private static void assertCarries(VersionRow version, Definition definition) {
        assertThat(version.roleName()).as("role_name of %s", version).isEqualTo(definition.name());
        assertThat(version.roleStatus()).as("role_status of %s", version).isEqualTo(definition.status());
        assertThat(version.accessScopeKind()).as("access_scope_kind of %s", version).isEqualTo(definition.accessScope());
        assertThat(version.permissionCodes()).as("permission_codes of %s, normalised (C order, no duplicates)", version)
                .containsExactlyElementsOf(definition.permissionCodes());
    }

    /** ADR-0034 decision 1 item 2: MANUAL needs recorded_by, carries no declaration revision; a version replaced by the next keeps its closure columns empty. */
    private static void assertManualBy(VersionRow version, UUID administrator) {
        assertThat(version.changeChannelKind()).as("change_channel_kind of %s", version).isEqualTo("MANUAL");
        assertThat(version.recordedBy()).as("recorded_by of %s", version).isEqualTo(administrator);
        assertThat(version.declarationRevisionNumber()).as("declaration_revision_number of %s", version).isNull();
        assertThat(version.changeReason()).as("change_reason of %s", version).isNotBlank();
        assertThat(version.versionAuditEntryId()).as("version_audit_entry_id of %s", version).isNotNull();
    }

    private static void assertReplaced(VersionRow version) {
        assertThat(version.to()).as("closed: %s", version).isNotNull();
        assertThat(version.closedBy()).as("a replaced version has no closer of its own: %s", version).isNull();
        assertThat(version.closureReason()).as("a replaced version has no closure reason: %s", version).isNull();
        assertThat(version.closureAuditEntryId()).as("a replaced version has no closure audit entry: %s", version).isNull();
    }

    @Test
    @DisplayName("0034-G7-03 新建得版本 1（MANUAL、记录人为操作人）；改名、改范围、改权限、停用、启用各恰好新增一个版本，旧版本在新版本开始时刻关闭；内容不变的修改不新增版本、当前版本不变")
    void everyChangeAddsExactlyOneVersionAndNoChangeAddsNone() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = newCode("rv_upd");
        String base = "Role versioning " + code;
        Definition created = definition(base, true, "SELF", PLAIN_CODES.get(0));

        UUID role = createdRole(administrator, code, created);

        List<VersionRow> first = versionsOf(role);
        assertThat(first).as("creating a role writes version 1").hasSize(1);
        assertThat(first.getFirst().versionNumber()).isEqualTo(1);
        assertThat(first.getFirst().open()).isTrue();
        assertThat(first.getFirst().from()).as("ADR-0034 decision 1 item 6: the start is the injected clock").isEqualTo(NOW);
        assertCarries(first.getFirst(), created);
        assertManualBy(first.getFirst(), administrator.userId());

        List<Definition> changes = List.of(
                definition(base + " renamed", true, "SELF", PLAIN_CODES.get(0)),
                definition(base + " renamed", true, "ALL", PLAIN_CODES.get(0)),
                definition(base + " renamed", true, "ALL", PLAIN_CODES.get(0), PLAIN_CODES.get(1)),
                definition(base + " renamed", false, "ALL", PLAIN_CODES.get(0), PLAIN_CODES.get(1)),
                definition(base + " renamed", true, "ALL", PLAIN_CODES.get(0), PLAIN_CODES.get(1)),
                definition(base + " renamed", true, "ALL", PLAIN_CODES.get(1)));
        Instant at = NOW;
        for (int i = 0; i < changes.size(); i++) {
            at = at.plus(Duration.ofMinutes(5));
            clock.set(at);
            MockHttpServletResponse updated = updateRole(administrator, role, changes.get(i));
            assertThat(updated.getStatus()).as(updated.getContentAsString()).isEqualTo(200);

            List<VersionRow> versions = versionsOf(role);
            assertThat(versions).as("change %d adds exactly one version", i + 1).hasSize(i + 2);
            assertWellFormedHistory(versions);
            VersionRow current = versions.getLast();
            assertThat(current.open()).isTrue();
            assertThat(current.from()).as("the new version starts at the injected clock").isEqualTo(at);
            assertCarries(current, changes.get(i));
            assertManualBy(current, administrator.userId());
            assertReplaced(versions.get(versions.size() - 2));
        }

        UUID currentBefore = versionsOf(role).getLast().roleVersionId();
        clock.set(at.plus(Duration.ofMinutes(5)));
        MockHttpServletResponse unchanged = updateRole(administrator, role, changes.getLast());
        assertThat(unchanged.getStatus()).as(unchanged.getContentAsString()).isEqualTo(200);
        List<VersionRow> after = versionsOf(role);
        assertThat(after).as("ADR-0034 decision 1 item 4: a definition that did not change adds no version").hasSize(changes.size() + 1);
        assertThat(after.getLast().roleVersionId()).isEqualTo(currentBefore);
        assertThat(after.getLast().open()).isTrue();
    }

    @Test
    @DisplayName("0034-G7-03 删除仍被持有的角色：409 PLATFORM_IDENTITY_ROLE_IN_USE；当前版本仍开放、未写关闭列，角色仍在 GET /roles 中")
    void deletingAHeldRoleIsRefused() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = newCode("rv_held");
        UUID role = createdRole(administrator, code, definition("Role versioning " + code, true, "SELF", PLAIN_CODES.get(2)));
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID holder = newEmployeeId();
        employee(holder, unit, null);
        grantByOperations(holder, code);
        clock.set(NOW.plus(Duration.ofMinutes(5)));

        MockHttpServletResponse refused = deleteRole(administrator, role);

        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(409);
        assertThat((String) JsonPath.read(refused.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_ROLE_IN_USE");
        List<VersionRow> versions = versionsOf(role);
        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().open()).as("the refused delete closed nothing").isTrue();
        assertThat(versions.getFirst().closedBy()).isNull();
        assertThat(listedRoleCodes(administrator)).contains(code);
    }

    @Test
    @DisplayName("0034-G7-03 删除角色：204；roles 行与全部版本仍在，最后一个版本在删除时刻关闭并写关闭人、关闭理由、关闭审计条目，无当前版本；GET /roles 不再列出；以原编码、全大写、大小写混合新建均被拒（409 PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE），不新增角色行")
    void deletingClosesTheLastVersionAndTheCodeIsNeverReused() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = newCode("rv_del");
        UUID role = createdRole(administrator, code, definition("Role versioning " + code, true, "SELF", PLAIN_CODES.get(3)));
        clock.set(NOW.plus(Duration.ofMinutes(10)));
        assertThat(updateRole(administrator, role, definition("Role versioning " + code + " v2", true, "SELF", PLAIN_CODES.get(3))).getStatus()).isEqualTo(200);
        Instant deletedAt = NOW.plus(Duration.ofMinutes(20));
        clock.set(deletedAt);

        MockHttpServletResponse deleted = deleteRole(administrator, role);

        assertThat(deleted.getStatus()).as(deleted.getContentAsString()).isEqualTo(204);
        assertThat(count("select count(*) from roles where role_id = ?", role)).as("ADR-0034 decision 1 item 5: the roles row stays").isEqualTo(1);
        List<VersionRow> versions = versionsOf(role);
        assertThat(versions).as("every version stays").hasSize(2);
        assertWellFormedHistory(versions);
        assertThat(versions.stream().filter(VersionRow::open)).as("a deleted role has no current version").isEmpty();
        VersionRow last = versions.getLast();
        assertThat(last.to()).as("closed at the moment of the delete").isEqualTo(deletedAt);
        assertThat(last.closedBy()).as("closed_by is the one who deleted").isEqualTo(administrator.userId());
        assertThat(last.closureReason()).isNotBlank();
        assertThat(last.closureAuditEntryId()).isNotNull();
        assertReplaced(versions.getFirst());

        assertThat(listedRoleCodes(administrator)).as("GET /roles lists current roles only").doesNotContain(code);

        String upper = code.toUpperCase(java.util.Locale.ROOT);
        String mixed = Character.toUpperCase(code.charAt(0)) + code.substring(1);
        List<String> attempts = new ArrayList<>(List.of(code, upper, mixed));
        for (String attempt : attempts) {
            MockHttpServletResponse reused = createRole(administrator, attempt, definition("Role versioning reuse " + attempt + " " + UUID.randomUUID(), true, "SELF"));
            assertThat(reused.getStatus()).as("0034-Q3: the code %s of a deleted role is not reused: %s", attempt, reused.getContentAsString()).isEqualTo(409);
            assertThat((String) JsonPath.read(reused.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE");
        }
        assertThat(count("select count(*) from roles where lower(code) = lower(?)", code)).isEqualTo(1);
    }

    @Test
    @DisplayName("0034-G5-03 角色版本时钟回拨：注入时钟回拨到当前版本开始之前再修订，新版本开始时刻 = 当前版本开始时刻（不早于它，也不早于已关闭版本的最大失效时刻），被接替的版本得零长区间；时钟再前进后新版本从注入时钟开始")
    void aVersionNeverStartsBeforeTheCurrentOneWhenTheClockGoesBack() throws Exception {
        SignedIn administrator = roleAdministrator();
        String code = newCode("rv_clock");
        String base = "Role versioning " + code;
        UUID role = createdRole(administrator, code, definition(base + " 1", true, "SELF", PLAIN_CODES.get(4)));
        Instant t2 = NOW.plus(Duration.ofMinutes(20));
        clock.set(t2);
        assertThat(updateRole(administrator, role, definition(base + " 2", true, "SELF", PLAIN_CODES.get(4))).getStatus()).isEqualTo(200);

        clock.set(NOW.plus(Duration.ofMinutes(10)));
        assertThat(updateRole(administrator, role, definition(base + " 3", true, "SELF", PLAIN_CODES.get(4))).getStatus()).isEqualTo(200);
        clock.set(NOW.minus(Duration.ofMinutes(10)));
        assertThat(updateRole(administrator, role, definition(base + " 4", true, "ALL", PLAIN_CODES.get(4))).getStatus()).isEqualTo(200);
        Instant t3 = NOW.plus(Duration.ofMinutes(30));
        clock.set(t3);
        assertThat(updateRole(administrator, role, definition(base + " 5", false, "ALL", PLAIN_CODES.get(4))).getStatus()).isEqualTo(200);

        List<VersionRow> versions = versionsOf(role);
        assertThat(versions).hasSize(5);
        assertWellFormedHistory(versions);
        assertThat(versions.stream().map(VersionRow::from).toList()).as("greatest(:now, current start, max closed end)")
                .containsExactly(NOW, t2, t2, t2, t3);
        assertThat(versions.get(1).to()).as("version 2 is replaced at its own start: a zero-length interval").isEqualTo(t2);
        assertThat(versions.get(2).to()).isEqualTo(t2);
        assertThat(versions.get(3).to()).isEqualTo(t3);
        for (int i = 1; i < versions.size(); i++) {
            Instant start = versions.get(i).from();
            for (VersionRow earlier : versions.subList(0, i)) {
                assertThat(start).as("version %d starts no earlier than version %d's start and end", i + 1, earlier.versionNumber())
                        .isAfterOrEqualTo(earlier.from()).isAfterOrEqualTo(earlier.to());
            }
        }
        assertThat(versions.getLast().roleName()).isEqualTo(base + " 5");
    }
}
