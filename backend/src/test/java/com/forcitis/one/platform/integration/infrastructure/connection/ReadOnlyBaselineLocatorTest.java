package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Finding;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.GrantLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.OverriddenGrant;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.OverridingDeny;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Unmet;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** (g) by effective permission and the locators of (f) and (g) (READONLY_VERIFY_ACCEPTANCE.md section 3a); the live catalog is in ReadOnlyVerificationCommandIT. */
class ReadOnlyBaselineLocatorTest {

    static final long DBO = 1;
    static final long OTHER_SCHEMA = 5;

    final FakeCatalog catalog = new FakeCatalog();

    static SecurablePermissionRow toPublic(String permission, String state, long schemaId, String schema, String object) {
        return new SecurablePermissionRow("OBJECT_OR_COLUMN", 900 + schemaId, state, permission, true, schemaId, schema, object, "public", "DATABASE_ROLE");
    }

    static SecurablePermissionRow ownSchemaDeny(long schemaId, String permission) {
        return new SecurablePermissionRow("SCHEMA", schemaId, "D", permission, false, schemaId, "s" + schemaId, null, null, "SQL_USER");
    }

    static GrantLocator publicGrant(String permission, String state, long objectId, String schema, String object) {
        return new GrantLocator("OBJECT_OR_COLUMN", schema, object, objectId, permission, state, false, "public", "DATABASE_ROLE");
    }

    private Unmet unmet(char item) {
        return ReadOnlyBaseline.check(catalog).unmet().stream().filter(u -> u.item() == item).findFirst().orElseThrow();
    }

    /** problem text, confirmation and findings of items (a)–(f) as ReadOnlyBaseline gave them before (g) judged by effective permission. */
    static final Map<String, String> UNCHANGED = Map.ofEntries(
            Map.entry("a database permission more", "[(a) database-level effective permissions differ from the platform baseline: more [VIEW DEFINITION], fewer []] [a:true:[EFFECTIVE_PERMISSIONS_DIFFER/1/null]]"),
            Map.entry("a database-level DENY lacking", "[(b) database-level DENY lacks [INSERT]] [b:true:[DATABASE_DENY_MISSING/1/null]]"),
            Map.entry("a database-level GRANT more", "[(b) database-level GRANT is [CONNECT, SELECT, VIEW DEFINITION], must be exactly [CONNECT, SELECT]] [b:true:[DATABASE_GRANT_NOT_BASELINE/1/null]]"),
            Map.entry("a grantable database-level GRANT", "[(b) database-level GRANT WITH GRANT OPTION [SHOWPLAN]] [b:true:[GRANT_WITH_GRANT_OPTION/1/null]]"),
            Map.entry("a database role", "[(c) the login is in the database roles [db_datareader, public], only public is allowed] [c:true:[DATABASE_ROLE_MEMBERSHIP/1/null]]"),
            Map.entry("a fixed server role", "[(d) the login is a member of the fixed server roles [securityadmin]] [d:true:[FIXED_SERVER_ROLE_MEMBERSHIP/1/null]]"),
            Map.entry("a server permission row more", "[(d) the login's rows in sys.server_permissions must be exactly [100 SERVER G CONNECT SQL]: more [100 SERVER D CREATE LOGIN]] [d:true:[SERVER_PERMISSION_ROW_NOT_BASELINE/1/null]]"),
            Map.entry("dbo", "[(e) the login is dbo] [e:true:[DATABASE_OWNER/1/null]]"),
            Map.entry("an owned schema", "[(e) owns 1 schema(s)] [e:true:[OWNS_SCHEMA/1/null]]"),
            Map.entry("an owned object", "[(e) owns 1 object(s)] [e:true:[OWNS_OBJECT/1/null]]"),
            Map.entry("an owned role", "[(e) owns 1 role(s)] [e:true:[OWNS_ROLE/1/null]]"),
            Map.entry("a grant on a database principal", "[(e) holds 1 grant(s) on database principals such as IMPERSONATE] [e:true:[DATABASE_PRINCIPAL_GRANT/1/null]]"),
            Map.entry("another user database", "[(f) the login can also enter the databases [otherworld]] [f:true:[OTHER_DATABASE_REACHABLE/1/null]]"),
            Map.entry("an own user in msdb", "[(f) the login has a user of its own in the system databases [msdb], only guest is allowed there] [f:true:[SYSTEM_DATABASE_OWN_USER/1/null]]"),
            Map.entry("a row the rule cannot read", "[(b) cannot be determined (NullPointerException, no SQL error, innermost NullPointerException)] [b:false:[CHECK_FAILED/1/null]]"),
            Map.entry("no answer", "[(c) cannot be determined (no answer)] [c:false:[NO_ANSWER/1/null]]"),
            Map.entry("an empty answer", "[(d) server-level effective permissions came back empty, cannot be determined] [d:false:[EMPTY_ANSWER/1/null]]"),
            Map.entry("a fixed server role the server cannot answer for", "[(d) membership of the fixed server roles [##MS_ServerStateReader##] cannot be determined] [d:false:[FIXED_SERVER_ROLE_UNANSWERED/1/null]]"),
            Map.entry("the own database not listed", "[(f) HAS_DBACCESS does not list the login's own database, cannot be determined] [f:false:[OWN_DATABASE_NOT_LISTED/1/null]]"));

