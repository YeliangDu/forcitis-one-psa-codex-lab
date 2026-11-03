package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.execute;
import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.launch;
import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.migratedDatabase;
import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.rows;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.Outcome;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 decision 1 item 7 (系统角色定义由代码声明，启动时对账) and guard rule 8 (SystemRoleDeclarationIntegrationTest); ADR-0034-IMPL card
 * rows 0034-G8-01 (空库启动建出六个系统角色，role_id 等于声明中的固定值，版本 1 记声明修订号、渠道 SEED；重启不新增版本；修订号更小告警不写；
 * 相等而内容不同启动失败；两实例同时启动结果相同；对账先于 ApplicationRunner、事件监听与重投、定时任务、HTTP 就绪) and 0034-G8-02 (种子转声明对照：
 * 改前基线迁移后的「角色 → 权限码」与改后对账后的当前版本逐码相等), with card 7.2 (六个 role_id 沿用 019a0000-0000-7000-8000-000000000101 至 …106，
 * 声明修订号 1). Since #872 the declaration also holds a seventh system role, 人事专员 personnel_specialist (lead decision 2026-10-30 on the
 * user decision of 2026-10-30 named 人事专员), holding organization.workforce:personnel-command:create; it is not in the 「改前」 seed, so
 * SEED stays the six and the role is checked on its own. Observed only from outside: the child process's outcome and output, and the rows of platform_identity.
 */
class SystemRoleDeclarationIT {

    static final String SUPER_ADMIN = "019a0000-0000-7000-8000-000000000101";
    static final String EMPLOYEE = "019a0000-0000-7000-8000-000000000102";

    /**
     * One system role as the identity baseline seeded it on main f62af6ae8 (V20261014130000 :523-586), the 「改前」 of card 0034-G8-02, with one
     * ruled change: org_data_admin's two sync run codes became organization.workforce:org-reconciliation:execute when #934 removed the old
     * sync (lead ruling 2026-11-02, point 4: the reconciliation is kept under that code with the same grants), and #907 granted it
     * organization.workforce:org-structure-command:create (card #907: the permission for 组织数据管理员, permissions.yaml updated).
     */
    record SeededRole(String roleId, String code, String name, String accessScope, boolean administrative, List<String> permissionCodes) {
    }

    static final List<SeededRole> SEED = List.of(
            new SeededRole(SUPER_ADMIN, "super_admin", "超级管理员", "ALL", true, List.of()),
            new SeededRole(EMPLOYEE, "employee", "员工", "SELF", false, List.of("platform.approval:task:approve", "platform.approval:task:read",
                    "platform.audit:own-login-record:read", "platform.file:file:create", "platform.file:file:read")),
            new SeededRole("019a0000-0000-7000-8000-000000000103", "process_administrator", "流程管理员", "SELF", false, List.of()),
            new SeededRole("019a0000-0000-7000-8000-000000000104", "org_data_admin", "组织数据管理员", "ALL", false, List.of(
                    "organization.attendance:locked-day:update", "organization.attendance:work-calendar:read", "organization.attendance:work-calendar:update",
                    "organization.workforce:legal-entity-attribution-correction:create", "organization.workforce:legal-entity:update",
                    "organization.workforce:org-reconciliation:execute", "organization.workforce:org-structure-command:create", "organization.workforce:org-unit:read",
                    "platform.integration:source-reference:update", "platform.numbering:numbering-rule:read", "platform.numbering:numbering-rule:update")),
            new SeededRole("019a0000-0000-7000-8000-000000000105", "business_admin", "业务管理员", "SELF", false, List.of(
                    "platform.integration:source-reference:approve", "platform.numbering:numbering-rule:approve", "platform.numbering:numbering-rule:read")),
            new SeededRole("019a0000-0000-7000-8000-000000000106", "security_auditor", "安全审计员", "ALL", false, List.of(
                    "organization.workforce:separation-hold:approve", "platform.audit:audit-entry:export", "platform.audit:audit-entry:read")));

    /** The seventh system role, declared by #872; its role_id is the declaration's choice and is not pinned here. */
    static final String PERSONNEL_SPECIALIST = "personnel_specialist";
    static final String PERSONNEL_COMMAND_CREATE = "organization.workforce:personnel-command:create";

    static final String VERSIONS = """
            select role_version_id::text, version_number, effective_to_at is null, change_channel_kind, declaration_revision_number, recorded_by,
                   authorization_request_id, change_reason, role_name, role_status, access_scope_kind, permission_codes
              from platform_identity.role_versions where role_id = ?::uuid order by version_number""";

    private static Outcome firstStart;

