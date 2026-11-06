package com.forcitis.one.accounting.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1439 (P1 data design record accounting-spine-p1): every constraint and trigger of accounting_period.fiscal_periods, exercised through SQL. */
@Testcontainers
class FiscalPeriodsMigrationIT {

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    static final String RESTRICT_VIOLATION = "23001";
    static final String CHECK_VIOLATION = "23514";
    static final String UNIQUE_VIOLATION = "23505";
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-01-02T02:00:00Z");

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").load().migrate();
    }

    private static Connection connect() throws SQLException {
        Connection db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db.setAutoCommit(false);
        return db;
    }

    private static void run(Connection db, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        }
    }

    private static String text(Connection db, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    interface Throwing {
        void run() throws SQLException;
    }

    /** A refused statement must not abort the transaction: it runs under a savepoint that is rolled back. */
    private static void refused(Connection db, String state, String rule, Throwing statement) throws SQLException {
        Savepoint before = db.setSavepoint();
        SQLException refusal;
        try {
            refusal = catchThrowableOfType(SQLException.class, statement::run);
        } finally {
            db.rollback(before);
            db.releaseSavepoint(before);
        }
        assertThat((Throwable) refusal).as("the database refuses: " + rule).isNotNull();
        assertThat(refusal.getSQLState()).as("SQLSTATE for " + rule + ": " + refusal.getMessage()).isEqualTo(state);
        assertThat(refusal.getMessage()).as("the refusal names the rule: " + rule).contains(rule);
    }

    private static UUID period(Connection db, UUID entity, int year, int number, String status) throws SQLException {
        UUID id = UUID.randomUUID();
        run(db, """
                insert into accounting_period.fiscal_periods (id, legal_entity_id, fiscal_year_number, period_number, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, 0)
                """, id, entity, year, number, status, AT, AT);
        return id;
    }

    private static void moveTo(Connection db, UUID id, String status) throws SQLException {
        run(db, "update accounting_period.fiscal_periods set status = ?, updated_at = ?, version = version + 1 where id = ?", status, AT.plusDays(1), id);
    }

    @Test
    @DisplayName("#1439 a period is inserted in any status; the full key is unique per entity, year and number")
    void insertAndUniqueKey() throws Exception {
        try (Connection db = connect()) {
            UUID entity = UUID.randomUUID();
            period(db, entity, 2026, 1, "OPEN");
            period(db, entity, 2026, 2, "CLOSING");
            period(db, entity, 2026, 3, "CLOSED");
            period(db, entity, 2026, 13, "OPEN");
            period(db, UUID.randomUUID(), 2026, 1, "OPEN");
            period(db, entity, 2027, 1, "OPEN");
            refused(db, UNIQUE_VIOLATION, "uk_fiscal_periods_entity_year_number", () -> period(db, entity, 2026, 1, "OPEN"));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1439 year 2000 to 2999, number 1 to 13 and the three statuses are checked")
    void checks() throws Exception {
        try (Connection db = connect()) {
            UUID entity = UUID.randomUUID();
            period(db, entity, 2000, 1, "OPEN");
            period(db, entity, 2999, 13, "OPEN");
            refused(db, CHECK_VIOLATION, "ck_fiscal_periods_fiscal_year_number", () -> period(db, entity, 1999, 1, "OPEN"));
            refused(db, CHECK_VIOLATION, "ck_fiscal_periods_fiscal_year_number", () -> period(db, entity, 3000, 1, "OPEN"));
            refused(db, CHECK_VIOLATION, "ck_fiscal_periods_period_number", () -> period(db, entity, 2026, 0, "OPEN"));
            refused(db, CHECK_VIOLATION, "ck_fiscal_periods_period_number", () -> period(db, entity, 2026, 14, "OPEN"));
            refused(db, CHECK_VIOLATION, "ck_fiscal_periods_status", () -> period(db, entity, 2026, 2, "REOPENED"));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1439 INV-99 only OPEN to CLOSING and CLOSING to CLOSED are legal, touching status, updated_at and version only")
    void statusEdges() throws Exception {
        try (Connection db = connect()) {
            UUID entity = UUID.randomUUID();
            UUID open = period(db, entity, 2026, 1, "OPEN");
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> moveTo(db, open, "CLOSED"));
            moveTo(db, open, "CLOSING");
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> moveTo(db, open, "OPEN"));
            moveTo(db, open, "CLOSED");
            assertThat(text(db, "select status from accounting_period.fiscal_periods where id = ?", open)).isEqualTo("CLOSED");

            UUID other = period(db, entity, 2026, 2, "OPEN");
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db,
                    "update accounting_period.fiscal_periods set status = 'CLOSING', fiscal_year_number = 2027 where id = ?", other));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db,
                    "update accounting_period.fiscal_periods set status = 'CLOSING', legal_entity_id = ? where id = ?", UUID.randomUUID(), other));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db,
                    "update accounting_period.fiscal_periods set fiscal_year_number = 2027 where id = ?", other));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1551 a move adds exactly 1 to version and never sets updated_at back; the moves that obey both still work")
    void versionAndUpdatedAtMoveForward() throws Exception {
        try (Connection db = connect()) {
            UUID entity = UUID.randomUUID();
            UUID id = period(db, entity, 2026, 1, "OPEN");
            String move = "update accounting_period.fiscal_periods set status = 'CLOSING', updated_at = ?, version = ? where id = ?";
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, move, AT.plusDays(1), 0L, id));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, move, AT.plusDays(1), 2L, id));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, move, AT.plusDays(1), -1L, id));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, move, AT.minusSeconds(1), 1L, id));
            assertThat(text(db, "select status || version from accounting_period.fiscal_periods where id = ?", id)).as("nothing moved").isEqualTo("OPEN0");

            run(db, move, AT, 1L, id);
            assertThat(text(db, "select status || version from accounting_period.fiscal_periods where id = ?", id)).as("the same instant is not earlier").isEqualTo("CLOSING1");
            String close = "update accounting_period.fiscal_periods set status = 'CLOSED', updated_at = ?, version = ? where id = ?";
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, close, AT.plusDays(1), 1L, id));
            run(db, close, AT.plusDays(1), 2L, id);
            assertThat(text(db, "select status || version from accounting_period.fiscal_periods where id = ?", id)).isEqualTo("CLOSED2");
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1439 INV-99 a CLOSED period accepts no update of any column")
    void closedIsFinal() throws Exception {
        try (Connection db = connect()) {
            UUID closed = period(db, UUID.randomUUID(), 2026, 1, "CLOSED");
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, "update accounting_period.fiscal_periods set updated_at = ? where id = ?", AT.plusDays(1), closed));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> moveTo(db, closed, "OPEN"));
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> moveTo(db, closed, "CLOSING"));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1439 no period row is ever deleted or truncated, in any status")
    void noDelete() throws Exception {
        try (Connection db = connect()) {
            UUID entity = UUID.randomUUID();
            for (String status : new String[] {"OPEN", "CLOSING", "CLOSED"}) {
                UUID id = period(db, entity, 2026, "OPEN".equals(status) ? 1 : "CLOSING".equals(status) ? 2 : 3, status);
                refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_change", () -> run(db, "delete from accounting_period.fiscal_periods where id = ?", id));
            }
            refused(db, RESTRICT_VIOLATION, "tg_fiscal_periods_truncate", () -> run(db, "truncate accounting_period.fiscal_periods"));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1439 the migration writes no period row")
    void noSeedRows() throws Exception {
        try (Connection db = connect()) {
            assertThat(text(db, "select count(*) from accounting_period.fiscal_periods")).isEqualTo("0");
        }
    }
}
