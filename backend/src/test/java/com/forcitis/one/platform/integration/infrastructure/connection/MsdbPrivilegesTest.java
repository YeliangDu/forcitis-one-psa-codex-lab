package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.MsdbPrivileges.TokenPermission;
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

/** C-14 (f) opus review H-1 and H-1′ as one-coordinator ruled them (2026-10-15, 2026-10-16); live: ReadOnlyVerificationCommandIT and ReadOnlyAccountCheckIT. */
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
    @ValueSource(strings = {"DATABASE G CONTROL", "DATABASE G ALTER ANY ROLE", "DATABASE G CREATE PROCEDURE", "DATABASE G SELECT",
            "DATABASE W CONNECT", "DATABASE_PRINCIPAL G IMPERSONATE", "SCHEMA G ALTER", "OBJECT_OR_COLUMN W EXECUTE", "OBJECT_OR_COLUMN W SELECT",
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
    @DisplayName("复审 H-1′（改自 H-1「没有本人用户时不查」）：本登录在 msdb 没有本人用户、只经 guest 进入时，msdb 可进就照查令牌范围权限与有效权限，六项为真即违规")
    void withoutAnMsdbOwnUserTheTokenAndTheEffectivePermissionsAreStillAsked() {
        FakeCatalog withoutMsdbUser = new FakeCatalog().ownUser("rdscore");
        MsdbPrivileges.EFFECTIVE.forEach(p -> withoutMsdbUser.msdbEffectivePermissions.put(p, 1));

        Unmet f = ReadOnlyBaseline.check(withoutMsdbUser).unmet().getFirst();

        assertThat(f.item()).isEqualTo('f');
        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1, List.of(new DatabaseLocator("msdb"))));
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

    private void token(boolean toPublic, String row) {
        String[] parts = row.split(" ", 3);
        catalog.msdbTokenPermissions.add(new TokenPermission(toPublic, parts[0], parts[1], parts[2]));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DATABASE G TAKE OWNERSHIP", "DATABASE G ALTER ANY DATABASE DDL TRIGGER", "DATABASE G CREATE FUNCTION", "DATABASE G CREATE ASSEMBLY",
            "DATABASE G CREATE TYPE", "DATABASE G CREATE VIEW", "DATABASE G CREATE TABLE", "DATABASE W CONNECT",
            "DATABASE_PRINCIPAL G IMPERSONATE", "SCHEMA G ALTER", "SCHEMA G CONTROL", "OBJECT_OR_COLUMN G ALTER", "OBJECT_OR_COLUMN W EXECUTE", "TYPE G REFERENCES"})
    @DisplayName("复审 H-1′ 令牌范围：授给令牌内角色或 public 的授权套同一份类别清单，清单外即违规（六项与 IMPERSONATE 全假、本人用户直接授权在清单内也违规）")
    void aTokenRowOutsideTheAllowListIsAViolation(String row) {
        for (boolean toPublic : List.of(false, true)) {
            catalog.msdbTokenPermissions = new FakeCatalog().msdbTokenPermissions;
            token(toPublic, row);

            assertPrivilegeViolation();
        }
    }

    @Test
    @DisplayName("复审 H-1′ 不误判：public 的两条库级默认 VIEW ANY COLUMN MASTER KEY / ENCRYPTION KEY DEFINITION 与令牌内的对象级 GRANT SELECT、GRANT EXECUTE、DENY 通过")
    void theDefaultsOfPublicAndTheRolesPass() {
        token(true, "DATABASE G VIEW ANY COLUMN MASTER KEY DEFINITION");
        token(true, "DATABASE G view any column encryption key definition ");
        token(false, "OBJECT_OR_COLUMN G SELECT");
        token(false, "OBJECT_OR_COLUMN D UPDATE");
        token(true, "OBJECT_OR_COLUMN G EXECUTE");

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.passed()).as(verdict.described().toString()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"VIEW ANY COLUMN MASTER KEY DEFINITION", "VIEW ANY COLUMN ENCRYPTION KEY DEFINITION"})
    @DisplayName("复审 H-1′：public 默认的两条只放行给 public，授给其他主体或带转授权即违规")
    void thePublicDefaultsAreAllowedToPublicAlone(String permission) {
        token(false, "DATABASE G " + permission);
        assertPrivilegeViolation();

        catalog.msdbTokenPermissions = new FakeCatalog().msdbTokenPermissions;
        token(true, "DATABASE W " + permission);
        assertPrivilegeViolation();
    }

    @Test
    @DisplayName("复审 H-1′ IMPERSONATE：HAS_PERMS_BY_NAME('dbo', 'USER', 'IMPERSONATE') 为真即违规（六项全假、令牌范围在清单内也违规）")
    void anEffectiveImpersonateIsAViolation() {
        catalog.msdbEffectivePermissions.put(MsdbPrivileges.IMPERSONATE, 1);

        assertPrivilegeViolation();
    }

    @Test
    @DisplayName("复审 H-1′ 判定不了：IMPERSONATE 答 NULL 或缺答时判定不了（NO_ANSWER），不按通过")
    void anUnansweredImpersonateIsUndetermined() {
        catalog.msdbEffectivePermissions.put(MsdbPrivileges.IMPERSONATE, null);
        assertThat(f().findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 1));

        catalog.msdbEffectivePermissions.remove(MsdbPrivileges.IMPERSONATE);
        Unmet f = f();

        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 1));
    }

    @Test
    @DisplayName("复审 H-1′ 空列表口径：令牌范围扫描为空列表判定不了（EMPTY_ANSWER），不按通过")
    void anEmptyTokenScanIsUndetermined() {
        catalog.msdbTokenPermissions.clear();

        Unmet f = f();

        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.EMPTY_ANSWER, 1));
    }

    @Test
    @DisplayName("复审 H-1′ 判定不了：令牌范围扫描答 NULL（NO_ANSWER），或某行有列缺答（NO_ANSWER，计缺答行数），不按通过")
    void aNullTokenScanOrAnUnansweredColumnIsUndetermined() {
        catalog.msdbTokenPermissions = null;
        assertThat(f().findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 1));

        catalog.msdbTokenPermissions = new FakeCatalog().msdbTokenPermissions;
        catalog.msdbTokenPermissions.add(new TokenPermission(false, null, "G", "ALTER"));
        catalog.msdbTokenPermissions.add(new TokenPermission(true, "SCHEMA", null, "ALTER"));
        catalog.msdbTokenPermissions.add(new TokenPermission(false, "SCHEMA", "G", null));
        Unmet f = f();

        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 3));
    }

    @Test
    @DisplayName("复审 H-1′：令牌范围扫描读不到时判定不了（CATALOG_QUERY_FAILED，带 SQL 错误号）；另一检查确认越线时违规优先")
    void anUnreadableTokenScanIsUndeterminedUnlessAViolationIsConfirmed() {
        FakeCatalog tokenRefused = new FakeCatalog() {
            @Override
            public List<TokenPermission> msdbTokenPermissions() {
                throw new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
            }
        }.rdsOwnUsers();

        Unmet f = ReadOnlyBaseline.check(tokenRefused).unmet().getFirst();
        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::failure).containsExactly(tuple(Reason.CATALOG_QUERY_FAILED, new SqlError(229, "S0001")));

        tokenRefused.msdbEffectivePermissions.put(MsdbPrivileges.IMPERSONATE, 1);
        f = ReadOnlyBaseline.check(tokenRefused).unmet().getFirst();
        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason)
                .containsExactlyInAnyOrder(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, Reason.CATALOG_QUERY_FAILED);
    }

    @Test
    @DisplayName("复审 H-1′ 只经 guest 进 msdb：令牌范围越线（授给 guest）违规，定位只写 msdb，没有 msdb 角色信息行；不读本人用户授权")
    void aGuestOnlyLoginIsJudgedOnItsTokenAndNotAskedForOwnRows() {
        FakeCatalog guestOnly = new FakeCatalog() {
            @Override
            public List<OwnUserPermission> ownUserPermissions(String systemDatabase) {
                assertThat(systemDatabase).as("no own user in msdb, so its rows are not asked").isNotEqualTo("msdb");
                return super.ownUserPermissions(systemDatabase);
            }
        }.ownUser("rdscore");
        assertThat(ReadOnlyBaseline.check(guestOnly).passed()).isTrue();

        guestOnly.msdbTokenPermissions.add(new TokenPermission(false, "DATABASE", "G", "ALTER ANY SCHEMA"));
        Verdict verdict = ReadOnlyBaseline.check(guestOnly);

        assertThat(verdict.unmet()).singleElement().satisfies(f -> {
            assertThat(f.item()).isEqualTo('f');
            assertThat(f.confirmed()).isTrue();
            assertThat(f.findings()).extracting(Finding::reason, Finding::count, Finding::locators)
                    .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1, List.of(new DatabaseLocator("msdb"))));
        });
        assertThat(verdict.ownUsers()).extracting(SystemDatabaseUsers.OwnUserRole::database).containsExactly("rdscore");
    }

    @Test
    @DisplayName("复审 H-1′：msdb 本人用户的角色读不到（判定不了）时仍查令牌范围与有效权限，越线即违规优先；Agent 作业数照读")
    void unreadableMsdbMembershipsDoNotSkipThePrivilegeCheck() {
        FakeCatalog membershipsRefused = new FakeCatalog() {
            @Override
            public List<SystemDatabaseUsers.Membership> ownUserMemberships(String systemDatabase) {
                if (systemDatabase.equals("msdb")) {
                    throw new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
                }
                return super.ownUserMemberships(systemDatabase);
            }
        }.rdsOwnUsers();
        membershipsRefused.msdbEffectivePermissions.put(MsdbPrivileges.IMPERSONATE, 1);
        membershipsRefused.ownedAgentJobs = 2;

        Unmet f = ReadOnlyBaseline.check(membershipsRefused).unmet().getFirst();

        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason)
                .containsExactlyInAnyOrder(Reason.CATALOG_QUERY_FAILED, Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, Reason.OWNS_AGENT_JOB);
    }

    @Test
    @DisplayName("复审 H-1′：令牌行记录的 toString、problem 与核实命令输出不含权限名、主体名与对象名")
    void aTokenRowNamesNothing() {
        token(false, "SCHEMA G ALTER");
        TokenPermission row = new TokenPermission(true, "DATABASE", "G", "TAKE OWNERSHIP");

        assertThat(row.toString()).doesNotContain("TAKE OWNERSHIP").doesNotContain("DATABASE");
        assertThat(f().problem()).doesNotContain("ALTER").doesNotContain("SCHEMA").doesNotContain("SQLAgent").doesNotContain("public");
        assertThat(report()).doesNotContain("SCHEMA").doesNotContain("ALTER");
    }

    private FakeCatalog guestOnly() {
        return new FakeCatalog().ownUser("rdscore");
    }

    private static void assertViolationOnly(FakeCatalog from) {
        Unmet f = ReadOnlyBaseline.check(from).unmet().getFirst();
        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count, Finding::locators)
                .containsExactly(tuple(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1, List.of(new DatabaseLocator("msdb"))));
    }

    private static CannotGetJdbcConnectionException refused() {
        return new CannotGetJdbcConnectionException("msdb", new SQLException("denied", "S0001", 229));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"db_ssisadmin", "DatabaseMailUserRole", "db_owner", "forcitis_nested_role", "sqlagentuserrole", "SQLAgentUserRole ", "dc_admin"})
    @DisplayName("复审 H-A：令牌里出现 public 与三个 SQLAgent 角色以外的任何角色即违规，按名称精确匹配（大小写、空格不同也违规），本人用户存在与只经 guest 两种进入都查")
    void aTokenRoleBeyondTheAllowedFourIsAViolation(String role) {
        catalog.msdbTokenRoles.add(role);
        assertPrivilegeViolation();

        FakeCatalog guest = guestOnly();
        guest.msdbTokenRoles = new java.util.ArrayList<>(List.of("public", role));
        assertViolationOnly(guest);
    }

    @Test
    @DisplayName("复审 H-A 不误判：令牌只有 public 与三个 SQLAgent 角色的任意子集（含只经 guest 的 public 单独一个）通过")
    void theAllowedTokenRolesPass() {
        assertThat(ReadOnlyBaseline.check(catalog).passed()).isTrue();

        FakeCatalog guest = guestOnly();
        guest.msdbTokenRoles = new java.util.ArrayList<>(List.of("public"));
        assertThat(ReadOnlyBaseline.check(guest).passed()).isTrue();
        guest.msdbTokenRoles = new java.util.ArrayList<>(List.of("SQLAgentReaderRole", "public"));
        assertThat(ReadOnlyBaseline.check(guest).passed()).isTrue();
    }

    @Test
    @DisplayName("复审 H-A 判定不了：令牌角色为空列表 EMPTY_ANSWER、答 NULL NO_ANSWER、某名称缺答 NO_ANSWER（计缺答个数），不按通过")
    void unansweredTokenRolesAreUndetermined() {
        catalog.msdbTokenRoles.clear();
        assertThat(f().findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.EMPTY_ANSWER, 1));

        catalog.msdbTokenRoles = null;
        assertThat(f().findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 1));

        catalog.msdbTokenRoles = new java.util.ArrayList<>(java.util.Arrays.asList("public", null, null));
        Unmet f = f();
        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 2));
    }

    @Test
    @DisplayName("复审 H-A：令牌角色读不到判定不了（CATALOG_QUERY_FAILED，带错误号）；另有确认越线的角色或拥有关系时违规优先")
    void anUnreadableTokenRoleListIsUndeterminedUnlessAViolationIsConfirmed() {
        FakeCatalog rolesRefused = new FakeCatalog() {
            @Override
            public List<String> msdbTokenRoles() {
                throw refused();
            }
        }.rdsOwnUsers();

        Unmet f = ReadOnlyBaseline.check(rolesRefused).unmet().getFirst();
        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::failure).containsExactly(tuple(Reason.CATALOG_QUERY_FAILED, new SqlError(229, "S0001")));

        rolesRefused.msdbOwnedByToken = 1L;
        f = ReadOnlyBaseline.check(rolesRefused).unmet().getFirst();
        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason)
                .containsExactlyInAnyOrder(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, Reason.CATALOG_QUERY_FAILED);
    }

    @Test
    @DisplayName("复审 H-A：违规优先于其他项判定不了（角色越线，同时拥有关系答 NULL）")
    void aRoleViolationOutweighsAnUnansweredOwnership() {
        catalog.msdbTokenRoles.add("db_ssisadmin");
        catalog.msdbOwnedByToken = null;

        Unmet f = f();

        assertThat(f.confirmed()).isTrue();
        assertThat(f.findings()).extracting(Finding::reason).containsExactlyInAnyOrder(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, Reason.NO_ANSWER);
    }

    @Test
    @DisplayName("复审 H-A：problem、定位行与核实命令输出不含令牌角色名")
    void aTokenRoleNamesNothing() {
        catalog.msdbTokenRoles.add("role_secret_marker");

        assertThat(f().problem()).doesNotContain("secret_marker");
        assertThat(report()).doesNotContain("secret_marker");
        assertThat(ReadOnlyBaseline.check(catalog).toString()).doesNotContain("secret_marker");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {1, 2, 100})
    @DisplayName("复审 M-A：令牌内主体在 msdb 拥有的 schema、对象、角色（平台默认的同名 schema 除外）个数大于 0 即违规，本人用户存在与只经 guest 两种进入都查")
    void whatTheTokenOwnsIsAViolation(long owned) {
        catalog.msdbOwnedByToken = owned;
        assertPrivilegeViolation();

        FakeCatalog guest = guestOnly();
        guest.msdbOwnedByToken = owned;
        assertViolationOnly(guest);
    }

    @Test
    @DisplayName("复审 M-A 判定不了：拥有关系答 NULL（NO_ANSWER）、读不到（CATALOG_QUERY_FAILED，带错误号）不按通过")
    void unansweredOwnershipIsUndetermined() {
        catalog.msdbOwnedByToken = null;
        Unmet f = f();
        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(tuple(Reason.NO_ANSWER, 1));

        FakeCatalog refusedOwnership = new FakeCatalog() {
            @Override
            public Long msdbOwnedByToken() {
                throw refused();
            }
        }.rdsOwnUsers();
        f = ReadOnlyBaseline.check(refusedOwnership).unmet().getFirst();
        assertThat(f.confirmed()).isFalse();
        assertThat(f.findings()).extracting(Finding::reason, Finding::failure).containsExactly(tuple(Reason.CATALOG_QUERY_FAILED, new SqlError(229, "S0001")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DATABASE D CONNECT", "SCHEMA D ALTER", "DATABASE_PRINCIPAL D IMPERSONATE", "DATABASE D CONTROL", "TYPE D REFERENCES"})
    @DisplayName("复审 Low-1：DENY 在任何 class 都放行（DENY 不会提权），本人用户直接授权与令牌范围两处一致")
    void aDenyOfAnyClassPasses(String row) {
        direct(row);
        token(false, row);
        token(true, row);

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.passed()).as(verdict.described().toString()).isTrue();
    }

    @Test
    @DisplayName("复审 Low-1：DENY 放行不波及转授权与 GRANT（同类 GRANT、W 仍违规）")
    void aDenyDoesNotLetTheGrantOfTheSameClassPass() {
        token(false, "SCHEMA G ALTER");

        assertPrivilegeViolation();
    }

    @Test
    @DisplayName("复审 Low-3：提示文字写「the login in msdb」，不再称本人用户（只经 guest 进入时也适用）")
    void theProblemSpeaksOfTheLoginInMsdb() {
        catalog.msdbEffectivePermissions.put("CONTROL", 1);
        catalog.msdbEffectivePermissions.put("ALTER", null);

        Unmet f = f();

        assertThat(f.problem()).contains("the login in msdb holds permissions beyond").doesNotContain("own user");
        assertThat(f.problem()).contains("effective permissions of the login in msdb cannot be determined");
    }
}
