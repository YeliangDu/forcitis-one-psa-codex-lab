package com.forcitis.one.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestPostgres;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1429 (PY0): plain SQL probes of the signed schema; each constraint is shown refused once and the valid row shown accepted. */
@Testcontainers
class PayrollMigrationIT {
    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-05T02:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final UUID DRAFTER = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");
    private static final UUID APPROVER = UUID.fromString("019a0000-0000-7000-8000-0000000000a2");
    private static final String VERSIONS = "organization_payroll.payroll_rule_versions";
    private static final String ROWS = "organization_payroll.payroll_rule_rows";
    private static final String CHECK = "23514";
    private static final String EXCLUSION = "23P01";
    private static final String UNIQUE = "23505";
    private static final String RESTRICT = "23001";

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    private static Connection connect() throws SQLException {
        Connection db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db.setAutoCommit(false);
        return db;
    }

    interface Sql {
        void run() throws SQLException;
    }

    private static void refused(Connection db, String state, String rule, Sql action) throws SQLException {
        var savepoint = db.setSavepoint();
        SQLException refusal;
        try {
            refusal = catchThrowableOfType(SQLException.class, action::run);
        } finally {
            db.rollback(savepoint);
            db.releaseSavepoint(savepoint);
        }
        assertThat((Throwable) refusal).as("database refuses " + rule).isNotNull();
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(state);
        assertThat(refusal.getMessage()).contains(rule);
    }

    private static void insert(Connection db, String table, Map<String, Object> row) throws SQLException {
        String columns = String.join(", ", row.keySet());
        String marks = String.join(", ", row.keySet().stream().map(c -> "?").toList());
        try (var statement = db.prepareStatement("insert into " + table + " (" + columns + ") values (" + marks + ")")) {
            int i = 1;
            for (Object value : row.values()) {
                statement.setObject(i++, value);
            }
            statement.execute();
        }
    }