    @Test
    @DisplayName("C-14 定位裁定（one-coordinator 2026-10-15）：(a)–(f) 的判定、problem 文本与发现在同一 FakeCatalog 输入下与改前逐字一致")
    void itemsAToFAreUnchanged() {
        for (ReadOnlyBaselineTest.Counterexample counterexample : ReadOnlyBaselineTest.COUNTEREXAMPLES) {
            if (counterexample.item() == 'g' || counterexample.name().equals("a failing catalog")) {
                continue;
            }
            FakeCatalog bent = new FakeCatalog();
            counterexample.bend().accept(bent);
            Verdict verdict = ReadOnlyBaseline.check(bent);

            String found = verdict.described() + " " + verdict.unmet().stream().map(u -> u.item() + ":" + u.confirmed() + ":"
                    + u.findings().stream().map(f -> f.reason() + "/" + f.count() + "/" + f.failure()).toList()).toList();
            assertThat(found).as(counterexample.name()).isEqualTo(UNCHANGED.get(counterexample.name()));
        }
    }

    @Test
    @DisplayName("ADR-0037 第 4 项 (b) 收窄 RULING-ADR37-10：public 的对象级 GRANT EXECUTE 被本账号库级 DENY EXECUTE 覆盖，(g) 通过，记为被拒绝覆盖的授权 by=DATABASE_DENY")
    void aDatabaseDenyOverridesTheSamePermission() {
        catalog.securablePermissionRows.add(toPublic("EXECUTE", "G", DBO, "dbo", "p_x"));

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.passed()).as(verdict.described().toString()).isTrue();
        assertThat(verdict.overridden()).containsExactly(new OverriddenGrant(publicGrant("EXECUTE", "G", 900 + DBO, "dbo", "p_x"), OverridingDeny.DATABASE_DENY));
    }

    @Test
    @DisplayName("RULING-ADR37-10 收窄安全条件 2：CONTROL、TAKE OWNERSHIP、IMPERSONATE、VIEW DEFINITION 不在库级 DENY 清单内，即使本账号另有同名 DENY 仍违规；GRANT WITH GRANT OPTION 一律违规")
    void permissionsOutsideTheListAndGrantOptionStayViolations() {
        catalog.databaseLevelPermissions.add(new DatabaseLevelPermission("D", "CONTROL"));
        for (String permission : List.of("CONTROL", "TAKE OWNERSHIP", "IMPERSONATE", "VIEW DEFINITION")) {
            catalog.securablePermissionRows.add(ownSchemaDeny(DBO, permission));
            catalog.securablePermissionRows.add(toPublic(permission, "G", DBO, "dbo", "t_x"));
        }
        catalog.securablePermissionRows.add(toPublic("EXECUTE", "W", DBO, "dbo", "p_x"));

        Unmet g = unmet('g');

        assertThat(g.confirmed()).isTrue();
        assertThat(g.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.reason()).isEqualTo(Reason.SECURABLE_GRANT_BEYOND_SELECT);
            assertThat(finding.count()).isEqualTo(5);
            assertThat(finding.locators()).containsExactlyInAnyOrder(publicGrant("CONTROL", "G", 900 + DBO, "dbo", "t_x"), publicGrant("TAKE OWNERSHIP", "G", 900 + DBO, "dbo", "t_x"),
                    publicGrant("IMPERSONATE", "G", 900 + DBO, "dbo", "t_x"), publicGrant("VIEW DEFINITION", "G", 900 + DBO, "dbo", "t_x"), publicGrant("EXECUTE", "W", 900 + DBO, "dbo", "p_x"));
        });
        assertThat(ReadOnlyBaseline.check(catalog).overridden()).isEmpty();
    }

    @Test
    @DisplayName("opus 安全复审 Medium-1（裁定原文「对象级 / schema 级」）：只有 OBJECT_OR_COLUMN 与 SCHEMA 的授权可被覆盖；"
            + "class 4 DATABASE_PRINCIPAL 上的 GRANT ALTER（如 ALTER ON ROLE）与 TYPE 上的 GRANT REFERENCES 即使本账号有库级同名 DENY 仍违规")
    void onlyObjectAndSchemaGrantsCanBeOverridden() {
        catalog.securablePermissionRows.add(new SecurablePermissionRow("DATABASE_PRINCIPAL", 7, "G", "ALTER", true, null, null, null, "public", "DATABASE_ROLE"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("TYPE", 8, "G", "REFERENCES", false, null, null, null, null, "SQL_USER"));

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.overridden()).isEmpty();
        assertThat(unmet('g').findings()).singleElement().satisfies(finding -> {
            assertThat(finding.reason()).isEqualTo(Reason.SECURABLE_GRANT_BEYOND_SELECT);
            assertThat(finding.locators()).containsExactlyInAnyOrder(
                    new GrantLocator("DATABASE_PRINCIPAL", null, null, 7, "ALTER", "G", false, "public", "DATABASE_ROLE"),
                    new GrantLocator("TYPE", null, null, 8, "REFERENCES", "G", true, null, "SQL_USER"));
        });
    }

    @Test
    @DisplayName("RULING-ADR37-10 收窄安全条件 1：schema 级 DENY 只覆盖同一 schema 中同名权限；所属 schema 不明的授权不被 schema 级 DENY 覆盖；授给 public 的 DENY 不算本账号的 DENY")
    void aSchemaDenyOverridesOnlyInItsOwnSchema() {
        catalog.databaseLevelPermissions.remove(new DatabaseLevelPermission("D", "EXECUTE"));
        catalog.securablePermissionRows.add(ownSchemaDeny(DBO, "EXECUTE"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", OTHER_SCHEMA, "D", "EXECUTE", true, OTHER_SCHEMA, "s5", null, "public", "DATABASE_ROLE"));
        catalog.securablePermissionRows.add(toPublic("EXECUTE", "G", DBO, "dbo", "p_in_dbo"));
        catalog.securablePermissionRows.add(toPublic("EXECUTE", "G", OTHER_SCHEMA, "s5", "p_elsewhere"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 77, "G", "EXECUTE", true, null, null, null, "public", "DATABASE_ROLE"));

        Verdict verdict = ReadOnlyBaseline.check(catalog);
        Unmet g = unmet('g');

        assertThat(verdict.overridden()).containsExactly(new OverriddenGrant(publicGrant("EXECUTE", "G", 900 + DBO, "dbo", "p_in_dbo"), OverridingDeny.SCHEMA_DENY));
        assertThat(g.findings()).singleElement().satisfies(finding -> assertThat(finding.locators()).containsExactlyInAnyOrder(
                publicGrant("EXECUTE", "G", 900 + OTHER_SCHEMA, "s5", "p_elsewhere"), publicGrant("EXECUTE", "G", 77, null, null)));
    }

    @Test
    @DisplayName("RULING-ADR37-10 收窄：库级与 schema 级都有同名 DENY 时记 by=DATABASE_DENY；授给本账号的授权同样判定，grantee 不保留用户名")
    void theDatabaseDenyIsNamedFirstAndOwnGrantsCount() {
        catalog.securablePermissionRows.add(ownSchemaDeny(DBO, "UPDATE"));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 901, "G", "UPDATE", false, DBO, "dbo", "t_x", null, "SQL_USER"));

        assertThat(ReadOnlyBaseline.check(catalog).overridden()).containsExactly(new OverriddenGrant(
                new GrantLocator("OBJECT_OR_COLUMN", "dbo", "t_x", 901, "UPDATE", "G", true, null, "SQL_USER"), OverridingDeny.DATABASE_DENY));
    }

    @Test
    @DisplayName("RULING-ADR37-10 收窄安全条件 3：本账号 DENY 一行都没有时，可被覆盖的授权判定不了（EMPTY_ANSWER），不出定位、不记被拒绝覆盖")
    void noDenyRowLeavesTheGrantUndetermined() {
        catalog.databaseLevelPermissions.removeIf(p -> p.state().equals("D"));
        catalog.securablePermissionRows.add(toPublic("EXECUTE", "G", DBO, "dbo", "p_x"));

        Verdict verdict = ReadOnlyBaseline.check(catalog);
        Unmet g = unmet('g');

        assertThat(g.confirmed()).isFalse();
        assertThat(g.findings()).extracting(Finding::reason).containsExactly(Reason.EMPTY_ANSWER);
        assertThat(g.findings()).flatExtracting(Finding::locators).isEmpty();
        assertThat(verdict.overridden()).isEmpty();
    }

    @Test
    @DisplayName("契约第 3a 节第 4 条待补（one-coordinator 2026-10-15）：DENY 查询失败而授权查询成功时，可被覆盖的授权 (g) 判定不了，带 SQL 错误号，不记被拒绝覆盖；同时的确认违规照样计数")
    void aFailedDenyQueryLeavesTheGrantUndetermined() {
        FakeCatalog failingDenies = new FakeCatalog() {
            @Override
            public List<DatabaseLevelPermission> databaseLevelPermissions() {
                throw new CannotGetJdbcConnectionException("pool", new SQLException("denied", "S0001", 229));
            }
        };
        failingDenies.securablePermissionRows.add(toPublic("EXECUTE", "G", DBO, "dbo", "p_x"));

        Verdict verdict = ReadOnlyBaseline.check(failingDenies);
        Unmet g = verdict.unmet().stream().filter(u -> u.item() == 'g').findFirst().orElseThrow();

        assertThat(g.confirmed()).isFalse();
        assertThat(g.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.reason()).isEqualTo(Reason.CATALOG_QUERY_FAILED);
            assertThat(finding.failure()).isNotNull();
            assertThat(finding.failure().number()).isEqualTo(229);
            assertThat(finding.locators()).isEmpty();
        });
        assertThat(verdict.overridden()).isEmpty();

        failingDenies.securablePermissionRows.add(toPublic("CONTROL", "G", DBO, "dbo", "t_x"));
        Unmet both = ReadOnlyBaseline.check(failingDenies).unmet().stream().filter(u -> u.item() == 'g').findFirst().orElseThrow();
        assertThat(both.confirmed()).isTrue();
        assertThat(both.findings()).extracting(Finding::reason).containsExactly(Reason.SECURABLE_GRANT_BEYOND_SELECT, Reason.CATALOG_QUERY_FAILED);
        assertThat(both.findings().getFirst().locators()).containsExactly(publicGrant("CONTROL", "G", 900 + DBO, "dbo", "t_x"));
    }

    @Test
    @DisplayName("C-14 定位裁定：DENY 查询失败而没有可被覆盖的授权时 (g) 照常通过，不因多查一次而判定不了")
    void aFailedDenyQueryDoesNotMatterWithoutAnOverridableGrant() {
        FakeCatalog failingDenies = new FakeCatalog() {
            @Override
            public List<DatabaseLevelPermission> databaseLevelPermissions() {
                throw new CannotGetJdbcConnectionException("pool", new SQLException("denied", "S0001", 229));
            }
        };

        assertThat(ReadOnlyBaseline.check(failingDenies).unmet()).extracting(Unmet::item).containsExactly('b');
    }

    @Test
    @DisplayName("C-14 定位裁定：(f) 两种原因都按库带定位，计数等于定位个数；problem 文本、拒绝原因中不出现 schema 名与对象名")
    void databasesAreLocatedAndNamesStayOutOfTheProblem() {
        catalog.ownUsers.put("master", 1L);
        catalog.ownUsers.put("msdb", 1L);
        catalog.reachableDatabases.add("otherworld");
        catalog.securablePermissionRows.add(toPublic("CONTROL", "G", DBO, "s_secret_marker", "t_secret_marker"));

        Unmet f = unmet('f');

        assertThat(f.findings()).extracting(Finding::reason, Finding::count).containsExactly(
                tuple(Reason.OTHER_DATABASE_REACHABLE, 1), tuple(Reason.SYSTEM_DATABASE_OWN_USER, 2));
        assertThat(f.findings().get(0).locators()).containsExactly(new DatabaseLocator("otherworld"));
        assertThat(f.findings().get(1).locators()).containsExactly(new DatabaseLocator("master"), new DatabaseLocator("msdb"));
        assertThat(ReadOnlyBaseline.violations(catalog)).allSatisfy(problem -> assertThat(problem).doesNotContain("secret_marker"));
    }
}
