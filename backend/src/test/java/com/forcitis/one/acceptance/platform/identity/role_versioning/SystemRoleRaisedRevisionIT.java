package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.launch;
import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.migratedDatabase;
import static com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.rows;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationEnvironment.Outcome;
import com.forcitis.one.acceptance.platform.identity.role_versioning.SystemRoleDeclarationIT.SeededRole;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 decision 1 item 7 (声明修订号大于库中该角色 SEED 版本的最大修订号 → 关闭当前版本、追加新版本，渠道 SEED，记修订号，理由写声明出处)
 * and guard rule 8; ADR-0034-IMPL card row 0034-G8-01 (修订号提高追加 SEED 版本). The database is first started on the code's own declaration
 * (revision 1), then on a declaration whose employee role is at revision 2 with another definition (RaisedDeclarationLauncher).
 * <p>
 * The test is written to catch a broken write path: it compares the whole appended row with the declared definition (name, status, scope,
 * every permission code, revision number, channel, no recorder, no request, a reason, an audit entry), the closing of the old version, the
 * seamless interval, the untouched old version, the untouched other roles, and that starting again adds nothing. A reconciliation that
 * appends nothing, appends the old content, writes a wrong revision or channel, or leaves the old version open fails at least one of them.
 */
class SystemRoleRaisedRevisionIT {

    private static final String EMPLOYEE = RaisedDeclarationLauncher.EMPLOYEE;

    private static final String VERSIONS = """
            select version_number, effective_to_at is null, role_name, role_status, access_scope_kind, permission_codes, change_channel_kind,
                   declaration_revision_number, recorded_by, authorization_request_id, change_reason, closed_by, closure_reason, closure_audit_entry_id,
                   version_audit_entry_id::text, role_version_id::text
              from platform_identity.role_versions where role_id = ?::uuid order by version_number""";

    private static List<List<Object>> versionsOf(String database, String roleId) throws SQLException {
        return rows(database, VERSIONS, roleId);
    }

    private static Map<String, List<List<Object>>> allVersions(String database) throws SQLException {
        Map<String, List<List<Object>>> all = new LinkedHashMap<>();
        for (SeededRole role : SystemRoleDeclarationIT.SEED) {
            all.put(role.roleId(), versionsOf(database, role.roleId()));
        }
        return all;
    }

    private static List<String> sorted(Object codes) {
        return ((List<?>) codes).stream().map(String::valueOf).sorted().toList();
    }

