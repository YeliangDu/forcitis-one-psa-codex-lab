package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** IMP-1022-NX5-08 (MIGRATION_WRITER_ROLE_DESIGN.md): the dedicated migration role of ADR-0036 may write migration rows, only the owner role may write seeded rows, and nobody else may write either. */
class MigrationWriterRoleIT {
    static final String VERSIONS = "platform_numbering.numbering_rule_versions";
    static final String INSERT = "insert into " + VERSIONS + " (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value,"
            + " effective_from_date, authored_at, status, source_kind, confirmed_by, confirmed_at) select uuidv7(), numbering_rule_id, %d, '{TYPE}-{seq:4}', 'NEVER', 1, DATE '2027-01-01', now(),"
            + " '%s', '%s', %s, %s from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'MW_TYPE_%d')";

    static PostgreSQLContainer postgres;
    static int version = 100;

    @BeforeAll
    static void migrate() throws Exception {
        postgres = TestPostgres.container();
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .defaultSchema("one_migration").createSchemas(true).load().migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create role one_test_owner nologin");
            st.execute("create role one_test_history_loader nologin");
            st.execute("create role one_test_app nologin");
            st.execute("create role one_x_history_loader nologin");
            st.execute("create role one_test_other nologin");
            st.execute("grant one_x_history_loader to one_test_app");
            st.execute("grant one_test_other to one_test_app");
            st.execute("alter table " + VERSIONS + " owner to one_test_owner");
            for (String role : new String[] {"one_test_owner", "one_test_history_loader", "one_test_app", "one_x_history_loader", "one_test_other"}) {
                st.execute("grant usage on schema platform_numbering to " + role);
                st.execute("grant select on platform_numbering.numbering_rules, platform_numbering.document_types to " + role);
            }
            for (String role : new String[] {"one_test_history_loader", "one_test_app", "one_x_history_loader", "one_test_other"}) {
                st.execute("grant insert on " + VERSIONS + " to " + role);
            }
        }
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static void insertAs(String role, String source) throws SQLException {
        insertAs(role, source, "PENDING_CONFIRMATION", "null");
    }

