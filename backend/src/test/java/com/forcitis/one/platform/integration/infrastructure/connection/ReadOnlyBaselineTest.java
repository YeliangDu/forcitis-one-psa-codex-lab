package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Finding;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SqlError;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Unmet;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

/** The decision of ADR-0037 item 4 on catalog answers; the live catalog is exercised by acceptance.platform.integration.ReadOnlyAccountCheckIT. */
class ReadOnlyBaselineTest {

    final FakeCatalog catalog = new FakeCatalog();

    record Counterexample(String name, Consumer<FakeCatalog> bend, char item, Reason reason) {
    }

    /** One bent answer per reason code; {@link #everyReasonIsReachedByACounterexample} keeps the list complete. */
    static final List<Counterexample> COUNTEREXAMPLES = List.of(
            new Counterexample("a database permission more", c -> c.databasePermissions.add("VIEW DEFINITION"), 'a', Reason.EFFECTIVE_PERMISSIONS_DIFFER),
            new Counterexample("a database-level DENY lacking", c -> c.databaseLevelPermissions.remove(new DatabaseLevelPermission("D", "INSERT")), 'b',
                    Reason.DATABASE_DENY_MISSING),
            new Counterexample("a database-level GRANT more", c -> c.databaseLevelPermissions.add(new DatabaseLevelPermission("G", "VIEW DEFINITION")), 'b',
                    Reason.DATABASE_GRANT_NOT_BASELINE),
            new Counterexample("a grantable database-level GRANT", c -> c.databaseLevelPermissions.add(new DatabaseLevelPermission("W", "SHOWPLAN")), 'b',
                    Reason.GRANT_WITH_GRANT_OPTION),
            new Counterexample("a database role", c -> c.roles.add("db_datareader"), 'c', Reason.DATABASE_ROLE_MEMBERSHIP),
            new Counterexample("a fixed server role", c -> c.memberships.put("securityadmin", 1), 'd', Reason.FIXED_SERVER_ROLE_MEMBERSHIP),
            new Counterexample("a server permission row more", c -> c.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "D", "CREATE LOGIN")), 'd',
                    Reason.SERVER_PERMISSION_ROW_NOT_BASELINE),
            new Counterexample("dbo", c -> c.ownership = new Ownership(true, 0, 0, 0, 0), 'e', Reason.DATABASE_OWNER),
            new Counterexample("an owned schema", c -> c.ownership = new Ownership(false, 1, 0, 0, 0), 'e', Reason.OWNS_SCHEMA),
            new Counterexample("an owned object", c -> c.ownership = new Ownership(false, 0, 1, 0, 0), 'e', Reason.OWNS_OBJECT),
            new Counterexample("an owned role", c -> c.ownership = new Ownership(false, 0, 0, 1, 0), 'e', Reason.OWNS_ROLE),
            new Counterexample("a grant on a database principal", c -> c.ownership = new Ownership(false, 0, 0, 0, 1), 'e', Reason.DATABASE_PRINCIPAL_GRANT),
            new Counterexample("another user database", c -> c.reachableDatabases.add("otherworld"), 'f', Reason.OTHER_DATABASE_REACHABLE),
            new Counterexample("an own user in msdb", c -> c.ownUsers.put("msdb", 1L), 'f', Reason.SYSTEM_DATABASE_OWN_USER),
            new Counterexample("a grant below the database", c -> c.securablePermissionRows.add(new SecurablePermissionRow("SCHEMA", 5, "G", "CONTROL", false)),
                    'g', Reason.SECURABLE_GRANT_BEYOND_SELECT),
            new Counterexample("a failing catalog", c -> c.unreachable = true, 'a', Reason.CATALOG_QUERY_FAILED),
            new Counterexample("a row the rule cannot read", c -> c.databaseLevelPermissions.add(new DatabaseLevelPermission(null, "SELECT")), 'b',
                    Reason.CHECK_FAILED),
            new Counterexample("no answer", c -> c.roles = null, 'c', Reason.NO_ANSWER),
            new Counterexample("an empty answer", c -> c.serverPermissions.clear(), 'd', Reason.EMPTY_ANSWER),
            new Counterexample("a fixed server role the server cannot answer for", c -> c.memberships.put("##MS_ServerStateReader##", null), 'd',
                    Reason.FIXED_SERVER_ROLE_UNANSWERED),
            new Counterexample("the own database not listed", c -> c.reachableDatabases.remove("legacyworld"), 'f', Reason.OWN_DATABASE_NOT_LISTED));

    private static Unmet unmet(FakeCatalog catalog, char item) {
        List<Unmet> found = ReadOnlyBaseline.check(catalog).unmet().stream().filter(u -> u.item() == item).toList();
        assertThat(found).as("(%s) unmet", item).hasSize(1);
        return found.getFirst();
    }

    private List<Reason> reasons(char item) {
        return unmet(catalog, item).findings().stream().map(Finding::reason).toList();
    }

    private List<Integer> counts(char item) {
        return unmet(catalog, item).findings().stream().map(Finding::count).toList();
    }

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
        assertThat(reasons('a')).containsExactly(Reason.EFFECTIVE_PERMISSIONS_DIFFER);
        assertThat(counts('a')).containsExactly(2);

        catalog.databasePermissions.clear();
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(a)").contains("empty");
        assertThat(reasons('a')).containsExactly(Reason.EMPTY_ANSWER);
    }

    @Test
    @DisplayName("ADR37-1 (b) a missing database-level DENY, a GRANT beyond CONNECT and SELECT and a grantable GRANT are named under (b)")
    void databaseLevelDeniesAndGrants() {
        catalog.databaseLevelPermissions.remove(new DatabaseLevelPermission("D", "INSERT"));
        catalog.databaseLevelPermissions.add(new DatabaseLevelPermission("G", "VIEW DEFINITION"));
        catalog.databaseLevelPermissions.add(new DatabaseLevelPermission("W", "SHOWPLAN"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(b)")
                .contains("DENY lacks [INSERT]").contains("VIEW DEFINITION").contains("GRANT WITH GRANT OPTION [SHOWPLAN]");
        assertThat(reasons('b')).containsExactly(Reason.DATABASE_DENY_MISSING, Reason.DATABASE_GRANT_NOT_BASELINE, Reason.GRANT_WITH_GRANT_OPTION);
        assertThat(counts('b')).containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("ADR37-1 (b) no database-level row at all is a violation, not a pass")
    void emptyDatabaseLevelIsRefused() {
        catalog.databaseLevelPermissions.clear();

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(b)");
        assertThat(reasons('b')).containsExactly(Reason.EMPTY_ANSWER);
    }

    @Test
    @DisplayName("ADR37-1 (c) any role besides public, a read-only one included, and no role at all are refused")
    void onlyPublic() {
        catalog.roles.add("db_datareader");
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(c)").contains("db_datareader");
        assertThat(reasons('c')).containsExactly(Reason.DATABASE_ROLE_MEMBERSHIP);
        assertThat(counts('c')).containsExactly(1);

        catalog.roles.clear();
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(c)");
        assertThat(reasons('c')).containsExactly(Reason.EMPTY_ANSWER);
    }

    @Test
    @DisplayName("ADR37-1 (d) server permissions must equal the baseline; a fixed server role, or one the server cannot answer for, refuses")
    void server() {
        catalog.serverPermissions.add("CONTROL SERVER");
        catalog.memberships.put("securityadmin", 1);
        catalog.memberships.put("##MS_ServerStateReader##", null);

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)")
                .contains("more [CONTROL SERVER]").contains("[securityadmin]").contains("##MS_ServerStateReader##");
        assertThat(reasons('d')).containsExactly(Reason.EFFECTIVE_PERMISSIONS_DIFFER, Reason.FIXED_SERVER_ROLE_MEMBERSHIP,
                Reason.FIXED_SERVER_ROLE_UNANSWERED);
    }

    @Test
    @DisplayName("ADR37-1 (e) dbo, owning a schema, an object or a role, and grants on a database principal are each named")
    void ownsNothing() {
        catalog.ownership = new Ownership(true, 1, 2, 3, 4);

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(e)")
                .contains("dbo").contains("1 schema").contains("2 object").contains("3 role").contains("4 grant");
        assertThat(reasons('e')).containsExactly(Reason.DATABASE_OWNER, Reason.OWNS_SCHEMA, Reason.OWNS_OBJECT, Reason.OWNS_ROLE,
                Reason.DATABASE_PRINCIPAL_GRANT);
        assertThat(counts('e')).containsExactly(1, 1, 2, 3, 4);
    }

    @Test
    @DisplayName("ADR37-1 (f) the own database and the four guest system databases pass; any other database refuses")
    void reachable() {
        catalog.reachableDatabases = List.of("legacyworld", "master", "tempdb");
        assertThat(ReadOnlyBaseline.violations(catalog)).isEmpty();

        catalog.reachableDatabases = List.of("legacyworld", "master", "otherworld");
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("otherworld");
        assertThat(reasons('f')).containsExactly(Reason.OTHER_DATABASE_REACHABLE);

        catalog.reachableDatabases = List.of("master");
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("own database");
        assertThat(reasons('f')).containsExactly(Reason.OWN_DATABASE_NOT_LISTED);
    }

    @Test
    @DisplayName("ADR37-1 a catalog that cannot be read refuses every item, naming only the failure type, error number and SQLState")
    void undeterminableRefusesWithoutTheMessage() {
        catalog.unreachable = true;

        List<String> violations = ReadOnlyBaseline.violations(catalog);

        assertThat(violations).hasSize(7).allSatisfy(v -> assertThat(v).contains("cannot be determined").doesNotContain(FakeCatalog.LEAKED));
        assertThat(violations).extracting(v -> v.substring(0, 3)).containsExactly("(a)", "(b)", "(c)", "(d)", "(e)", "(f)", "(g)");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).as("nothing was seen, so nothing is a confirmed violation").isFalse();
        assertThat(ReadOnlyBaseline.check(catalog).unmet()).flatExtracting(Unmet::findings).isNotEmpty().allSatisfy(finding -> {
            assertThat(finding.reason()).isEqualTo(Reason.CATALOG_QUERY_FAILED);
            assertThat(finding.sqlError()).as("the failure carries no SQL exception").isEmpty();
        });
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
        assertThat(reasons('d')).containsExactly(Reason.SERVER_PERMISSION_ROW_NOT_BASELINE);
        assertThat(counts('d')).containsExactly(3);
    }

    @Test
    @DisplayName("ADR37-1 RULING-ADR37-11 (d) one more server-level DENY row is refused: the rows must be exactly GRANT CONNECT SQL")
    void oneMoreServerDenyIsRefused() {
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "D", "CREATE LOGIN"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)").contains("more [100 SERVER D CREATE LOGIN]");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).isTrue();
        assertThat(reasons('d')).containsExactly(Reason.SERVER_PERMISSION_ROW_NOT_BASELINE);
        assertThat(counts('d')).containsExactly(1);
    }

    @Test
    @DisplayName("ADR37-1 RULING-ADR37-11 (d) one more direct GRANT within the platform baseline is refused as well")
    void oneMoreServerGrantWithinTheBaselineIsRefused() {
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "G", "VIEW SERVER STATE"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)").contains("more [100 SERVER G VIEW SERVER STATE]");
        assertThat(reasons('d')).containsExactly(Reason.SERVER_PERMISSION_ROW_NOT_BASELINE);
    }

    @Test
    @DisplayName("ADR37-1 RULING-ADR37-11 (d) rows without GRANT CONNECT SQL are refused; no row at all cannot be determined")
    void missingConnectSqlRow() {
        catalog.serverPermissionRows.clear();
        catalog.serverPermissionRows.add(new ServerPermissionRow(100, "SERVER", "W", "CONNECT SQL"));
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)").contains("lacks 100 SERVER G CONNECT SQL");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).isTrue();
        assertThat(reasons('d')).containsExactly(Reason.SERVER_PERMISSION_ROW_NOT_BASELINE);
        assertThat(counts('d')).as("one row more, the GRANT row lacking").containsExactly(2);

        catalog.serverPermissionRows.clear();
        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(d)").contains("cannot be determined");
        assertThat(ReadOnlyBaseline.check(catalog).violated()).isFalse();
        assertThat(reasons('d')).containsExactly(Reason.EMPTY_ANSWER);
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
        assertThat(reasons('g')).containsExactly(Reason.SECURABLE_GRANT_BEYOND_SELECT);
        assertThat(counts('g')).containsExactly(5);
    }

    @Test
    @DisplayName("ADR37-1 复审 H-2 (f) a user of the login's own in a system database (only guest is allowed there) is named under (f)")
    void systemDatabasesOnlyThroughGuest() {
        catalog.ownUsers.put("msdb", 1L);

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("[msdb]").contains("guest");
        assertThat(reasons('f')).containsExactly(Reason.SYSTEM_DATABASE_OWN_USER);
    }

    @Test
    @DisplayName("ADR37-1 复审 L-4 (f) a user database is not a system database because of its name: MSDB with a user database id is refused")
    void systemDatabasesByIdNotByName() {
        catalog.reachableDatabaseRows = List.of(new ReachableDatabase(6, "legacyworld"), new ReachableDatabase(1, "master"),
                new ReachableDatabase(7, "MSDB"), new ReachableDatabase(8, "RDSCORE"));

        assertThat(ReadOnlyBaseline.violations(catalog)).singleElement().asString().startsWith("(f)").contains("[MSDB, RDSCORE]");
        assertThat(reasons('f')).containsExactly(Reason.OTHER_DATABASE_REACHABLE);
        assertThat(counts('f')).containsExactly(2);
    }

    @Test
    @DisplayName("ADR37-1 M-1 an empty answer is undeterminable, not a confirmed violation; one confirmed item makes the whole check a violation")
    void undeterminableIsToldApart() {
        catalog.serverPermissions.clear();
        Verdict emptyOnly = ReadOnlyBaseline.check(catalog);
        assertThat(emptyOnly.passed()).isFalse();
        assertThat(emptyOnly.violated()).isFalse();
        assertThat(emptyOnly.described()).singleElement().asString().startsWith("(d)").contains("empty");
        assertThat(emptyOnly.unmet().getFirst().findings()).extracting(Finding::reason).containsExactly(Reason.EMPTY_ANSWER);

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
            assertThat(unmet.findings()).extracting(Finding::reason).containsExactly(Reason.EFFECTIVE_PERMISSIONS_DIFFER, Reason.FIXED_SERVER_ROLE_UNANSWERED);
        });
    }

    @Test
    @DisplayName("PAR-2 a query that gives no answer and a rule that fails on an answer are undeterminable, each with its own reason code")
    void noAnswerAndAFailingRule() {
        catalog.roles = null;
        catalog.databaseLevelPermissions.add(new DatabaseLevelPermission(null, "SELECT"));

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.violated()).isFalse();
        assertThat(verdict.described()).hasSize(2);
        assertThat(reasons('b')).containsExactly(Reason.CHECK_FAILED);
        assertThat(reasons('c')).containsExactly(Reason.NO_ANSWER);
    }

    @Test
    @DisplayName("PAR-2 a failed query carries the error number and SQLState of the innermost SQL exception (the driver's, below the pool's), never its message")
    void failedQueryCarriesTheDriversSqlError() {
        SQLException driver = new SQLException("Cannot open database " + FakeCatalog.LEAKED, "S0001", 4060);
        catalog.failure = new CannotGetJdbcConnectionException("pool", new SQLTransientConnectionException("pool timed out", "S0001", 0, driver));

        Verdict verdict = ReadOnlyBaseline.check(catalog);

        assertThat(verdict.unmet()).hasSize(7).allSatisfy(unmet -> {
            assertThat(unmet.findings()).isNotEmpty().allSatisfy(finding -> {
                assertThat(finding.reason()).isEqualTo(Reason.CATALOG_QUERY_FAILED);
                assertThat(finding.sqlError()).contains(new SqlError(4060, "S0001"));
            });
            assertThat(unmet.problem()).doesNotContain(FakeCatalog.LEAKED);
        });
        assertThat(SqlError.of(new IllegalStateException("no SQL here"))).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("PAR-2 count adds up per call: when nothing can be read, each item counts its failed queries ((d) three, (f) two, the others one)")
    void failedQueriesAreCountedPerCall() {
        catalog.unreachable = true;

        assertThat(ReadOnlyBaseline.check(catalog).unmet()).extracting(u -> u.item() + "=" + u.findings().stream().mapToInt(Finding::count).sum())
                .containsExactly("a=1", "b=1", "c=1", "d=3", "e=1", "f=2", "g=1");
    }

    @Test
    @DisplayName("PAR-2 a driver that gives no SQLState leaves the state empty, not invented")
    void missingSqlStateStaysEmpty() {
        assertThat(SqlError.of(new SQLException("x"))).contains(new SqlError(0, null));
    }

    @Test
    @DisplayName("PAR-2 every counterexample yields its reason code on its item")
    void everyCounterexampleYieldsItsReason() {
        for (Counterexample counterexample : COUNTEREXAMPLES) {
            FakeCatalog bent = new FakeCatalog();
            counterexample.bend().accept(bent);

            assertThat(unmet(bent, counterexample.item()).findings()).as(counterexample.name()).extracting(Finding::reason)
                    .contains(counterexample.reason());
        }
    }

    @Test
    @DisplayName("PAR-2 every reason code is reached by at least one counterexample")
    void everyReasonIsReachedByACounterexample() {
        Set<Reason> reached = EnumSet.noneOf(Reason.class);
        COUNTEREXAMPLES.forEach(counterexample -> reached.add(counterexample.reason()));

        assertThat(reached).containsExactlyInAnyOrder(Reason.values());
    }
}
