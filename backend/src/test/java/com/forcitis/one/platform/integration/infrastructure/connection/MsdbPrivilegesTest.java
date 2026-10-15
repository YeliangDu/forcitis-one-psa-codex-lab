package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Finding;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SqlError;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Unmet;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** C-14 (f) opus review H-1 as one-coordinator ruled it (2026-10-15); live: ReadOnlyVerificationCommandIT and ReadOnlyAccountCheckIT. */
class MsdbPrivilegesTest {

    final FakeCatalog catalog = new FakeCatalog().rdsOwnUsers();

    private Unmet f() {
        Verdict verdict = ReadOnlyBaseline.check(catalog);
        assertThat(verdict.unmet()).extracting(Unmet::item).containsExactly('f');
        return verdict.unmet().getFirst();
    }

    private void direct(String row) {
        String[] parts = row.split(" ", 3);
        catalog.ownUserPermissions.get("msdb").add(new OwnUserPermission(parts[0], parts[1], parts[2]));
    }

    private String report() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ReadOnlyVerificationCommand.report(ReadOnlyBaseline.check(catalog), new PrintStream(out, true, StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8);
    }

    private void assertPrivilegeViolation() {
        Unmet f = f();
        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1, List.of(new DatabaseLocator("msdb"))));
    }

    @Test
    @DisplayName("复审 H-1 不误判：one-ops 在 RDS 实测的直接授权形状（库级 GRANT CONNECT，对象级 GRANT SELECT、GRANT EXECUTE、DENY SELECT）且六项有效权限全假时通过")
    void theOneOpsShapePasses() {
        List.of("OBJECT_OR_COLUMN G SELECT", "OBJECT_OR_COLUMN G SELECT", "OBJECT_OR_COLUMN G EXECUTE", "OBJECT_OR_COLUMN D SELECT", "OBJECT_OR_COLUMN D EXECUTE")
                .forEach(this::direct);

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.passed()).as(verdict.described().toString()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DATABASE G CONTROL", "DATABASE G ALTER ANY ROLE", "DATABASE G CREATE PROCEDURE", "DATABASE G SELECT", "DATABASE D CONNECT",
            "DATABASE W CONNECT", "DATABASE_PRINCIPAL G IMPERSONATE", "SCHEMA G ALTER", "SCHEMA D ALTER", "OBJECT_OR_COLUMN W EXECUTE", "OBJECT_OR_COLUMN W SELECT",
            "OBJECT_OR_COLUMN G UPDATE", "OBJECT_OR_COLUMN G CONTROL", "TYPE G REFERENCES"})
    @DisplayName("复审 H-1 直接授权类别清单：DATABASE 只许 G CONNECT，OBJECT_OR_COLUMN 只许 G SELECT、G EXECUTE 与任意 D，其他 class 与带转授权一律违规（有效权限全假也违规）")
    void aDirectRowOutsideTheAllowListIsAViolation(String row) {
        direct(row);

        assertPrivilegeViolation();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("effective")
    @DisplayName("复审 H-1 有效权限：CONTROL、ALTER、ALTER ANY ROLE、ALTER ANY USER、ALTER ANY SCHEMA、CREATE PROCEDURE 任一为真即违规（直接授权都在清单内也违规）")
    void anEffectivePrivilegeIsAViolation(String privilege) {
        catalog.msdbEffectivePermissions.put(privilege, 1);

        assertPrivilegeViolation();
    }

    static List<String> effective() {
        return MsdbPrivileges.EFFECTIVE;
    }

    @Test
    @DisplayName("复审 H-1：直接授权与六项有效权限同时越线时仍只计 1（库数），一个定位行")
    void theCountIsTheNumberOfDatabases() {
        MsdbPrivileges.EFFECTIVE.forEach(p -> catalog.msdbEffectivePermissions.put(p, 1));
        direct("DATABASE G CONTROL");
        direct("DATABASE_PRINCIPAL G IMPERSONATE");

        assertPrivilegeViolation();
        assertThat(report().lines()).contains("(f) VIOLATED reason=SYSTEM_DATABASE_OWN_USER_PRIVILEGE count=1",
                "  (f) violation reason=SYSTEM_DATABASE_OWN_USER_PRIVILEGE database=\"msdb\"");
    }

    @Test
    @DisplayName("复审 H-1：problem、记录的 toString 与核实命令输出不含权限名与对象名")
    void namesNoPermission() {
        catalog.msdbEffectivePermissions.put("ALTER ANY ROLE", 1);
        direct("DATABASE_PRINCIPAL G IMPERSONATE");

        Unmet f = f();

        assertThat(f.problem()).doesNotContain("IMPERSONATE").doesNotContain("ALTER ANY ROLE").doesNotContain("CONTROL");
        assertThat(ReadOnlyBaseline.check(catalog).toString()).doesNotContain("IMPERSONATE").doesNotContain("ALTER ANY ROLE").doesNotContain("msdb");
        assertThat(report()).doesNotContain("IMPERSONATE").doesNotContain("ALTER ANY ROLE");
    }

    @Test
    @DisplayName("复审 H-1：msdb 本人用户的角色已越线（SYSTEM_DATABASE_OWN_USER）时只报角色越线，不再另报权限越线")
    void aRoleBeyondTheAgentRolesIsReportedOnce() {
        catalog.ownUser("msdb", "SQLAgentUserRole", "db_owner");
        MsdbPrivileges.EFFECTIVE.forEach(p -> catalog.msdbEffectivePermissions.put(p, 1));

        assertThat(f().findings()).extracting(Finding::reason, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER, List.of(new DatabaseLocator("msdb"))));
    }

    @Test
    @DisplayName("复审 H-1：msdb 叫不出名称的角色（判定不了）不挡权限检查，有效权限为真仍违规")
    void anUnnamedRoleDoesNotSkipThePrivilegeCheck() {
        catalog.ownUsers.get("msdb").add(new SystemDatabaseUsers.Membership(true, null));
        catalog.msdbEffectivePermissions.put("CONTROL", 1);

        Unmet f = f();

        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason).containsExactlyInAnyOrder(Reason.NO_ANSWER, Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE);
    }

    @Test
    @DisplayName("复审 H-1：本登录在 msdb 没有本人用户时不查 msdb 权限")
    void withoutAnMsdbOwnUserNothingIsAsked() {
        FakeCatalog withoutMsdbUser = new FakeCatalog().ownUser("rdscore");
        MsdbPrivileges.EFFECTIVE.forEach(p -> withoutMsdbUser.msdbEffectivePermissions.put(p, 1));

        assertThat(ReadOnlyBaseline.check(withoutMsdbUser).passed()).isTrue();
    }

    @Test
    @DisplayName("复审 H-1 判定不了：有效权限某项答 NULL 或缺答时判定不了（NO_ANSWER），不按通过")
    void anUnansweredPrivilegeIsUndetermined() {
        catalog.msdbEffectivePermissions.put("CONTROL", null);
        catalog.msdbEffectivePermissions.remove("ALTER ANY USER");

        Unmet f = f();

        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 2));
    }

    @Test
    @DisplayName("复审 H-1 判定不了：msdb 本人用户的直接授权为空时判定不了（真实本人用户总有 CONNECT），不按通过")
    void noDirectRowIsUndetermined() {
        catalog.ownUserPermissions.get("msdb").clear();

        Unmet f = f();

        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason).containsExactly(Reason.EMPTY_ANSWER);
    }

    @Test
    @DisplayName("复审 H-1 判定不了：直接授权或有效权限读不到时判定不了，带 SQL 错误号与 SQLState")
    void unreadableAnswersAreUndeterminedWithTheSqlError() {
        FakeCatalog directRefused = new FakeCatalog() {
            @Override
            public List<OwnUserPermission> ownUserPermissions(String systemDatabase) {
                if (systemDatabase.equals("msdb")) {
                    throw new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
                }
                return super.ownUserPermissions(systemDatabase);
            }
        }.rdsOwnUsers();
        FakeCatalog effectiveRefused = new FakeCatalog() {
            @Override
            public Map<String, Integer> msdbEffectivePermissions() {
                throw new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
            }
        }.rdsOwnUsers();

        for (FakeCatalog refused : List.of(directRefused, effectiveRefused)) {
            Unmet f = ReadOnlyBaseline.check(refused).unmet().getFirst();
            assertThat(f.item()).isEqualTo('f');
            assertThat(f.confirmed()).isFalse();
            assertThat(f.findings()).extracting(Finding::reason, Finding::failure)
                    .containsExactly(tuple(Reason.CATALOG_QUERY_FAILED, new SqlError(229, "S0001")));
        }
    }

    @Test
    @DisplayName("复审 H-1：一项读不到而另一项确认越线时违规优先")
    void aConfirmedViolationOutweighsAnUnreadableAnswer() {
        FakeCatalog effectiveRefused = new FakeCatalog() {
            @Override
            public Map<String, Integer> msdbEffectivePermissions() {
                throw new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
            }
        }.rdsOwnUsers();
        effectiveRefused.ownUserPermissions.get("msdb").add(new OwnUserPermission("OBJECT_OR_COLUMN", "W", "EXECUTE"));

        Unmet f = ReadOnlyBaseline.check(effectiveRefused).unmet().getFirst();

        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason)
                .containsExactlyInAnyOrder(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, Reason.CATALOG_QUERY_FAILED);
    }
}