    static void insertAs(String role, String source, String status, String confirmedBy) throws SQLException {
        int n = version++;
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("insert into platform_numbering.document_types (document_type_id, code, owner_bc_code, is_finance_confirmation_required, effective_from_date)"
                    + " values (uuidv7(), 'MW_TYPE_" + n + "', 'cost.timesheet', false, DATE '2026-01-01')");
            st.execute("insert into platform_numbering.numbering_rules (numbering_rule_id, document_type_id, version, event_count)"
                    + " select uuidv7(), document_type_id, 1, 0 from platform_numbering.document_types where code = 'MW_TYPE_" + n + "'");
            for (String hop : role.split(">")) {
                st.execute("set role " + hop);
            }
            st.execute(INSERT.formatted(n, status, source, confirmedBy, confirmedBy.equals("null") ? "null" : "now()", n));
        }
    }

    @Test
    @DisplayName("the migration role writes migration rows and not seeded rows; the owner role writes both")
    void migrationRoleWritesMigrationRowsOnly() {
        assertThatCode(() -> insertAs("one_test_history_loader", "MIGRATION")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertAs("one_test_history_loader", "SEEDED")).hasMessageContaining("can only be written by a migration");
        assertThatCode(() -> insertAs("one_test_owner", "MIGRATION")).doesNotThrowAnyException();
        assertThatCode(() -> insertAs("one_test_owner", "SEEDED")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the application role cannot write either kind, not even after switching to another role it belongs to")
    void applicationRoleWritesNeither() {
        for (String source : new String[] {"MIGRATION", "SEEDED"}) {
            assertThatThrownBy(() -> insertAs("one_test_app", source)).hasMessageContaining("can only be written by a migration");
            assertThatThrownBy(() -> insertAs("one_test_app>one_test_other", source)).hasMessageContaining("can only be written by a migration");
        }
    }

    @Test
    @DisplayName("a look-alike migration role of another environment, granted to the application role, is not accepted")
    void impostorMigrationRoleIsRefused() {
        assertThatThrownBy(() -> insertAs("one_x_history_loader", "MIGRATION")).hasMessageContaining("can only be written by a migration");
        assertThatThrownBy(() -> insertAs("one_test_app", "MIGRATION")).hasMessageContaining("can only be written by a migration");
    }

    @Test
    @DisplayName("the decision is false, never NULL, when the owner has no migration role or is not named after the owner role convention")
    void decisionIsFalseWithoutAMigrationRole() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create role plain_owner nologin");
            st.execute("create role ghost_owner nologin");
            st.execute("create role bare nologin");
            st.execute("create role bare_history_loader nologin");
            st.execute("create table public.bare_probe (id int)");
            st.execute("alter table public.bare_probe owner to bare");
            st.execute("grant usage on schema platform_numbering to bare_history_loader");
            st.execute("create table public.plain_probe (id int)");
            st.execute("alter table public.plain_probe owner to plain_owner");
            st.execute("create table public.ghost_probe (id int)");
            st.execute("alter table public.ghost_probe owner to ghost_owner");
            st.execute("grant usage on schema platform_numbering to one_test_history_loader");
            st.execute("set role one_test_history_loader");
            for (String table : new String[] {"public.plain_probe", "public.ghost_probe"}) {
                assertThat(isWriter(st, table)).as(table).isFalse();
            }
            st.execute("reset role");
            st.execute("set role bare_history_loader");
            assertThat(isWriter(st, "public.bare_probe")).as("an owner whose name does not end in _owner has no migration role").isFalse();
            st.execute("reset role");
            st.execute("set role one_test_history_loader");
            for (String table : new String[] {"public.plain_probe", "public.ghost_probe"}) {
                try (ResultSet rs = st.executeQuery("select platform_numbering.is_migration_writer('" + table + "'::regclass)")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1)).isFalse();
                    assertThat(rs.wasNull()).as(table).isFalse();
                }
            }
        }
    }

    static boolean isWriter(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("select platform_numbering.is_migration_writer('" + table + "'::regclass)")) {
            rs.next();
            boolean value = rs.getBoolean(1);
            assertThat(rs.wasNull()).isFalse();
            return value;
        }
    }

    @Test
    @DisplayName("the migration role cannot insert a migrated version as already confirmed, so four eyes cannot be skipped through the migration path")
    void migratedVersionsAreInsertedPending() {
        assertThatThrownBy(() -> insertAs("one_test_history_loader", "MIGRATION", "CONFIRMED", "'01900000-0000-7000-8000-000000000a01'::uuid"))
                .hasMessageContaining("must be inserted pending confirmation");
        assertThatThrownBy(() -> insertAs("one_test_owner", "MIGRATION", "CONFIRMED", "'01900000-0000-7000-8000-000000000a01'::uuid"))
                .hasMessageContaining("must be inserted pending confirmation");
    }

    @Test
    @DisplayName("no SECURITY DEFINER function touches the version table: the trigger would see the definer as the current user and let the caller write seeded rows")
    void noDefinerFunctionWritesTheVersionTable() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("select p.oid::regprocedure::text from pg_proc p where p.prosecdef and p.prosrc ilike '%numbering_rule_versions%'")) {
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("the decision functions are SECURITY INVOKER with a pinned search_path, since a definer would see the owner as the current user")
    void decisionFunctionsAreInvokerAndPinned() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("select proname, prosecdef, proconfig::text from pg_proc where pronamespace = 'platform_numbering'::regnamespace"
                        + " and proname in ('is_owner_member', 'is_migration_writer', 'guard_rule_version_source')")) {
            int seen = 0;
            while (rs.next()) {
                seen++;
                assertThat(rs.getBoolean(2)).as(rs.getString(1)).isFalse();
                assertThat(rs.getString(3)).as(rs.getString(1)).contains("search_path=pg_catalog, pg_temp");
            }
            assertThat(seen).isEqualTo(3);
        }
    }
}
