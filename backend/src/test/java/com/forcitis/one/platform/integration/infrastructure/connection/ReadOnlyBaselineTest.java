package com.forcitis.one.platform.integration.infrastructure.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
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

        assertThat(violations).hasSize(6).allSatisfy(v -> assertThat(v).contains("cannot be determined").doesNotContain(FakeCatalog.LEAKED));
        assertThat(violations).extracting(v -> v.substring(0, 3)).containsExactly("(a)", "(b)", "(c)", "(d)", "(e)", "(f)");
    }
}