    /** The first start on a database the migrations alone built, shared by the tests that only read what it wrote. */
    private static synchronized Outcome firstStart() throws Exception {
        if (firstStart == null) {
            firstStart = launch(migratedDatabase(SystemRoleDeclarationEnvironment.FIRST_DATABASE));
        }
        return firstStart;
    }

    private static List<List<Object>> versionsOf(String database, String roleId) throws SQLException {
        return rows(database, VERSIONS, roleId);
    }

    private static Map<String, List<String>> versionIds(String database) throws SQLException {
        Map<String, List<String>> ids = new LinkedHashMap<>();
        for (SeededRole role : SEED) {
            ids.put(role.roleId(), versionsOf(database, role.roleId()).stream().map(row -> (String) row.get(0)).toList());
        }
        return ids;
    }

    /** ADR-0034 decision 1 item 7 and card 7.2: a role the declaration wrote has exactly one version, number 1, open, SEED, revision 1 (org_data_admin 3), no recorder, no request. */
    private static void assertOneSeedVersion(String database) throws SQLException {
        for (SeededRole role : SEED) {
            List<List<Object>> versions = versionsOf(database, role.roleId());
            assertThat(versions).as("%s (%s) has exactly one version", role.code(), role.roleId()).hasSize(1);
            List<Object> version = versions.getFirst();
            assertThat(version.get(1)).as("version_number of %s", role.code()).isEqualTo(1);
            assertThat(version.get(2)).as("%s's version is current", role.code()).isEqualTo(true);
            assertThat(version.get(3)).as("change_channel_kind of %s", role.code()).isEqualTo("SEED");
            // org_data_admin's declared content changed (sync run codes -> org-reconciliation:execute): under the declaration convention changed content raises
            // the revision, so existing dev databases reconcile instead of refusing to start (lead ruling 2026-11-02 on #1014); #907 changed it again.
            int revision = role.code().equals("org_data_admin") ? 3 : 1;
            assertThat(version.get(4)).as("declaration_revision_number of %s (card 7.2: 声明修订号 1; org_data_admin 2 since #934, 3 since #907)", role.code()).isEqualTo(revision);
            assertThat(version.get(5)).as("recorded_by of a SEED version").isNull();
            assertThat(version.get(6)).as("authorization_request_id of a SEED version").isNull();
            assertThat((String) version.get(7)).as("change_reason of %s", role.code()).isNotBlank();
        }
    }

    /** The 人事专员 role: SYSTEM, not administrative, holding the entry permission, with exactly one current SEED version 1 of revision 1. */
    private static void assertPersonnelSpecialistSeeded(String database) throws SQLException {
        List<List<Object>> role = rows(database, "select role_id::text, kind, is_administrative from platform_identity.roles where code = ?", PERSONNEL_SPECIALIST);
        assertThat(role).as("the declared role %s", PERSONNEL_SPECIALIST).hasSize(1);
        assertThat(role.getFirst().subList(1, 3)).as("kind and is_administrative of %s", PERSONNEL_SPECIALIST).isEqualTo(List.of("SYSTEM", false));
        List<List<Object>> versions = versionsOf(database, (String) role.getFirst().getFirst());
        assertThat(versions).as("%s has exactly one version", PERSONNEL_SPECIALIST).hasSize(1);
        List<Object> version = versions.getFirst();
        assertThat(List.of(version.get(1), version.get(2), version.get(3), version.get(4))).as("number, current, channel and revision of %s", PERSONNEL_SPECIALIST)
                .isEqualTo(List.of(1, true, "SEED", 1));
        assertThat(version.get(5)).as("recorded_by of a SEED version").isNull();
        assertThat(version.get(6)).as("authorization_request_id of a SEED version").isNull();
        assertThat(((List<?>) version.get(11)).stream().map(String::valueOf).toList()).as("permission codes of %s", PERSONNEL_SPECIALIST)
                .contains(PERSONNEL_COMMAND_CREATE);
    }

