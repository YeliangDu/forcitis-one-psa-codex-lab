package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * IMP-1022-PAR-05 (MIGRATION_WRITER_ROLE_DESIGN.md section 3.1, ADR-0036): the migration role of the work calendar has SELECT and INSERT only, writes a pending
 * migrated version with its days, and cannot confirm or withdraw it; a person confirms it through the application role. Nobody else writes migrated rows.
 */
class CalendarMigrationWriterIT {
    static final String VERSIONS = "organization_attendance.work_calendar_versions";
    static final String DAYS = "organization_attendance.work_calendar_version_days";
    static final String REFUSED = "can only be written by the owner role or the migration role";
    static final String PERSON = "01900000-0000-7000-8000-000000000b01";

    static PostgreSQLContainer postgres;

    @BeforeAll
    static void migrate() throws Exception {
        postgres = TestPostgres.container();
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .defaultSchema("one_migration").createSchemas(true).load().migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String role : new String[] {"one_test_owner", "one_test_history_loader", "one_test_app", "one_x_history_loader", "one_test_other"}) {
                st.execute("create role " + role + " nologin");
                st.execute("grant usage on schema organization_attendance to " + role);
                st.execute("grant select on organization_attendance.work_calendars to " + role);
            }
            st.execute("grant one_x_history_loader to one_test_app");
            st.execute("grant one_test_other to one_test_app");
            st.execute("alter table " + VERSIONS + " owner to one_test_owner");
            for (String role : new String[] {"one_test_history_loader", "one_x_history_loader"}) {
                st.execute("grant select, insert on " + VERSIONS + ", " + DAYS + " to " + role);
            }
            st.execute("grant select, insert, update on " + VERSIONS + " to one_test_app, one_test_other");
            st.execute("grant select, insert on " + DAYS + " to one_test_app, one_test_other, one_test_owner");
        }
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static UUID calendar() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("insert into organization_attendance.work_calendars (id, calendar_scope_kind, legal_entity_id, created_at, updated_at) values ('" + id
                    + "', 'LEGAL_ENTITY', '" + UUID.randomUUID() + "', now(), now())");
        }
        return id;
    }

    /** Inserts as the role (through SET ROLE hops, "a>b") a migrated version with a holiday, a company day and a manual day. */
    static UUID migratedAs(String role, UUID calendar, String source) throws SQLException {
        UUID version = UUID.randomUUID();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String hop : role.split(">")) {
                st.execute("set role " + hop);
            }
            boolean migration = source.equals("MIGRATION");
            st.execute("insert into " + VERSIONS + " (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date, authored_by, authored_at, created_at, updated_at)"
                    + " values ('" + version + "', '" + calendar + "', 2026, 1, 'PENDING_CONFIRMATION', '" + source + "', DATE '2020-01-01', "
                    + (migration ? "null, null" : "'" + PERSON + "', now()") + ", now(), now())");
            day(st, version, "2026-10-03", "HOLIDAY", "OFFICIAL", "null", "null");
            day(st, version, "2026-10-10", "HOLIDAY", "COMPANY", "null", "null");
            day(st, version, "2026-10-04", "MAKE_UP_WORKDAY_CANCELLED", "COMPANY", "'cancelled by the company'", "null");
            day(st, version, "2026-10-17", "HOLIDAY", "MANUAL", "'corrected by hand'", "'" + PERSON + "'");
        }
        return version;
    }

    static void day(Statement st, UUID version, String date, String kind, String basis, String note, String approvedBy) throws SQLException {
        st.execute("insert into " + DAYS + " (id, work_calendar_version_id, year_number, calendar_date, day_kind, basis_kind, basis_note, approved_by, created_at, updated_at)"
                + " values (uuidv7(), '" + version + "', 2026, DATE '" + date + "', '" + kind + "', '" + basis + "', " + note + ", " + approvedBy + ", now(), now())");
    }

    static void confirmAs(String role, UUID version, String confirmedBy, String register) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("set role " + role);
            st.execute("update " + VERSIONS + " set confirmation_status = 'CONFIRMED', confirmed_by = " + confirmedBy + ", confirmed_at = now(), decision_audit_entry_id = uuidv7(),"
                    + " official_notice_number = 'n', official_notice_url = 'https://www.gov.cn/n', company_leave_register_kind = '" + register + "',"
                    + " company_leave_register_key = " + (register.equals("REGISTERED") ? "'key-1'" : "null") + " where id = '" + version + "'");
        }
    }

    @Test
    @DisplayName("the migration role writes a pending migrated version with its days (company and manual days too); the owner role does as well")
    void migrationRoleWritesPendingVersionsWithDays() {
        assertThatCode(() -> migratedAs("one_test_history_loader", calendar(), "MIGRATION")).doesNotThrowAnyException();
        assertThatCode(() -> migratedAs("one_test_owner", calendar(), "MIGRATION")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the application role cannot write a migrated version, not even after switching to a role it belongs to; a look-alike role of another environment is refused")
    void nobodyElseWritesMigratedVersions() {
        assertThatThrownBy(() -> migratedAs("one_test_app", calendar(), "MIGRATION")).hasMessageContaining(REFUSED);
        assertThatThrownBy(() -> migratedAs("one_test_app>one_test_other", calendar(), "MIGRATION")).hasMessageContaining(REFUSED);
        assertThatThrownBy(() -> migratedAs("one_x_history_loader", calendar(), "MIGRATION")).hasMessageContaining(REFUSED);
        assertThatThrownBy(() -> migratedAs("one_test_app>one_x_history_loader", calendar(), "MIGRATION")).hasMessageContaining(REFUSED);
    }

    @Test
    @DisplayName("the migration role has no UPDATE: it can neither confirm nor withdraw; a person confirms through the application role, and the confirmer is required")
    void aPersonConfirmsTheMigratedVersion() throws SQLException {
        UUID version = migratedAs("one_test_history_loader", calendar(), "MIGRATION");
        assertThatThrownBy(() -> confirmAs("one_test_history_loader", version, "'" + PERSON + "'::uuid", "REGISTERED")).hasMessageContaining("permission denied");
        assertThatThrownBy(() -> withdrawAs("one_test_history_loader", version)).hasMessageContaining("permission denied");
        assertThatThrownBy(() -> confirmAs("one_test_app", version, "null", "REGISTERED")).hasMessageContaining("ck_work_calendar_versions_confirmation_columns");
        assertThatThrownBy(() -> confirmAs("one_test_app", version, "'" + PERSON + "'::uuid", "NONE")).hasMessageContaining("REGISTERED");
        assertThatCode(() -> confirmAs("one_test_app", version, "'" + PERSON + "'::uuid", "REGISTERED")).doesNotThrowAnyException();
        assertThatThrownBy(() -> {
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.execute("set role one_test_history_loader");
                day(st, version, "2026-11-07", "HOLIDAY", "OFFICIAL", "null", "null");
            }
        }).hasMessageContaining("days can only be added to a calendar version that waits for confirmation");
    }

    static void withdrawAs(String role, UUID version) throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("set role " + role);
            st.execute("update " + VERSIONS + " set confirmation_status = 'WITHDRAWN', withdrawn_by = '" + PERSON + "', withdrawn_at = now(), withdraw_reason_text = 'x' where id = '" + version + "'");
        }
    }

    @Test
    @DisplayName("a migrated version cannot be inserted as already confirmed, by anyone")
    void migratedVersionsAreNeverInsertedConfirmed() throws SQLException {
        UUID calendar = calendar();
        for (String role : new String[] {"one_test_history_loader", "one_test_owner"}) {
            assertThatThrownBy(() -> {
                try (Connection c = connect(); Statement st = c.createStatement()) {
                    st.execute("set role " + role);
                    st.execute("insert into " + VERSIONS + " (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date, confirmed_by, confirmed_at,"
                            + " decision_audit_entry_id, official_notice_number, official_notice_url, company_leave_register_kind, created_at, updated_at) values (uuidv7(), '" + calendar
                            + "', 2026, 1, 'CONFIRMED', 'MIGRATION', DATE '2020-01-01', '" + PERSON + "', now(), uuidv7(), 'n', 'https://www.gov.cn/n', 'NONE', now(), now())");
                }
            }).hasMessageContaining("starts as PENDING_CONFIRMATION");
        }
    }

    @Test
    @DisplayName("the decision is false, never NULL, when the owner has no migration role or is not named after the owner role convention")
    void decisionIsFalseWithoutAMigrationRole() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create role plain_owner nologin");
            st.execute("create table public.plain_probe (id int)");
            st.execute("alter table public.plain_probe owner to plain_owner");
            st.execute("set role one_test_history_loader");
            try (ResultSet rs = st.executeQuery("select organization_attendance.is_migration_writer('public.plain_probe'::regclass)")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isFalse();
                assertThat(rs.wasNull()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("the decision functions are SECURITY INVOKER with a pinned search_path; the day guard is the one SECURITY DEFINER function, pinned and blind to who the caller is")
    void functionsArePinnedAndTheDayGuardIgnoresTheCaller() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            int seen = 0;
            try (ResultSet rs = st.executeQuery("select proname, prosecdef, proconfig::text, prosrc from pg_proc where pronamespace = 'organization_attendance'::regnamespace"
                    + " and proname in ('is_owner_member', 'is_migration_writer', 'guard_work_calendar_version_insert', 'guard_work_calendar_version_day_insert')")) {
                while (rs.next()) {
                    seen++;
                    String name = rs.getString(1);
                    assertThat(rs.getString(3)).as(name).contains("search_path=pg_catalog, pg_temp");
                    boolean definer = name.equals("guard_work_calendar_version_day_insert");
                    assertThat(rs.getBoolean(2)).as(name + " prosecdef").isEqualTo(definer);
                    if (definer) {
                        assertThat(rs.getString(4)).as("the definer body does not look at who calls").doesNotContainIgnoringCase("current_user").doesNotContainIgnoringCase("session_user")
                                .doesNotContainIgnoringCase("pg_has_role");
                    }
                }
            }
            assertThat(seen).isEqualTo(4);
            try (ResultSet rs = st.executeQuery("select p.oid::regprocedure::text from pg_proc p where p.prosecdef and p.pronamespace = 'organization_attendance'::regnamespace"
                    + " and (p.prosrc ~* 'insert\\s+into' or p.prosrc ~* 'update\\s+organization' or p.prosrc ~* 'delete\\s+from') and p.proname <> 'guard_work_calendar_version_change'")) {
                assertThat(rs.next()).as("a SECURITY DEFINER function of the schema that writes").isFalse();
            }
        }
    }

    @Test
    @DisplayName("the require_owner_for_migration_version function is gone: confirming and withdrawing do not look at the writer")
    void theOldOwnerCheckIsRemoved() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("select count(*) from pg_proc where proname = 'require_owner_for_migration_version'")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }
}
