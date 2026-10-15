package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The decision of ADR-0037 item 4 on catalog answers; the live catalog is exercised by acceptance.platform.integration.ReadOnlyAccountCheckIT. */
class ReadOnlyBaselineTest {

    final FakeCatalog catalog = new FakeCatalog();

    @Test
    @DisplayName("ADR37-1 the baseline login passes every item")
    void baselinePasses() {
        assertThat(ReadOnlyBaseline.violations(catalog)).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 (a) one database permission more or less than the baseline, or none at all, is named under (a)")
    void databasePermissionsMustEqualTheBaseline() {
        catalog.databasePermissions.add("VIEW DEFINITION");
        catalog.databasePermissions.remove("SHOWPLAN");

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString()
                .startsWith("(a)").contains("more [VIEW DEFINITION]").contains("fewer [SHOWPLAN]");

        catalog.databasePermissions.clear();
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(a)").contains("empty");
    }

    @Test
    @DisplayName("ADR37-1 (b) a missing database-level DENY, a GRANT beyond CONNECT and SELECT and a grantable GRANT are named under (b)")
    void databaseLevelDeniesAndGrants() {
        catalog.databaseLevelPermissions.remove(new DatabaseLevelPermission("D", "INSERT"));
        catalog.databaseLevelPermissions.add(new DatabaseLevelPermission("G", "VIEW DEFINITION"));
        catalog.databaseLevelPermissions.add(new DatabaseLevelPermission("W", "SHOWPLAN"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(b)")
                .contains("DENY lacks [INSERT]").contains("VIEW DEFINITION").contains("GRANT WITH GRANT OPTION [SHOWPLAN]");
    }

    @Test
    @DisplayName("ADR37-1 (b) no database-level row at all is a violation, not a pass")
    void emptyDatabaseLevelIsRefused() {
        catalog.databaseLevelPermissions.clear();

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(b)");
    }

    @Test
    @DisplayName("ADR37-1 (c) any role besides public, a read-only one included, and no role at all are refused")
    void onlyPublic() {
        catalog.roles.add("db_datareader");
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(c)").contains("db_datareader");

        catalog.roles.clear();
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(c)");
    }

    @Test
    @DisplayName("ADR37-1 (d) server permissions must equal the baseline; a fixed server role, or one the server cannot answer for, refuses")
    void server() {
        catalog.serverPermissions.add("CONTROL SERVER");
        catalog.memberships.put("securityadmin", 1);
        catalog.memberships.put("##MS_ServerStateReader##", null);

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)")
                .contains("more [CONTROL SERVER]").contains("[securityadmin]").contains("##MS_ServerStateReader##");
    }

    @Test
    @DisplayName("ADR37-1 (e) dbo, owning a schema, an object or a role, and grants on a database principal are each named")
    void ownsNothing() {
        catalog.ownership = new Ownership(true, 1, 2, 3, 4);

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(e)")
                .contains("dbo").contains("1 schema").contains("2 object").contains("3 role").contains("4 grant");
    }

    @Test
    @DisplayName("ADR37-1 (f) the own database and the four guest system databases pass; any other database refuses")
    void reachable() {
        catalog.reachableDatabases = List.of("legacyworld", "master", "tempdb");
        assertThat(ReadOnlyBaseline.violations(catalog)).isEmpty();

        catalog.reachableDatabases = List.of("legacyworld", "master", "otherworld");
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("otherworld");

        catalog.reachableDatabases = List.of("master");
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("own database");
    }

    @Test
    @DisplayName("ADR37-1 a catalog that cannot be read refuses every item, naming only the failure type, error number and SQLState")
    void undeterminableRefusesWithoutTheMessage() {
        catalog.unreachable = true;

        List<String> violations = ReadOnlyBaseline.violations(catalog);

        assertThat(violations).hasSize(7).allSatisfy(v -> assertThat(v).contains("cannot be determined").doesNotContain(FakeCatalog.LEAKED));
        assertThat(violations).extracting(v -> v.substring(0, 3)).containsExactly("(a)", "(b)", "(c)", "(d)", "(e)", "(f)", "(g)");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).as("nothing was seen, so nothing is a confirmed violation").isFalse();
    }

    @Test
    @DisplayName("ADR37-1 复审 H-1 (d) a server permission row of the login beyond GRANT CONNECT SQL, such as IMPERSONATE ON LOGIN::sa, is named under (d)")
    void serverPermissionRows() {
        catalog.serverPermissionRows.add(new ServerPermissionRow(101, "SERVER_PRINCIPAL", "G", "IMPERSONATE"));
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "W", "VIEW SERVER STATE"));
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "G", "CONTROL SERVER"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)")
                .contains("SERVER_PRINCIPAL G IMPERSONATE").contains("SERVER W VIEW SERVER STATE").contains("SERVER G CONTROL SERVER");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).isTrue();
    }

    @Test
    @DisplayName("ADR37-1 复审 H-1 (d) a server-level DENY or a direct GRANT within the platform baseline adds nothing and passes")
    void serverRowsWithinTheBaselinePass() {
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "G", "VIEW SERVER STATE"));
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "D", "CREATE LOGIN"));

        assertThat(ReadOnlyBaseline.violations(catalog)).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 复审 H-1 (g) the real logins' rows pass: schema-level DENY of six and GRANT SELECT (A8), object-level GRANT SELECT (old system)")
    void evidenceRowsPass() {
        catalog.securablePermissionRows.clear();
        for (String schema : List.of("1", "5")) {
            for (String deny : List.of("ALTER", "DELETE", "EXECUTE", "INSERT", "REFERENCES", "UPDATE")) {
                catalog.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", Long.parseLong(schema), "D", deny, false));
            }
        }
        catalog.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", 5, "G", "SELECT", false));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 917578307, "G", "SELECT", false));
        assertThat(ReadOnlyBaseline.violations(catalog)).isEmpty();

        catalog.securablePermissionRows.clear();
        assertThat(ReadOnlyBaseline.violations(catalog)).as("the evidence login holds no such row at all").isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 复审 H-1 (g) any other grant below the database level, of the login or of public on a user securable, is named under (g)")
    void belowTheDatabase() {
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 42, "G", "TAKE OWNERSHIP", false));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", 5, "G", "CONTROL", false));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", 1, "W", "SELECT", false));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 43, "G", "UPDATE", true));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("DATABASE_PRINCIPAL", 7, "G", "IMPERSONATE", false));
        catalog.securablePermissionRows.add(new SecurablePermissionRow("OBJECT_OR_COLUMN", 44, "D", "CONTROL", true));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(g)")
                .contains("OBJECT_OR_COLUMN 42 G TAKE OWNERSHIP").contains("SCHEMA 5 G CONTROL").contains("SCHEMA 1 W SELECT")
                .contains("OBJECT_OR_COLUMN 43 G UPDATE to public").contains("DATABASE_PRINCIPAL 7 G IMPERSONATE").doesNotContain("44");
    }

    @Test
    @DisplayName("ADR37-1 复审 H-2 (f) a user of the login's own in a system database (only guest is allowed there) is named under (f)")
    void systemDatabasesOnlyThroughGuest() {
        catalog.ownUsers.put("msdb", 1L);

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("[msdb]").contains("guest");
    }

    @Test
    @DisplayName("ADR37-1 复审 L-4 (f) a user database is not a system database because of its name: MSDB with a user database id is refused")
    void systemDatabasesByIdNotByName() {
        catalog.reachableDatabaseRows = List.of(new ReachableDatabase(6, "legacyworld"), new ReachableDatabase(1, "master"),
                new ReachableDatabase(7, "MSDB"), new ReachableDatabase(8, "RDSCORE"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("[MSDB, RDSCORE]");
    }

    @Test
    @DisplayName("ADR37-1 M-1 an empty answer is undeterminable, not a confirmed violation; one confirmed item makes the whole check a violation")
    void undeterminableIsToldApart() {
        catalog.serverPermissions.clear();
        Verdict emptyOnly = ReadOnlyBaseline.check(catalog);
        assertThat(emptyOnly.passed()).isFalse();
        assertThat(emptyOnly.violated()).isFalse();
        assertThat(emptyOnly.described()).singleElement().asString().startsWith("(d)").contains("empty");

        catalog.roles.add("db_datareader");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).isTrue();
    }

    @Test
    @DisplayName("ADR37-1 M-1 an item with a violation and an unanswered query is a confirmed violation")
    void violationWinsWithinAnItem() {
        catalog.serverPermissions.add("CONTROL SERVER");
        catalog.memberships.put("##MS_ServerStateReader##", null);

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.unmet()).singleElement().satisfies(unmet -> {
            assertThat(unmet.item()).isEqualTo('d');
            assertThat(unmet.confirmed()).isTrue();
        });
    }
}