    /** A newer SEED version of the employee role written straight into the database: closes version 1 and opens version 2 without one of its codes. */
    private static void writeSeedVersionTwo(String database, int revision) throws SQLException {
        execute(database, """
                update platform_identity.role_versions set effective_to_at = effective_from_at + interval '1 second', updated_at = effective_from_at + interval '1 second'
                 where role_id = ?::uuid and version_number = 1 and effective_to_at is null""", EMPLOYEE);
        execute(database, """
                insert into platform_identity.role_versions (role_version_id, role_id, version_number, effective_from_at, effective_to_at, role_name, role_status,
                       access_scope_kind, permission_codes, change_channel_kind, recorded_by, change_reason, authorization_request_id,
                       declaration_revision_number, version_audit_entry_id, closed_by, closure_reason, closure_audit_entry_id, created_at, updated_at)
                select ?, role_id, 2, effective_to_at, null, role_name, role_status, access_scope_kind, array_remove(permission_codes, 'platform.file:file:create'),
                       'SEED', null, 'acceptance: a declaration revision the database already holds', null, ?, ?, null, null, null, effective_to_at, effective_to_at
                  from platform_identity.role_versions where role_id = ?::uuid and version_number = 1""", UUID.randomUUID(), revision, UUID.randomUUID(), EMPLOYEE);
        assertThat(versionsOf(database, EMPLOYEE)).as("the fixture wrote version 2 of the employee role").hasSize(2);
    }

    @Test
    @DisplayName("0034-G8-01 空库启动：建出七个系统角色——六个沿用基线种子（role_id 为声明固定值 019a…101 至 …106、kind SYSTEM、编码与是否管理员角色同声明）加人事专员 personnel_specialist（#872），每个恰有版本 1：当前、渠道 SEED、声明修订号 1（组织数据管理员为 3，#934 与 #907 各改了一次其声明内容）、无记录人、无授权申请")
    void anEmptyDatabaseGetsTheSixSystemRoles() throws Exception {
        Outcome outcome = firstStart();

        assertThat(outcome.started()).as(outcome.output()).isTrue();
        String database = SystemRoleDeclarationEnvironment.FIRST_DATABASE;
        List<List<Object>> roles = rows(database, "select role_id::text, code, kind, is_administrative from platform_identity.roles where code <> ? order by role_id",
                PERSONNEL_SPECIALIST);
        List<List<Object>> expected = new ArrayList<>();
        for (SeededRole role : SEED) {
            expected.add(List.of(role.roleId(), role.code(), "SYSTEM", role.administrative()));
        }
        assertThat(roles).as("the roles a start on an empty database writes, besides personnel_specialist").isEqualTo(expected);
        assertOneSeedVersion(database);
        assertPersonnelSpecialistSeeded(database);
    }

    @Test
    @DisplayName("0034-G8-01 对账时点：最早阶段的生命周期 bean 启动时（先于 Web 服务器、调度器、事件监听容器）与第一个 ApplicationRunner 运行时，七个系统角色都已有当前 SEED 版本")
    void theReconciliationIsDoneBeforeAnythingElseRuns() throws Exception {
        Outcome outcome = firstStart();

        assertThat(outcome.started()).as(outcome.output()).isTrue();
        assertThat(outcome.linesWith(SystemRoleDeclarationLauncher.PROBE + " " + SystemRoleDeclarationLauncher.LIFECYCLE))
                .as(outcome.output()).singleElement().asString().endsWith(" open-seed-versions=7");
        assertThat(outcome.linesWith(SystemRoleDeclarationLauncher.PROBE + " " + SystemRoleDeclarationLauncher.RUNNER))
                .as(outcome.output()).singleElement().asString().endsWith(" open-seed-versions=7");
    }

    @Test
    @DisplayName("0034-G8-02 种子转声明逐码对照：对账后六个系统角色的当前版本（名称、状态 ENABLED、范围类型、权限码集合）与改前基线种子逐项相等，差异为空")
    void theDeclarationCarriesTheSeedCodeForCode() throws Exception {
        Outcome outcome = firstStart();
        assertThat(outcome.started()).as(outcome.output()).isTrue();

        List<String> differences = new ArrayList<>();
        for (SeededRole role : SEED) {
            List<List<Object>> current = rows(SystemRoleDeclarationEnvironment.FIRST_DATABASE, """
                    select role_name, role_status, access_scope_kind, permission_codes from platform_identity.role_versions
                     where role_id = ?::uuid and effective_to_at is null""", role.roleId());
            if (current.size() != 1) {
                differences.add(role.code() + ": " + current.size() + " current versions");
                continue;
            }
            List<Object> version = current.getFirst();
            List<Object> expected = List.of(role.name(), "ENABLED", role.accessScope(), role.permissionCodes().stream().sorted().toList());
            List<Object> actual = List.of(version.get(0), version.get(1), version.get(2), ((List<?>) version.get(3)).stream().map(String::valueOf).sorted().toList());
            if (!expected.equals(actual)) {
                differences.add(role.code() + ": seed " + expected + " / declaration " + actual);
            }
        }
        assertThat(differences).as("seed -> declaration, code for code").isEmpty();
    }