    @Test
    @DisplayName("0034-G8-01 修订号提高：声明中员工角色为修订 2 且定义不同，启动后员工角色恰有两个版本，版本 1 在版本 2 开始时刻关闭且内容原样，版本 2 当前、渠道 SEED、修订号 2、内容逐项等于声明、无记录人无授权申请、有理由与审计条目；其他五个角色不变；再次以同一声明启动不再新增版本")
    void aHigherRevisionAppendsASeedVersion() throws Exception {
        String database = migratedDatabase("forcitis_one_roles_raised");
        Outcome first = launch(database);
        assertThat(first.started()).as(first.output()).isTrue();
        Map<String, List<List<Object>>> before = allVersions(database);
        assertThat(before.get(EMPLOYEE)).as("the first start wrote version 1 only").hasSize(1);

        Outcome raised = launch(database, RaisedDeclarationLauncher.class);

        assertThat(raised.started()).as(raised.output()).isTrue();
        List<List<Object>> employee = versionsOf(database, EMPLOYEE);
        assertThat(employee).as("the raised revision appends exactly one version: %s", raised.output()).hasSize(2);

        List<Object> one = employee.get(0);
        assertThat(one).as("version 1 is kept as it was, only closed").hasSize(before.get(EMPLOYEE).getFirst().size());
        assertThat(one.get(0)).isEqualTo(1);
        assertThat(one.get(1)).as("version 1 is closed (a role has one current version)").isEqualTo(false);
        List<Object> oneBefore = before.get(EMPLOYEE).getFirst();
        for (int column : new int[] {0, 2, 3, 4, 6, 7, 8, 9, 10, 14, 15}) {
            assertThat(one.get(column)).as("version 1 column %d is unchanged", column).isEqualTo(oneBefore.get(column));
        }
        assertThat(sorted(one.get(5))).as("version 1 keeps its permission codes").isEqualTo(sorted(oneBefore.get(5)));
        assertThat(one.get(11)).as("a version replaced by the next has no closer of its own (card 7.1: closure columns only for a delete)").isNull();
        assertThat(one.get(12)).isNull();
        assertThat(one.get(13)).isNull();

        List<Object> two = employee.get(1);
        assertThat(two.get(0)).isEqualTo(2);
        assertThat(two.get(1)).as("version 2 is the current version").isEqualTo(true);
        assertThat(two.get(2)).as("role_name is the declared one").isEqualTo(RaisedDeclarationLauncher.RAISED_NAME);
        assertThat(two.get(3)).as("role_status").isEqualTo("ENABLED");
        assertThat(two.get(4)).as("access_scope_kind").isEqualTo(RaisedDeclarationLauncher.RAISED_SCOPE.name());
        assertThat(sorted(two.get(5))).as("permission_codes are the declared ones, all of them and no other").isEqualTo(RaisedDeclarationLauncher.RAISED_CODES.stream().sorted().toList());
        assertThat(two.get(6)).as("change_channel_kind").isEqualTo("SEED");
        assertThat(two.get(7)).as("declaration_revision_number is the declared revision").isEqualTo(RaisedDeclarationLauncher.RAISED_REVISION);
        assertThat(two.get(8)).as("recorded_by of a SEED version").isNull();
        assertThat(two.get(9)).as("authorization_request_id of a SEED version").isNull();
        assertThat((String) two.get(10)).as("change_reason").isNotBlank();
        assertThat(two.get(11)).as("closed_by of an open version").isNull();
        assertThat(two.get(12)).isNull();
        assertThat(two.get(13)).isNull();
        assertThat(two.get(14)).as("version_audit_entry_id: the audit entry written with the version (card 7.1, A-6)").isNotNull();
        assertThat(two.get(15)).as("a new version has a new id").isNotEqualTo(oneBefore.get(15));

        Object seamless = rows(database, """
                select v2.effective_from_at = v1.effective_to_at and v2.effective_from_at >= v1.effective_from_at
                  from platform_identity.role_versions v1 join platform_identity.role_versions v2 on v2.role_id = v1.role_id
                 where v1.role_id = ?::uuid and v1.version_number = 1 and v2.version_number = 2""", EMPLOYEE).getFirst().getFirst();
        assertThat(seamless).as("ADR-0034 decision 1 item 6: version 2 starts where version 1 was closed, not before version 1 began").isEqualTo(true);
        String auditEntryId = String.valueOf(two.get(14));
        assertThat(raised.linesWith(RaisedDeclarationLauncher.AUDIT_PROBE)).as("the audit query of the raised start answered: %s", raised.output())
                .noneMatch(line -> line.contains("failed"));
        assertThat(raised.linesWith(RaisedDeclarationLauncher.AUDIT_PROBE + " " + auditEntryId + " "))
                .as("the audit entry of version 2 exists, read through the audit module's own query: %s", raised.output())
                .containsExactly(RaisedDeclarationLauncher.AUDIT_PROBE + " " + auditEntryId + " " + RaisedDeclarationLauncher.OPENED);

        Map<String, List<List<Object>>> after = allVersions(database);
        for (SeededRole role : SystemRoleDeclarationIT.SEED) {
            if (!role.roleId().equals(EMPLOYEE)) {
                assertThat(after.get(role.roleId())).as("%s was declared unchanged: no version written, none closed", role.code()).isEqualTo(before.get(role.roleId()));
            }
        }
        assertThat(rows(database, "select count(*) from platform_identity.roles").getFirst().getFirst()).as("no role row added").isEqualTo(6L);

        Outcome again = launch(database, RaisedDeclarationLauncher.class);
        assertThat(again.started()).as(again.output()).isTrue();
        assertThat(allVersions(database)).as("starting again on the same revision adds no version").isEqualTo(after);
    }
}