    private static void exec(Connection db, String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
        }
    }

    private static Map<String, Object> version(String kind, String jurisdiction, LocalDate from, LocalDate to, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("rule_kind", kind);
        row.put("jurisdiction_key", jurisdiction);
        row.put("effective_from_date", from);
        row.put("effective_to_date", to);
        row.put("status", status);
        row.put("drafted_by_user_id", DRAFTER);
        row.put("approved_by_user_id", status.equals("DRAFT") ? null : APPROVER);
        row.put("published_at", status.equals("DRAFT") ? null : AT);
        row.put("source_note", "test citation");
        row.put("created_at", AT);
        row.put("updated_at", AT);
        return row;
    }

    private static UUID draft(Connection db, String kind, String jurisdiction, LocalDate from, LocalDate to) throws SQLException {
        Map<String, Object> row = version(kind, jurisdiction, from, to, "DRAFT");
        insert(db, VERSIONS, row);
        return (UUID) row.get("id");
    }

    private static void publish(Connection db, UUID id) throws SQLException {
        exec(db, "update " + VERSIONS + " set status = 'EFFECTIVE', approved_by_user_id = ?, published_at = ?, version = version + 1 where id = ?", APPROVER, AT, id);
    }

    private static Map<String, Object> rowOf(UUID versionId, String kind, String key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("rule_version_id", versionId);
        row.put("row_kind", kind);
        row.put("row_key", key);
        row.put("created_at", AT);
        row.put("updated_at", AT);
        return row;
    }

    private static Map<String, Object> validRow(UUID versionId, String kind) {
        Map<String, Object> row;
        switch (kind) {
            case "INCOME_TAX_BAND" -> {
                row = rowOf(versionId, kind, "01");
                row.put("lower_bound_amount", BigDecimal.ZERO);
                row.put("lower_bound_currency", "CNY");
                row.put("upper_bound_amount", new BigDecimal("1000.00"));
                row.put("upper_bound_currency", "CNY");
                row.put("applied_rate", new BigDecimal("0.030000"));
                row.put("quick_deduction_amount", BigDecimal.ZERO);
                row.put("quick_deduction_currency", "CNY");
            }
            case "INCOME_TAX_MONTHLY_BASIC_DEDUCTION" -> {
                row = rowOf(versionId, kind, "-");
                row.put("flat_amount", new BigDecimal("5000.00"));
                row.put("flat_currency", "CNY");
            }
            case "SOCIAL_INSURANCE_RATE", "HOUSING_FUND_RATE" -> {
                row = rowOf(versionId, kind, "pension");
                row.put("party_kind", "EMPLOYEE");
                row.put("applied_rate", new BigDecimal("0.080000"));
            }
            case "SOCIAL_INSURANCE_BASE_LIMIT", "HOUSING_FUND_BASE_LIMIT" -> {
                row = rowOf(versionId, kind, "pension");
                row.put("lower_bound_amount", new BigDecimal("1000.00"));
                row.put("lower_bound_currency", "CNY");
                row.put("upper_bound_amount", new BigDecimal("2000.00"));
                row.put("upper_bound_currency", "CNY");
            }
            default -> {
                row = rowOf(versionId, "ROUNDING_MODE", "-");
                row.put("rounding_kind", "HALF_UP");
            }
        }
        return row;
    }

    private static final List<String> ROW_KINDS = List.of("INCOME_TAX_BAND", "INCOME_TAX_MONTHLY_BASIC_DEDUCTION", "SOCIAL_INSURANCE_RATE",
            "SOCIAL_INSURANCE_BASE_LIMIT", "HOUSING_FUND_RATE", "HOUSING_FUND_BASE_LIMIT", "ROUNDING_MODE");

    @Test
    @DisplayName("#1429 the valid row of every row kind is accepted in a draft version")
    void everyRowKindHasAValidShape() throws SQLException {
        try (Connection db = connect()) {
            UUID id = draft(db, "INCOME_TAX", "CN", FROM, null);
            for (String kind : ROW_KINDS) {
                insert(db, ROWS, validRow(id, kind));
            }
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1429 each version constraint refuses its violating row")
    void versionConstraintsRefuse() throws SQLException {
        try (Connection db = connect()) {
            refused(db, CHECK, "ck_payroll_rule_versions_rule_kind", () -> insert(db, VERSIONS, version("BONUS", "CN", FROM, null, "DRAFT")));
            refused(db, CHECK, "ck_payroll_rule_versions_jurisdiction_key", () -> insert(db, VERSIONS, version("INCOME_TAX", "cn", FROM, null, "DRAFT")));
            refused(db, CHECK, "ck_payroll_rule_versions_effective_interval", () -> insert(db, VERSIONS, version("INCOME_TAX", "CN", FROM, FROM, "DRAFT")));
            // the change trigger refuses a non-DRAFT insert before the status CHECK is evaluated, so the value list is shown refused through it
            refused(db, RESTRICT, "inserted as DRAFT", () -> insert(db, VERSIONS, withStatus("RETIRED")));
            UUID id = draft(db, "INCOME_TAX", "CN", FROM, null);
            refused(db, CHECK, "ck_payroll_rule_versions_approver_columns", () -> exec(db,
                    "update " + VERSIONS + " set status = 'EFFECTIVE', published_at = ? where id = ?", AT, id));
            refused(db, CHECK, "ck_payroll_rule_versions_drafter_not_approver", () -> exec(db,
                    "update " + VERSIONS + " set status = 'EFFECTIVE', approved_by_user_id = ?, published_at = ? where id = ?", DRAFTER, AT, id));
            Map<String, Object> blankNote = version("INCOME_TAX", "CN", FROM, null, "DRAFT");
            blankNote.put("source_note", " ");
            refused(db, CHECK, "ck_payroll_rule_versions_source_note", () -> insert(db, VERSIONS, blankNote));
            db.rollback();
        }
    }

    private static Map<String, Object> withStatus(String status) {
        Map<String, Object> row = version("INCOME_TAX", "CN", FROM, null, "DRAFT");
        row.put("status", status);
        row.put("approved_by_user_id", APPROVER);
        row.put("published_at", AT);
        return row;
    }

    @Test
    @DisplayName("#1429 INV-139: two effective intervals of one kind and jurisdiction overlap and are refused; other jurisdictions and drafts may overlap")
    void overlapIsRefused() throws SQLException {
        try (Connection db = connect()) {
            effective(db, "SOCIAL_INSURANCE", "SH", FROM, LocalDate.of(2026, 7, 1));
            UUID overlapping = draft(db, "SOCIAL_INSURANCE", "SH", LocalDate.of(2026, 6, 30), null);
            refused(db, EXCLUSION, "ex_payroll_rule_versions_effective_interval_overlap", () -> publish(db, overlapping));
            effective(db, "SOCIAL_INSURANCE", "SH", LocalDate.of(2026, 7, 1), null);
            effective(db, "SOCIAL_INSURANCE", "BJ", FROM, null);
            effective(db, "HOUSING_FUND", "SH", FROM, null);
            db.rollback();
        }
    }

    private static UUID effective(Connection db, String kind, String jurisdiction, LocalDate from, LocalDate to) throws SQLException {
        UUID id = draft(db, kind, jurisdiction, from, to);
        publish(db, id);
        return id;
    }

    @Test
    @DisplayName("#1429 the change trigger allows only draft to draft, publish, supersede and deleting a draft")
    void changeTriggerAllowsTheLegalWritesOnly() throws SQLException {
        try (Connection db = connect()) {
            refused(db, RESTRICT, "inserted as DRAFT", () -> insert(db, VERSIONS, version("INCOME_TAX", "CN", FROM, null, "EFFECTIVE")));
            UUID id = draft(db, "INCOME_TAX", "CN", FROM, null);
            exec(db, "update " + VERSIONS + " set source_note = 'other', jurisdiction_key = 'CN', version = version + 1 where id = ?", id);
            refused(db, RESTRICT, "cannot become", () -> exec(db, "update " + VERSIONS + " set status = 'SUPERSEDED', effective_to_date = ? where id = ?",
                    LocalDate.of(2027, 1, 1), id));
            refused(db, RESTRICT, "changes only its status and approval", () -> exec(db,
                    "update " + VERSIONS + " set status = 'EFFECTIVE', approved_by_user_id = ?, published_at = ?, source_note = 'changed in passing' where id = ?", APPROVER, AT, id));
            publish(db, id);
            refused(db, RESTRICT, "cannot become", () -> exec(db, "update " + VERSIONS + " set status = 'DRAFT', approved_by_user_id = null, published_at = null where id = ?", id));
            refused(db, RESTRICT, "cannot become", () -> exec(db, "update " + VERSIONS + " set source_note = 'rewritten' where id = ?", id));
            refused(db, RESTRICT, "only a DRAFT", () -> exec(db, "delete from " + VERSIONS + " where id = ?", id));
            refused(db, RESTRICT, "only closes its open interval", () -> exec(db,
                    "update " + VERSIONS + " set status = 'SUPERSEDED', effective_to_date = ?, source_note = 'x' where id = ?", LocalDate.of(2027, 1, 1), id));
            refused(db, CHECK, "ck_payroll_rule_versions_supersede_has_end", () -> exec(db, "update " + VERSIONS + " set status = 'SUPERSEDED' where id = ?", id));
            exec(db, "update " + VERSIONS + " set status = 'SUPERSEDED', effective_to_date = ?, version = version + 1 where id = ?", LocalDate.of(2027, 1, 1), id);
            refused(db, RESTRICT, "cannot become", () -> exec(db, "update " + VERSIONS + " set effective_to_date = ? where id = ?", LocalDate.of(2027, 2, 1), id));
            refused(db, RESTRICT, "cannot be truncated", () -> exec(db, "truncate " + VERSIONS + " cascade"));
            UUID second = draft(db, "ROUNDING", "CN", FROM, null);
            exec(db, "delete from " + VERSIONS + " where id = ?", second);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1429 each row-kind shape is refused when a column the kind forbids is set or a required one is missing")
    void rowKindShapesAreEnforced() throws SQLException {
        try (Connection db = connect()) {
            UUID id = draft(db, "INCOME_TAX", "CN", FROM, null);
            for (String kind : ROW_KINDS) {
                Map<String, Object> extra = validRow(id, kind);
                extra.put(kind.equals("ROUNDING_MODE") ? "applied_rate" : "rounding_kind", kind.equals("ROUNDING_MODE") ? new BigDecimal("0.1") : "HALF_UP");
                refused(db, CHECK, "ck_payroll_rule_rows_kind_columns", () -> insert(db, ROWS, extra));
            }
            Map<String, Object> noRate = validRow(id, "INCOME_TAX_BAND");
            noRate.remove("applied_rate");
            refused(db, CHECK, "ck_payroll_rule_rows_kind_columns", () -> insert(db, ROWS, noRate));
            Map<String, Object> noParty = validRow(id, "SOCIAL_INSURANCE_RATE");
            noParty.remove("party_kind");
            refused(db, CHECK, "ck_payroll_rule_rows_kind_columns", () -> insert(db, ROWS, noParty));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1429 the column-level constraints of a rule row each refuse their violation")
    void rowColumnConstraintsRefuse() throws SQLException {
        try (Connection db = connect()) {
            UUID id = draft(db, "INCOME_TAX", "CN", FROM, null);
            Map<String, Object> badKind = validRow(id, "ROUNDING_MODE");
            badKind.put("row_kind", "BONUS");
            // an unknown kind also fails the per-kind CHECK, which PostgreSQL names first (constraint names in order)
            refused(db, CHECK, "ck_payroll_rule_rows_kind_columns", () -> insert(db, ROWS, badKind));
            Map<String, Object> badKey = validRow(id, "INCOME_TAX_BAND");
            badKey.put("row_key", "1");
            refused(db, CHECK, "ck_payroll_rule_rows_row_key", () -> insert(db, ROWS, badKey));
            Map<String, Object> badParty = validRow(id, "SOCIAL_INSURANCE_RATE");
            badParty.put("party_kind", "STATE");
            refused(db, CHECK, "ck_payroll_rule_rows_party_kind", () -> insert(db, ROWS, badParty));
            Map<String, Object> halfPair = validRow(id, "INCOME_TAX_BAND");
            halfPair.put("lower_bound_currency", null);
            refused(db, CHECK, "ck_payroll_rule_rows_lower_bound_pair", () -> insert(db, ROWS, halfPair));
            Map<String, Object> upperPair = validRow(id, "INCOME_TAX_BAND");
            upperPair.put("upper_bound_currency", null);
            refused(db, CHECK, "ck_payroll_rule_rows_upper_bound_pair", () -> insert(db, ROWS, upperPair));
            Map<String, Object> quickPair = validRow(id, "INCOME_TAX_BAND");
            quickPair.put("quick_deduction_currency", null);
            refused(db, CHECK, "ck_payroll_rule_rows_quick_deduction_pair", () -> insert(db, ROWS, quickPair));
            Map<String, Object> flatPair = validRow(id, "INCOME_TAX_MONTHLY_BASIC_DEDUCTION");
            flatPair.put("flat_currency", null);
            refused(db, CHECK, "ck_payroll_rule_rows_flat_pair", () -> insert(db, ROWS, flatPair));
            Map<String, Object> usd = validRow(id, "INCOME_TAX_MONTHLY_BASIC_DEDUCTION");
            usd.put("flat_currency", "USD");
            refused(db, CHECK, "ck_payroll_rule_rows_currency", () -> insert(db, ROWS, usd));
            Map<String, Object> negative = validRow(id, "INCOME_TAX_MONTHLY_BASIC_DEDUCTION");
            negative.put("flat_amount", new BigDecimal("-1.00"));
            refused(db, CHECK, "ck_payroll_rule_rows_amounts_not_negative", () -> insert(db, ROWS, negative));
            Map<String, Object> rate = validRow(id, "SOCIAL_INSURANCE_RATE");
            rate.put("applied_rate", new BigDecimal("1.000001"));
            refused(db, CHECK, "ck_payroll_rule_rows_applied_rate", () -> insert(db, ROWS, rate));
            Map<String, Object> rounding = validRow(id, "ROUNDING_MODE");
            rounding.put("rounding_kind", "UP");
            refused(db, CHECK, "ck_payroll_rule_rows_rounding_kind", () -> insert(db, ROWS, rounding));
            Map<String, Object> order = validRow(id, "SOCIAL_INSURANCE_BASE_LIMIT");
            order.put("upper_bound_amount", new BigDecimal("1000.00"));
            refused(db, CHECK, "ck_payroll_rule_rows_bounds_order", () -> insert(db, ROWS, order));
            Map<String, Object> orphan = validRow(UUID.randomUUID(), "ROUNDING_MODE");
            // the row trigger finds no draft parent before the foreign key is checked; the key itself is shown by deleting a version that has rows
            refused(db, RESTRICT, "never change", () -> insert(db, ROWS, orphan));
            insert(db, ROWS, validRow(id, "SOCIAL_INSURANCE_RATE"));
            refused(db, UNIQUE, "uk_payroll_rule_rows_version_kind_key_party", () -> insert(db, ROWS, validRow(id, "SOCIAL_INSURANCE_RATE")));
            Map<String, Object> otherSide = validRow(id, "SOCIAL_INSURANCE_RATE");
            otherSide.put("party_kind", "EMPLOYER");
            insert(db, ROWS, otherSide);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1429 the rows of a published version are frozen: no insert, update, delete or truncate, and a version with rows cannot be deleted")
    void publishedRowsAreFrozen() throws SQLException {
        try (Connection db = connect()) {
            UUID id = draft(db, "ROUNDING", "CN", FROM, null);
            Map<String, Object> rounding = validRow(id, "ROUNDING_MODE");
            insert(db, ROWS, rounding);
            exec(db, "update " + ROWS + " set rounding_kind = 'DOWN' where id = ?", rounding.get("id"));
            refused(db, RESTRICT, "fk_payroll_rule_rows_rule_version_id", () -> exec(db, "delete from " + VERSIONS + " where id = ?", id));
            publish(db, id);
            refused(db, RESTRICT, "never change", () -> insert(db, ROWS, validRow(id, "ROUNDING_MODE")));
            refused(db, RESTRICT, "never change", () -> exec(db, "update " + ROWS + " set rounding_kind = 'HALF_EVEN' where id = ?", rounding.get("id")));
            refused(db, RESTRICT, "never change", () -> exec(db, "delete from " + ROWS + " where id = ?", rounding.get("id")));
            refused(db, RESTRICT, "cannot be truncated", () -> exec(db, "truncate " + ROWS));
            UUID other = draft(db, "ROUNDING", "SH", FROM, null);
            Map<String, Object> moved = validRow(other, "ROUNDING_MODE");
            insert(db, ROWS, moved);
            refused(db, RESTRICT, "never change", () -> exec(db, "update " + ROWS + " set rule_version_id = ? where id = ?", id, moved.get("id")));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1429 both tables and every column carry a comment, and the migration inserts no row")
    void commentsAreCompleteAndTheTablesAreEmpty() throws SQLException {
        try (Connection db = connect(); var statement = db.createStatement()) {
            List<String> missing = new ArrayList<>();
            try (var rows = statement.executeQuery("select c.relname || '.' || a.attname from pg_class c join pg_namespace n on n.oid = c.relnamespace "
                    + "join pg_attribute a on a.attrelid = c.oid and a.attnum > 0 and not a.attisdropped "
                    + "where n.nspname = 'organization_payroll' and c.relkind = 'r' and col_description(c.oid, a.attnum) is null")) {
                while (rows.next()) {
                    missing.add(rows.getString(1));
                }
            }
            assertThat(missing).as("columns without a comment").isEmpty();
            try (var rows = statement.executeQuery("select (select count(*) from " + VERSIONS + ") + (select count(*) from " + ROWS + ")")) {
                rows.next();
                assertThat(rows.getLong(1)).isZero();
            }
        }
    }
}