    @Test
    @DisplayName("0034-G8-01 声明不变时重启：启动成功，七个系统角色仍各只有版本 1，版本 ID 不变")
    void aRestartAddsNoVersion() throws Exception {
        String database = migratedDatabase("forcitis_one_roles_restart");
        Outcome first = launch(database);
        assertThat(first.started()).as(first.output()).isTrue();
        Map<String, List<String>> before = versionIds(database);

        Outcome again = launch(database);

        assertThat(again.started()).as(again.output()).isTrue();
        assertOneSeedVersion(database);
        assertThat(versionIds(database)).isEqualTo(before);
    }

    @Test
    @DisplayName("0034-G8-01 声明修订号小于库中 SEED 版本的最大修订号：启动成功，写一条含角色 ID 的 ALERT，不写版本，库中当前版本不变（其他系统角色亦不变）")
    void aSmallerRevisionIsAlertedAndNotWritten() throws Exception {
        String database = migratedDatabase("forcitis_one_roles_older_code");
        Outcome first = launch(database);
        assertThat(first.started()).as(first.output()).isTrue();
        writeSeedVersionTwo(database, 2);
        Map<String, List<String>> before = versionIds(database);

        Outcome older = launch(database);

        assertThat(older.started()).as(older.output()).isTrue();
        assertThat(older.lines()).as("ALERT for the employee role: declaration revision 1 < database revision 2").anySatisfy(line ->
                assertThat(line).contains("ALERT").contains(EMPLOYEE));
        assertThat(versionIds(database)).as("nothing written").isEqualTo(before);
        List<List<Object>> employee = versionsOf(database, EMPLOYEE);
        assertThat(employee.getLast().get(2)).as("version 2 stays current").isEqualTo(true);
        assertThat(employee.getLast().get(4)).isEqualTo(2);
    }

    @Test
    @DisplayName("0034-G8-01 声明修订号与库中相等而内容不同：进程不启动（退出码非 0），输出含 ALERT，不写版本")
    void anEqualRevisionWithOtherContentStopsTheStart() throws Exception {
        String database = migratedDatabase("forcitis_one_roles_same_revision");
        Outcome first = launch(database);
        assertThat(first.started()).as(first.output()).isTrue();
        writeSeedVersionTwo(database, 1);
        Map<String, List<String>> before = versionIds(database);

        Outcome refused = launch(database);

        assertThat(refused.started()).as(refused.output()).isFalse();
        assertThat(refused.exitCode()).as(refused.output()).isNotZero();
        assertThat(refused.lines()).as(refused.output()).anySatisfy(line -> assertThat(line).contains("ALERT"));
        assertThat(versionIds(database)).as("nothing written").isEqualTo(before);
    }

    /**
     * Takes every role row and role version out again, the way card 3.3 allows a test to clear the history tables (the append-only triggers
     * are switched off inside the fixture only, after NumberingFixtures). Used once, so that two instances meet on a database without role
     * data while the process engine's own tables, which it creates at its first start and whose creation is not safe to race, already exist.
     */
    private static void clearRoleData(String database) throws SQLException {
        execute(database, "alter table platform_identity.role_versions disable trigger user");
        try {
            execute(database, "delete from platform_identity.role_versions");
            execute(database, "delete from platform_identity.roles");
        } finally {
            execute(database, "alter table platform_identity.role_versions enable trigger user");
        }
    }

    @Test
    @DisplayName("0034-G8-01 两个实例同时在无角色数据的库上启动：都启动成功，结果与单实例相同（七个系统角色，各恰有版本 1）")
    void twoInstancesStartingTogetherEndAlike() throws Exception {
        String database = migratedDatabase("forcitis_one_roles_together");
        Outcome warmUp = launch(database);
        assertThat(warmUp.started()).as(warmUp.output()).isTrue();
        clearRoleData(database);
        assertThat(rows(database, "select count(*) from platform_identity.roles").getFirst().getFirst()).as("no role data before the two starts").isEqualTo(0L);

        CompletableFuture<Outcome> one = CompletableFuture.supplyAsync(() -> start(database));
        CompletableFuture<Outcome> other = CompletableFuture.supplyAsync(() -> start(database));
        Outcome a = one.join();
        Outcome b = other.join();

        assertThat(a.started()).as(a.output()).isTrue();
        assertThat(b.started()).as(b.output()).isTrue();
        assertThat(rows(database, "select count(*) from platform_identity.roles").getFirst().getFirst()).isEqualTo(7L);
        assertOneSeedVersion(database);
        assertPersonnelSpecialistSeeded(database);
    }

    private static Outcome start(String database) {
        try {
            return launch(database);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
