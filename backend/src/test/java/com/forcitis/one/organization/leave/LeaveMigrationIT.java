package com.forcitis.one.organization.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1400 / #1470: plain SQL probes of the signed L1a and L1b schemas; no application validation masks a database refusal. */
@Testcontainers
class LeaveMigrationIT {
    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-05T02:00:00Z");
    private static final LocalDate MONTH = LocalDate.of(2026, 11, 1);
    private static final List<String> TABLES = List.of("leave_requests", "leave_request_days", "leave_request_holds",
            "overtime_requests", "attendance_records", "monthly_confirmations", "monthly_confirmation_lines",
            "leave_types", "leave_rule_versions", "leave_entitlement_rules", "leave_accounts", "leave_ledger_lines", "leave_grant_basis");
    private static final String SCHEMA = "organization_leave.";

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

    private static void run(Connection db, String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
        }
    }

    private static List<String> strings(Connection db, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (var statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
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
        if (rule.matches("(?:ck|fk|uk|pk|ex|tg)_.*")) {
            assertThat(((PSQLException) refusal).getServerErrorMessage().getConstraint()).isEqualTo(rule);
        } else {
            assertThat(refusal.getMessage()).contains(rule);
        }
    }

    private static Map<String, Object> row(String table) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("created_at", AT);
        row.put("updated_at", AT);
        switch (table) {
            case "leave_requests" -> {
                row.put("employment_relationship_id", UUID.randomUUID());
                row.put("employee_id", UUID.randomUUID());
                row.put("leave_type_id", UUID.randomUUID());
                row.put("start_at", AT);
                row.put("end_at", AT.plusHours(1));
                row.put("status", "DRAFT");
                row.put("approval_instance_id", null);
                row.put("legal_entity_id", UUID.randomUUID());
            }
            case "leave_request_days" -> {
                row.put("leave_request_id", UUID.randomUUID());
                row.put("work_date", MONTH);
                row.put("minutes_count", 0);
                row.put("day_hundredths_count", 0);
            }
            case "leave_request_holds" -> {
                row.put("leave_request_id", UUID.randomUUID());
                row.put("ledger_line_id", UUID.randomUUID());
            }
            case "overtime_requests" -> {
                row.put("employment_relationship_id", UUID.randomUUID());
                row.put("employee_id", UUID.randomUUID());
                row.put("work_date", MONTH);
                row.put("minutes_count", 1);
                row.put("day_kind", "WORKDAY");
                row.put("compensation_kind", "PAY");
                row.put("status", "REQUESTED");
                row.put("approval_instance_id", null);
                row.put("legal_entity_id", UUID.randomUUID());
            }
            case "attendance_records" -> {
                row.put("employee_id", UUID.randomUUID());
                row.put("record_kind", "PUNCH");
                row.put("occurred_at", AT);
                row.put("source_system", "SYNTHETIC");
                row.put("external_record_key", UUID.randomUUID().toString());
            }
            case "monthly_confirmations" -> {
                row.put("employee_id", UUID.randomUUID());
                row.put("month_start_date", MONTH);
                row.put("status", "GENERATED");
                row.put("confirmed_by_kind", null);
                row.put("confirmed_at", null);
                row.put("locked_at", null);
            }
            case "monthly_confirmation_lines" -> {
                row.put("monthly_confirmation_id", UUID.randomUUID());
                row.put("employment_relationship_id", UUID.randomUUID());
                row.put("line_kind", "ATTENDANCE_DAYS");
                row.put("leave_type_id", null);
                row.put("is_paid_leave", null);
                row.put("compensation_kind", null);
                row.put("day_kind", null);
                row.put("minutes_count", null);
                row.put("day_hundredths_count", 100);
                row.put("original_month_start_date", null);
            }
            case "leave_types" -> {
                row.put("code", "T_" + UUID.randomUUID().toString().replace("-", "").substring(0, 30).toUpperCase());
                row.put("display_name", "Synthetic leave"); row.put("ledger_unit_kind", "DAY");
                row.put("is_paid", true); row.put("is_quota_counted", true);
            }
            case "leave_rule_versions" -> {
                row.put("leave_type_id", UUID.randomUUID()); row.put("scope_kind", "ALL"); row.put("employer_entity_id", null);
                row.put("version_number", 1); row.put("status", "IN_EFFECT"); row.put("effective_from_date", LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));
                row.put("effective_to_date", null); row.put("request_unit_kind", "DAY"); row.put("is_negative_allowed", false);
                row.put("is_proof_required", false); row.put("rollover_kind", "NONE"); row.put("basis_ref_text", "Synthetic rule");
                row.put("published_by", UUID.randomUUID()); row.put("published_at", AT); row.put("decision_audit_entry_id", UUID.randomUUID());
            }
            case "leave_entitlement_rules" -> {
                row.put("rule_version_id", UUID.randomUUID()); row.put("service_years_from_count", 0);
                row.put("service_years_to_count", null); row.put("granted_qty", 0); row.put("proration_kind", "NONE");
            }
            case "leave_accounts" -> {
                row.put("employment_relationship_id", UUID.randomUUID()); row.put("employee_id", UUID.randomUUID());
                row.put("leave_type_id", UUID.randomUUID()); row.put("rule_version_id", UUID.randomUUID());
                row.put("leave_year_number", 2026); row.put("status", "OPEN"); row.put("sealed_at", null);
            }
            case "leave_ledger_lines" -> {
                row.put("leave_account_id", UUID.randomUUID()); row.put("employment_relationship_id", UUID.randomUUID());
                row.put("leave_type_id", UUID.randomUUID()); row.put("line_kind", "GRANT"); row.put("line_qty", 0);
                row.put("source_kind", "LEAVE_REQUEST"); row.put("source_id", UUID.randomUUID()); row.put("effective_date", MONTH);
            }
            case "leave_grant_basis" -> {
                row.put("leave_ledger_line_id", UUID.randomUUID()); row.put("relationship_start_date", MONTH);
                row.put("prior_service_months_count", 0); row.put("relationship_end_date", null); row.put("unpaid_leave_days_count", 0);
                row.put("employer_entity_id", UUID.randomUUID()); row.put("rule_version_id", UUID.randomUUID()); row.put("leave_type_id", UUID.randomUUID());
            }
            default -> throw new IllegalArgumentException(table);
        }
        return row;
    }

    private static UUID insert(Connection db, String table, Map<String, Object> row) throws SQLException {
        run(db, "insert into " + SCHEMA + table + " (" + String.join(", ", row.keySet()) + ") values ("
                + row.keySet().stream().map(key -> "?").collect(Collectors.joining(", ")) + ")", row.values().toArray());
        return (UUID) row.get("id");
    }

    /** Parent identities are synthetic: cross-context references intentionally need no workforce/identity data. */
    private static Map<String, Object> validRow(Connection db, String table) throws SQLException {
        Map<String, Object> row = row(table);
        if (table.equals("leave_requests")) row.put("leave_type_id", insert(db, "leave_types", row("leave_types")));
        if (table.equals("leave_request_days") || table.equals("leave_request_holds")) {
            row.put("leave_request_id", insert(db, "leave_requests", validRow(db, "leave_requests")));
            if (table.equals("leave_request_holds")) row.put("ledger_line_id", insert(db, "leave_ledger_lines", validRow(db, "leave_ledger_lines")));
        } else if (table.equals("monthly_confirmation_lines")) {
            row.put("monthly_confirmation_id", insert(db, "monthly_confirmations", row("monthly_confirmations")));
        } else if (table.equals("leave_rule_versions")) {
            row.put("leave_type_id", insert(db, "leave_types", row("leave_types")));
            row.put("effective_from_date", LocalDate.parse(strings(db, "select (transaction_timestamp() at time zone 'Asia/Shanghai')::date").getFirst()));
        } else if (table.equals("leave_entitlement_rules") || table.equals("leave_accounts")) {
            var version = validRow(db, "leave_rule_versions");
            row.put("rule_version_id", insert(db, "leave_rule_versions", version));
            if (table.equals("leave_accounts")) row.put("leave_type_id", version.get("leave_type_id"));
        } else if (table.equals("leave_ledger_lines")) {
            var account = validRow(db, "leave_accounts"); row.put("leave_account_id", insert(db, "leave_accounts", account));
            row.put("employment_relationship_id", account.get("employment_relationship_id")); row.put("leave_type_id", account.get("leave_type_id"));
        } else if (table.equals("leave_grant_basis")) {
            var line = validRow(db, "leave_ledger_lines"); row.put("leave_ledger_line_id", insert(db, "leave_ledger_lines", line));
            row.put("leave_type_id", line.get("leave_type_id"));
            row.put("rule_version_id", UUID.fromString(strings(db, "select rule_version_id from organization_leave.leave_accounts where id = '" + line.get("leave_account_id") + "'").getFirst()));
        }
        return row;
    }

    record Check(String table, String suffix, Map<String, Object> changes) {
        String name() {
            return "ck_" + table + "_" + suffix;
        }
    }

    private static Check check(String table, String suffix, Object... pairs) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            changes.put((String) pairs[i], pairs[i + 1]);
        }
        return new Check(table, suffix, changes);
    }

    private static List<Check> checks() {
        return List.of(
                check("leave_types", "code", "code", "x"),
                check("leave_types", "display_name", "display_name", " "),
                check("leave_types", "display_name", "display_name", "X".repeat(61)),
                check("leave_types", "ledger_unit_kind", "ledger_unit_kind", "UNKNOWN"),
                check("leave_rule_versions", "scope_kind", "scope_kind", "UNKNOWN", "employer_entity_id", UUID.randomUUID()),
                check("leave_rule_versions", "scope_matches_entity", "employer_entity_id", UUID.randomUUID()),
                check("leave_rule_versions", "scope_matches_entity", "scope_kind", "EMPLOYER_ENTITY"),
                check("leave_rule_versions", "version_number", "version_number", 0),
                check("leave_rule_versions", "status", "status", "UNKNOWN"),
                check("leave_rule_versions", "effective_range", "effective_to_date", LocalDate.of(2000, 1, 1)),
                check("leave_rule_versions", "request_unit_kind", "request_unit_kind", "UNKNOWN"),
                check("leave_rule_versions", "exempt_threshold_minutes_count", "exempt_threshold_minutes_count", 0),
                check("leave_rule_versions", "rollover_kind", "rollover_kind", "UNKNOWN"),
                check("leave_rule_versions", "rollover_columns", "rollover_kind", "CAPPED"),
                check("leave_rule_versions", "rollover_columns", "rollover_cap_qty", 1),
                check("leave_rule_versions", "rollover_columns", "rollover_kind", "CAPPED", "rollover_cap_qty", 0),
                check("leave_rule_versions", "rollover_columns", "rollover_expiry_month_number", 1),
                check("leave_rule_versions", "rollover_expiry_month_number", "rollover_kind", "UNLIMITED", "rollover_expiry_month_number", 13),
                check("leave_rule_versions", "grant_valid_days_count", "grant_valid_days_count", 0),
                check("leave_rule_versions", "basis_ref_text", "basis_ref_text", " spaced "),
                check("leave_rule_versions", "basis_ref_text", "basis_ref_text", "X".repeat(201)),
                check("leave_rule_versions", "void_columns", "status", "VOIDED"),
                check("leave_rule_versions", "void_columns", "voided_at", AT),
                check("leave_rule_versions", "void_columns", "void_reason_text", "Synthetic"),
                check("leave_rule_versions", "void_columns", "voided_by", UUID.randomUUID()),
                check("leave_rule_versions", "void_columns", "status", "VOIDED", "voided_by", UUID.randomUUID(), "voided_at", AT, "void_reason_text", " "),
                check("leave_entitlement_rules", "service_years_from_count", "service_years_from_count", -1),
                check("leave_entitlement_rules", "service_range", "service_years_to_count", 0),
                check("leave_entitlement_rules", "granted_qty", "granted_qty", -1),
                check("leave_entitlement_rules", "proration_kind", "proration_kind", "UNKNOWN"),
                check("leave_accounts", "leave_year_number", "leave_year_number", 1999),
                check("leave_accounts", "status", "status", "UNKNOWN"),
                check("leave_accounts", "sealed_columns", "sealed_at", AT),
                check("leave_accounts", "sealed_columns", "status", "SEALED"),
                check("leave_accounts", "sealed_columns", "status", "SEALED", "sealed_at", AT, "leave_year_number", null),
                check("leave_ledger_lines", "line_kind", "line_kind", "UNKNOWN"),
                check("leave_ledger_lines", "sign", "line_qty", -1),
                check("leave_ledger_lines", "sign", "line_kind", "ADJUST", "original_leave_year_number", 2026),
                check("leave_ledger_lines", "source_kind", "source_kind", "x"),
                check("leave_ledger_lines", "expiry_kind", "expiry_date", MONTH),
                check("leave_ledger_lines", "expiry_kind", "line_kind", "HOLD", "expiry_date", MONTH.plusDays(1)),
                check("leave_ledger_lines", "consumes_kind", "consumes_line_id", UUID.randomUUID()),
                check("leave_ledger_lines", "reason_text", "line_kind", "ADJUST", "line_qty", 1, "adjusted_by", UUID.randomUUID(), "reason_text", " "),
                check("leave_ledger_lines", "adjust_columns", "adjusted_by", UUID.randomUUID(), "reason_text", "Synthetic"),
                check("leave_ledger_lines", "adjust_columns", "corrects_line_id", UUID.randomUUID()),
                check("leave_ledger_lines", "adjust_columns", "line_kind", "ADJUST", "line_qty", 1),
                check("leave_ledger_lines", "adjust_columns", "line_kind", "ADJUST", "line_qty", 1, "reason_text", "Synthetic"),
                check("leave_ledger_lines", "adjust_columns", "line_kind", "ADJUST", "line_qty", 1, "original_leave_year_number", 2026, "reason_text", "Synthetic"),
                check("leave_ledger_lines", "adjust_columns", "line_kind", "ADJUST", "line_qty", 1, "adjusted_by", UUID.randomUUID()),
                check("leave_grant_basis", "prior_service_months_count", "prior_service_months_count", -1),
                check("leave_grant_basis", "unpaid_leave_days_count", "unpaid_leave_days_count", -1),
                check("leave_requests", "period", "end_at", AT),
                check("leave_requests", "status", "status", "UNKNOWN"),
                check("leave_requests", "in_approval_has_instance", "status", "IN_APPROVAL"),
                check("leave_request_days", "minutes_count", "minutes_count", -1),
                check("leave_request_days", "minutes_count", "minutes_count", 1441),
                check("leave_request_days", "day_hundredths_count", "day_hundredths_count", -1),
                check("leave_request_days", "day_hundredths_count", "day_hundredths_count", 101),
                check("overtime_requests", "minutes_count", "minutes_count", 0),
                check("overtime_requests", "minutes_count", "minutes_count", 1441),
                check("overtime_requests", "day_kind", "day_kind", "UNKNOWN"),
                check("overtime_requests", "compensation_kind", "compensation_kind", "UNKNOWN"),
                check("overtime_requests", "status", "status", "UNKNOWN"),
                check("overtime_requests", "holiday_pay_only", "day_kind", "STATUTORY_HOLIDAY", "compensation_kind", "COMP_TIME"),
                check("overtime_requests", "status_matches_compensation", "status", "COMPENSATED_PAY", "compensation_kind", "COMP_TIME"),
                check("overtime_requests", "status_matches_compensation", "status", "CREDITED_AS_COMP_TIME"),
                check("overtime_requests", "in_approval_has_instance", "status", "IN_APPROVAL"),
                check("attendance_records", "record_kind", "record_kind", "UNKNOWN"),
                check("attendance_records", "source_system", "source_system", "xy"),
                check("attendance_records", "source_system", "source_system", "A"),
                check("attendance_records", "source_system", "source_system", "A".repeat(33)),
                check("attendance_records", "external_record_key", "external_record_key", ""),
                check("attendance_records", "external_record_key", "external_record_key", " spaced "),
                check("attendance_records", "external_record_key", "external_record_key", "X".repeat(101)),
                check("monthly_confirmations", "month_start_date", "month_start_date", MONTH.plusDays(1)),
                check("monthly_confirmations", "status", "status", "UNKNOWN", "confirmed_by_kind", "EMPLOYEE", "confirmed_at", AT),
                check("monthly_confirmations", "confirmed_by_kind", "status", "CONFIRMED", "confirmed_by_kind", "UNKNOWN", "confirmed_at", AT),
                check("monthly_confirmations", "confirmation_columns", "status", "CONFIRMED"),
                check("monthly_confirmations", "confirmation_columns", "confirmed_by_kind", "EMPLOYEE", "confirmed_at", AT),
                check("monthly_confirmations", "confirmation_columns", "confirmed_at", AT),
                check("monthly_confirmations", "lock_columns", "status", "LOCKED", "confirmed_by_kind", "EMPLOYEE", "confirmed_at", AT),
                check("monthly_confirmations", "lock_columns", "locked_at", AT),
                check("monthly_confirmation_lines", "line_kind", "line_kind", "UNKNOWN", "minutes_count", 0, "day_hundredths_count", null),
                check("monthly_confirmation_lines", "compensation_kind", "line_kind", "OVERTIME", "compensation_kind", "UNKNOWN", "day_kind", "WORKDAY", "minutes_count", 1, "day_hundredths_count", null),
                check("monthly_confirmation_lines", "day_kind", "line_kind", "OVERTIME", "compensation_kind", "PAY", "day_kind", "UNKNOWN", "minutes_count", 1, "day_hundredths_count", null),
                check("monthly_confirmation_lines", "minutes_count", "line_kind", "OVERTIME", "compensation_kind", "PAY", "day_kind", "WORKDAY", "minutes_count", -1, "day_hundredths_count", null),
                check("monthly_confirmation_lines", "day_hundredths_count", "day_hundredths_count", -1),
                check("monthly_confirmation_lines", "leave_columns", "leave_type_id", UUID.randomUUID()),
                check("monthly_confirmation_lines", "leave_columns", "is_paid_leave", true),
                check("monthly_confirmation_lines", "leave_columns", "line_kind", "LEAVE", "minutes_count", 1, "day_hundredths_count", null),
                check("monthly_confirmation_lines", "overtime_columns", "compensation_kind", "PAY"),
                check("monthly_confirmation_lines", "overtime_columns", "day_kind", "WORKDAY"),
                check("monthly_confirmation_lines", "overtime_columns", "line_kind", "OVERTIME", "minutes_count", 1, "day_hundredths_count", null),
                check("monthly_confirmation_lines", "quantity_columns", "minutes_count", 1),
                check("monthly_confirmation_lines", "quantity_columns", "day_hundredths_count", null),
                check("monthly_confirmation_lines", "quantity_columns", "line_kind", "LEAVE", "leave_type_id", UUID.randomUUID(), "is_paid_leave", true),
                check("monthly_confirmation_lines", "original_month_start_date", "original_month_start_date", MONTH.plusDays(1)));
    }

    private static final List<String> L1B = List.of("leave_types", "leave_rule_versions", "leave_entitlement_rules", "leave_accounts", "leave_ledger_lines", "leave_grant_basis");
    private static void constraintMode(Connection db) throws SQLException {
        for (String table : L1B) run(db, "alter table " + SCHEMA + table + " disable trigger user");
    }

    @Test
    @DisplayName("#1400 / #1470 every named CHECK refuses a violating insert and accepts a valid insert")
    void everyCheckHasARedAndGreenInsertAndNoneIsLeftUnprobed() throws Exception {
        try (Connection db = connect()) {
            constraintMode(db); // Otherwise BEFORE guards can mask the named CHECK under test. Transaction rolls this back.
            Set<String> tested = new TreeSet<>();
            for (Check probe : checks()) {
                var valid = validRow(db, probe.table());
                var invalid = new LinkedHashMap<>(valid);
                invalid.putAll(probe.changes());
                refused(db, "23514", probe.name(), () -> insert(db, probe.table(), invalid));
                insert(db, probe.table(), valid);
                tested.add(probe.name());
            }
            assertThat(strings(db, "select conname from pg_constraint where connamespace = 'organization_leave'::regnamespace and contype = 'c'"))
                    .containsExactlyInAnyOrderElementsOf(tested);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 all primary keys and NOT NULL columns reject a violating insert")
    void primaryKeysAndRequiredColumnsAreEnforced() throws Exception {
        try (Connection db = connect()) {
            constraintMode(db);
            for (String table : TABLES) {
                var valid = validRow(db, table);
                insert(db, table, valid);
                refused(db, "23505", "pk_" + table, () -> insert(db, table, valid));
                for (String column : strings(db, "select column_name from information_schema.columns where table_schema = 'organization_leave' and table_name = '"
                        + table + "' and is_nullable = 'NO'")) {
                    var missing = new LinkedHashMap<>(valid);
                    missing.put("id", UUID.randomUUID());
                    missing.put(column, null);
                    refused(db, "23502", column, () -> insert(db, table, missing));
                }
            }
            db.rollback();
        }
    }

    private static void duplicate(Connection db, String table, String rule, Map<String, Object> first, Map<String, Object> next) throws SQLException {
        insert(db, table, first);
        refused(db, "23505", rule, () -> insert(db, table, next));
    }

    @Test
    @DisplayName("#1400 every unique key including partial approval indexes and NULLS NOT DISTINCT has a red and green pair")
    void businessKeysAndPartialUniquesAreEnforced() throws Exception {
        try (Connection db = connect()) {
            for (String table : List.of("leave_requests", "overtime_requests")) {
                var first = validRow(db, table);
                first.put("approval_instance_id", UUID.randomUUID());
                var same = validRow(db, table);
                same.put("approval_instance_id", first.get("approval_instance_id"));
                duplicate(db, table, "uk_" + table + "_approval_instance_id", first, same);
                same.put("approval_instance_id", UUID.randomUUID());
                insert(db, table, same);
                insert(db, table, validRow(db, table));
                insert(db, table, validRow(db, table)); // multiple NULL approval ids are allowed
            }
            for (String table : List.of("leave_request_days", "leave_request_holds", "attendance_records", "monthly_confirmations", "monthly_confirmation_lines")) {
                var first = validRow(db, table);
                var same = new LinkedHashMap<>(first);
                same.put("id", UUID.randomUUID());
                String key = switch (table) {
                    case "leave_request_days" -> "request_work_date";
                    case "leave_request_holds" -> "ledger_line_id"; // overlaps the request/line key: probe that one in isolation below
                    case "attendance_records" -> "source_external_record_key";
                    case "monthly_confirmations" -> "employee_month";
                    case "monthly_confirmation_lines" -> "identity";
                    default -> throw new IllegalArgumentException(table);
                };
                if (table.equals("leave_request_holds")) {
                    // PostgreSQL checks the request/line key first for a duplicate pair.
                    same.put("leave_request_id", insert(db, "leave_requests", validRow(db, "leave_requests")));
                }
                duplicate(db, table, "uk_" + table + "_" + key, first, same);
                switch (table) {
                    case "leave_request_days" -> same.put("work_date", MONTH.plusDays(1));
                    case "leave_request_holds" -> same.put("ledger_line_id", insert(db, "leave_ledger_lines", validRow(db, "leave_ledger_lines")));
                    case "attendance_records" -> same.put("external_record_key", "another-key");
                    case "monthly_confirmations" -> same.put("month_start_date", MONTH.plusMonths(1));
                    case "monthly_confirmation_lines" -> same.put("original_month_start_date", MONTH.minusMonths(1));
                    default -> throw new IllegalArgumentException(table);
                }
                insert(db, table, same);
            }
            assertThat(strings(db, "select indexname from pg_indexes where schemaname = 'organization_leave' and indexname like 'uk_%' and tablename not in ('leave_types', 'leave_rule_versions', 'leave_entitlement_rules', 'leave_accounts', 'leave_ledger_lines', 'leave_grant_basis')"))
                    .containsExactlyInAnyOrder("uk_leave_requests_approval_instance_id", "uk_overtime_requests_approval_instance_id",
                            "uk_leave_request_days_request_work_date", "uk_leave_request_holds_request_line", "uk_leave_request_holds_ledger_line_id",
                            "uk_attendance_records_source_external_record_key", "uk_monthly_confirmations_employee_month", "uk_monthly_confirmation_lines_identity");
            // The stronger ledger-line unique also prevents a duplicate pair. Drop only it inside this rolled-back probe.
            run(db, "alter table organization_leave.leave_request_holds drop constraint uk_leave_request_holds_ledger_line_id");
            var hold = validRow(db, "leave_request_holds");
            var sameHold = new LinkedHashMap<>(hold);
            sameHold.put("id", UUID.randomUUID());
            duplicate(db, "leave_request_holds", "uk_leave_request_holds_request_line", hold, sameHold);
            sameHold.put("ledger_line_id", insert(db, "leave_ledger_lines", validRow(db, "leave_ledger_lines")));
            insert(db, "leave_request_holds", sameHold);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 L1a parent keys still refuse missing parents and parent deletion")
    void foreignKeysRestrictOrphansAndDeletionWithoutCrossContextKeys() throws Exception {
        try (Connection db = connect()) {
            List<String> expected = new ArrayList<>();
            for (String table : List.of("leave_request_days", "leave_request_holds", "monthly_confirmation_lines")) {
                String column = table.equals("monthly_confirmation_lines") ? "monthly_confirmation_id" : "leave_request_id";
                String parent = table.equals("monthly_confirmation_lines") ? "monthly_confirmations" : "leave_requests";
                String key = "fk_" + table + "_" + column;
                expected.add(key);
                var invalid = validRow(db, table);
                invalid.put(column, UUID.randomUUID());
                refused(db, "23503", key, () -> insert(db, table, invalid));
                var valid = validRow(db, table);
                insert(db, table, valid);
                refused(db, "23001", key, () -> run(db, "delete from " + SCHEMA + parent + " where id = ?", valid.get(column)));
                UUID unreferenced = insert(db, parent, validRow(db, parent));
                run(db, "delete from " + SCHEMA + parent + " where id = ?", unreferenced);
            }
            assertThat(strings(db, "select conname from pg_constraint where connamespace = 'organization_leave'::regnamespace and contype = 'f' and conrelid in ('organization_leave.leave_request_days'::regclass, 'organization_leave.leave_request_holds'::regclass, 'organization_leave.monthly_confirmation_lines'::regclass) and conname not in ('fk_leave_request_holds_ledger_line_id', 'fk_monthly_confirmation_lines_leave_type_id')"))
                    .containsExactlyInAnyOrderElementsOf(expected);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 live leave periods exclude overlap per relationship; adjacent, inactive and other-relationship rows are valid")
    void liveLeaveOverlapIsExcludedOnInsertAndActivation() throws Exception {
        try (Connection db = connect()) {
            for (String status : List.of("SUBMITTED", "IN_APPROVAL", "APPROVED")) {
                var first = validRow(db, "leave_requests");
                first.put("status", status);
                first.put("approval_instance_id", UUID.randomUUID());
                insert(db, "leave_requests", first);
                var next = new LinkedHashMap<>(first);
                next.put("id", UUID.randomUUID());
                next.put("approval_instance_id", UUID.randomUUID());
                refused(db, "23P01", "ex_leave_requests_live_period_overlap", () -> insert(db, "leave_requests", next));
                next.put("start_at", AT.plusHours(1));
                next.put("end_at", AT.plusHours(2));
                insert(db, "leave_requests", next);
                for (String inactive : List.of("DRAFT", "REJECTED", "WITHDRAWN", "RECALLED", "CANCELLED")) {
                    var draft = new LinkedHashMap<>(first);
                    draft.put("id", UUID.randomUUID());
                    draft.put("approval_instance_id", null);
                    draft.put("status", inactive);
                    UUID id = insert(db, "leave_requests", draft);
                    refused(db, "23P01", "ex_leave_requests_live_period_overlap", () ->
                            run(db, "update organization_leave.leave_requests set status = 'SUBMITTED' where id = ?", id));
                }
                var other = new LinkedHashMap<>(first);
                other.put("id", UUID.randomUUID());
                other.put("employment_relationship_id", UUID.randomUUID());
                other.put("approval_instance_id", UUID.randomUUID());
                insert(db, "leave_requests", other);
            }
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 RULE-519 attendance and holds allow inserts but reject every update, delete and truncate")
    void appendOnlyFactsCannotBeRewrittenOrCleared() throws Exception {
        try (Connection db = connect()) {
            for (String table : List.of("attendance_records", "leave_request_holds")) {
                UUID id = insert(db, table, validRow(db, table));
                refused(db, "23001", "append-only", () -> run(db, "update " + SCHEMA + table + " set id = id where id = ?", id));
                refused(db, "23001", "append-only", () -> run(db, "delete from " + SCHEMA + table + " where id = ?", id));
                refused(db, "23001", "append-only", () -> run(db, "truncate table " + SCHEMA + table));
                assertThat(strings(db, "select count(*) from " + SCHEMA + table)).containsExactly("1");
            }
            db.rollback();
        }
    }

    private static void lock(Connection db, UUID id) throws SQLException {
        run(db, """
                update organization_leave.monthly_confirmations
                   set status = 'LOCKED', confirmed_by_kind = 'EMPLOYEE', confirmed_at = ?, locked_at = ? where id = ?
                """, AT, AT, id);
    }

    @Test
    @DisplayName("#1400 INV-105 RULE-518 locked parents and lines reject changes, including moving a line into or out of a locked parent")
    void lockedConfirmationsAndTheirLinesAreImmutable() throws Exception {
        try (Connection db = connect()) {
            var line = validRow(db, "monthly_confirmation_lines");
            UUID parent = (UUID) line.get("monthly_confirmation_id");
            UUID id = insert(db, "monthly_confirmation_lines", line);
            run(db, "update organization_leave.monthly_confirmation_lines set day_hundredths_count = 200 where id = ?", id);
            var removable = validRow(db, "monthly_confirmation_lines");
            UUID removableId = insert(db, "monthly_confirmation_lines", removable);
            run(db, "delete from organization_leave.monthly_confirmation_lines where id = ?", removableId);
            lock(db, parent);
            refused(db, "23001", "LOCKED", () -> run(db, "update organization_leave.monthly_confirmations set status = status where id = ?", parent));
            refused(db, "23001", "LOCKED", () -> run(db, "update organization_leave.monthly_confirmations set status = 'CONFIRMED', locked_at = null where id = ?", parent));
            refused(db, "23001", "LOCKED", () -> run(db, "delete from organization_leave.monthly_confirmations where id = ?", parent));
            refused(db, "23001", "LOCKED", () -> run(db, "update organization_leave.monthly_confirmation_lines set minutes_count = minutes_count where id = ?", id));
            refused(db, "23001", "LOCKED", () -> run(db, "delete from organization_leave.monthly_confirmation_lines where id = ?", id));
            var added = new LinkedHashMap<>(line);
            added.put("id", UUID.randomUUID());
            added.put("employment_relationship_id", UUID.randomUUID());
            refused(db, "23001", "LOCKED", () -> insert(db, "monthly_confirmation_lines", added));
            UUID open = insert(db, "monthly_confirmations", row("monthly_confirmations"));
            refused(db, "23001", "LOCKED", () -> run(db, "update organization_leave.monthly_confirmation_lines set monthly_confirmation_id = ? where id = ?", open, id));
            var openLine = validRow(db, "monthly_confirmation_lines");
            UUID openId = insert(db, "monthly_confirmation_lines", openLine);
            refused(db, "23001", "LOCKED", () -> run(db, "update organization_leave.monthly_confirmation_lines set monthly_confirmation_id = ? where id = ?", parent, openId));
            run(db, "update organization_leave.monthly_confirmation_lines set monthly_confirmation_id = ? where id = ?", open, openId);
            UUID confirmed = insert(db, "monthly_confirmations", row("monthly_confirmations"));
            run(db, "update organization_leave.monthly_confirmations set status = 'CONFIRMED', confirmed_by_kind = 'DEFAULT', confirmed_at = ? where id = ?", AT, confirmed);
            run(db, "delete from organization_leave.monthly_confirmations where id = ?", confirmed);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 all signed enum values and quantity/text boundaries accept valid inserts")
    void enumValuesAndBoundariesHavePositiveExamples() throws Exception {
        try (Connection db = connect()) {
            for (String status : List.of("REQUESTED", "IN_APPROVAL", "APPROVED", "COMPENSATED_PAY", "CREDITED_AS_COMP_TIME", "REJECTED", "WITHDRAWN")) {
                var overtime = row("overtime_requests");
                overtime.put("status", status);
                overtime.put("approval_instance_id", status.equals("IN_APPROVAL") ? UUID.randomUUID() : null);
                overtime.put("compensation_kind", status.equals("CREDITED_AS_COMP_TIME") ? "COMP_TIME" : "PAY");
                overtime.put("minutes_count", 1440);
                insert(db, "overtime_requests", overtime);
            }
            for (String day : List.of("WORKDAY", "REST_DAY", "STATUTORY_HOLIDAY")) {
                var overtime = row("overtime_requests");
                overtime.put("day_kind", day);
                insert(db, "overtime_requests", overtime);
                for (String compensation : List.of("PAY", "COMP_TIME")) {
                    var line = validRow(db, "monthly_confirmation_lines");
                    line.put("line_kind", "OVERTIME");
                    line.put("minutes_count", 0);
                    line.put("day_hundredths_count", null);
                    line.put("day_kind", day);
                    line.put("compensation_kind", compensation);
                    insert(db, "monthly_confirmation_lines", line);
                }
            }
            for (boolean paid : List.of(true, false)) {
                var line = validRow(db, "monthly_confirmation_lines");
                line.put("line_kind", "LEAVE");
                line.put("minutes_count", 0);
                line.put("day_hundredths_count", null);
                line.put("leave_type_id", insert(db, "leave_types", row("leave_types")));
                line.put("is_paid_leave", paid);
                insert(db, "monthly_confirmation_lines", line);
            }
            for (String kind : List.of("PUNCH", "OUTFIELD", "MAKE_UP")) {
                var attendance = row("attendance_records");
                attendance.put("record_kind", kind);
                attendance.put("source_system", "A".repeat(32));
                attendance.put("external_record_key", kind + "X".repeat(100 - kind.length()));
                insert(db, "attendance_records", attendance);
            }
            var oneCharacter = row("attendance_records");
            oneCharacter.put("external_record_key", "X");
            insert(db, "attendance_records", oneCharacter);
            var day = validRow(db, "leave_request_days");
            day.put("minutes_count", 1440);
            day.put("day_hundredths_count", 100);
            insert(db, "leave_request_days", day);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 monthly line identity distinguishes each fact dimension and treats absent dimensions as equal")
    void lineIdentityUsesEverySignedDimension() throws Exception {
        try (Connection db = connect()) {
            var base = validRow(db, "monthly_confirmation_lines");
            base.put("line_kind", "OVERTIME");
            base.put("minutes_count", 60);
            base.put("day_hundredths_count", null);
            base.put("compensation_kind", "PAY");
            base.put("day_kind", "WORKDAY");
            insert(db, "monthly_confirmation_lines", base);
            for (var dimension : Map.of("employment_relationship_id", UUID.randomUUID(), "compensation_kind", "COMP_TIME",
                    "day_kind", "REST_DAY", "original_month_start_date", MONTH.minusMonths(1)).entrySet()) {
                var distinct = new LinkedHashMap<>(base);
                distinct.put("id", UUID.randomUUID());
                distinct.put(dimension.getKey(), dimension.getValue());
                insert(db, "monthly_confirmation_lines", distinct);
                distinct.put("id", UUID.randomUUID());
                refused(db, "23505", "uk_monthly_confirmation_lines_identity", () -> insert(db, "monthly_confirmation_lines", distinct));
            }
            var leave = new LinkedHashMap<>(base);
            leave.put("id", UUID.randomUUID());
            leave.put("line_kind", "LEAVE");
            leave.put("compensation_kind", null);
            leave.put("day_kind", null);
            leave.put("leave_type_id", insert(db, "leave_types", row("leave_types")));
            leave.put("is_paid_leave", true);
            insert(db, "monthly_confirmation_lines", leave);
            leave.put("id", UUID.randomUUID());
            leave.put("leave_type_id", insert(db, "leave_types", row("leave_types")));
            insert(db, "monthly_confirmation_lines", leave);
            leave.put("id", UUID.randomUUID());
            refused(db, "23505", "uk_monthly_confirmation_lines_identity", () -> insert(db, "monthly_confirmation_lines", leave));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1400 line edits serialize with parent locking in either transaction order")
    void concurrentLineEditsCannotPassAParentLock() throws Exception {
        UUID parent;
        UUID lineId;
        try (Connection setup = connect()) {
            var line = validRow(setup, "monthly_confirmation_lines");
            parent = (UUID) line.get("monthly_confirmation_id");
            lineId = insert(setup, "monthly_confirmation_lines", line);
            setup.commit();
        }
        try (Connection locker = connect(); Connection writer = connect()) {
            run(locker, "set local lock_timeout = '200ms'");
            run(writer, "set local lock_timeout = '200ms'");
            run(writer, "update organization_leave.monthly_confirmation_lines set day_hundredths_count = 200 where id = ?", lineId);
            refused(locker, "55P03", "lock timeout", () -> lock(locker, parent));
            writer.rollback();
            run(writer, "set local lock_timeout = '200ms'");
            lock(locker, parent);
            refused(writer, "55P03", "lock timeout", () ->
                    run(writer, "update organization_leave.monthly_confirmation_lines set day_hundredths_count = 300 where id = ?", lineId));
            locker.commit();
            refused(writer, "23001", "LOCKED", () ->
                    run(writer, "update organization_leave.monthly_confirmation_lines set day_hundredths_count = 300 where id = ?", lineId));
            writer.rollback();
        } finally {
            // Only this fixture is committed to make the two-connection probe possible.
            try (Connection cleanup = connect()) {
                run(cleanup, "alter table organization_leave.monthly_confirmation_lines disable trigger guard_locked_monthly_confirmation");
                run(cleanup, "alter table organization_leave.monthly_confirmations disable trigger guard_locked_monthly_confirmation");
                run(cleanup, "delete from organization_leave.monthly_confirmation_lines where id = ?", lineId);
                run(cleanup, "delete from organization_leave.monthly_confirmations where id = ?", parent);
                run(cleanup, "alter table organization_leave.monthly_confirmation_lines enable trigger guard_locked_monthly_confirmation");
                run(cleanup, "alter table organization_leave.monthly_confirmations enable trigger guard_locked_monthly_confirmation");
                cleanup.commit();
            }
        }
    }

    @Test
    @DisplayName("#1400 signed schema has exactly thirteen tables; nullable comments, no carved-out or biometric/location fields, three completed deferred UUID keys")
    void schemaAndCommentsMatchTheSignedSlice() throws Exception {
        try (Connection db = connect()) {
            assertThat(strings(db, "select conname from pg_constraint where connamespace = 'organization_leave'::regnamespace and octet_length(conname) > 63")).isEmpty();
            assertThat(strings(db, "select proname from pg_proc where pronamespace = 'organization_leave'::regnamespace and not coalesce(proconfig @> array['search_path=pg_catalog, pg_temp'], false)")).isEmpty();
            assertThat(strings(db, "select table_name from information_schema.tables where table_schema = 'organization_leave' and table_type = 'BASE TABLE'"))
                    .containsExactlyInAnyOrderElementsOf(TABLES);
            assertThat(strings(db, """
                    select c.relname || '.' || a.attname from pg_attribute a
                    join pg_class c on c.oid = a.attrelid join pg_namespace n on n.oid = c.relnamespace
                    where n.nspname = 'organization_leave' and c.relkind = 'r' and a.attnum > 0 and not a.attisdropped
                    and (col_description(c.oid, a.attnum) is null or (not a.attnotnull and col_description(c.oid, a.attnum) not like '%为空表示%'))
                    """)).isEmpty();
            assertThat(strings(db, """
                    select relname from pg_class where relnamespace = 'organization_leave'::regnamespace and relkind = 'r'
                    and coalesce(obj_description(oid), '') not like '%粒度%'
                    """)).isEmpty();
            assertThat(strings(db, "select column_name from information_schema.columns where table_schema = 'organization_leave'"))
                    .doesNotContain("proof_file_id", "latitude", "longitude", "location", "biometric", "face_image");
            assertThat(strings(db, """
                    select table_name || '.' || column_name from information_schema.columns where table_schema = 'organization_leave'
                    and data_type = 'uuid' and ((table_name in ('leave_requests', 'monthly_confirmation_lines') and column_name = 'leave_type_id')
                    or (table_name = 'leave_request_holds' and column_name = 'ledger_line_id'))
                    """)).hasSize(3);
            db.rollback();
        }
    }

    private static Map<String, Object> copy(Map<String, Object> source, Object... pairs) {
        var next = new LinkedHashMap<>(source); next.put("id", UUID.randomUUID());
        for (int i = 0; i < pairs.length; i += 2) next.put((String) pairs[i], pairs[i + 1]);
        return next;
    }
    private static Map<String, Object> line(Map<String, Object> account, String kind, Object lot) {
        var line = row("leave_ledger_lines"); line.put("leave_account_id", account.get("id"));
        line.put("employment_relationship_id", account.get("employment_relationship_id")); line.put("leave_type_id", account.get("leave_type_id"));
        line.put("line_kind", kind); line.put("consumes_line_id", lot);
        if (kind.equals("ADJUST")) { line.put("line_qty", -1); line.put("original_leave_year_number", 2026); }
        return line;
    }
    private static void voidVersion(Connection db, Object id) throws SQLException {
        run(db, "update organization_leave.leave_rule_versions set status = 'VOIDED', voided_by = ?, voided_at = ?, void_reason_text = 'Synthetic correction' where id = ?", UUID.randomUUID(), AT, id);
    }
    private static void sealAccount(Connection db, Object id) throws SQLException {
        run(db, "update organization_leave.leave_accounts set status = 'SEALED', sealed_at = ?, updated_at = ? where id = ?", AT, AT.plusSeconds(1), id);
    }

    @Test
    void versionWritesOnlyAppendCloseAndReopenWithinTheirOwnTypeAndScope() throws Exception {
        try (Connection db = connect()) {
            for (Object entity : new Object[] {null, UUID.randomUUID()}) {
                var first = validRow(db, "leave_rule_versions"); first.put("employer_entity_id", entity);
                first.put("scope_kind", entity == null ? "ALL" : "EMPLOYER_ENTITY");
                LocalDate today = (LocalDate) first.get("effective_from_date");
                refused(db, "23514", "tg_leave_rule_versions_insert", () -> insert(db, "leave_rule_versions", copy(first, "effective_from_date", today.minusDays(1))));
                refused(db, "23514", "tg_leave_rule_versions_insert", () -> insert(db, "leave_rule_versions", copy(first, "version_number", 2)));
                refused(db, "23514", "tg_leave_rule_versions_insert", () -> insert(db, "leave_rule_versions", copy(first, "effective_to_date", today.plusDays(1))));
                refused(db, "23514", "tg_leave_rule_versions_insert", () -> insert(db, "leave_rule_versions", copy(first, "status", "VOIDED")));
                insert(db, "leave_rule_versions", first);
                refused(db, "23514", "tg_leave_rule_versions_change", () -> voidVersion(db, first.get("id")));
                var second = copy(first, "version_number", 2, "effective_from_date", today.plusDays(1)); insert(db, "leave_rule_versions", second);
                assertThat(strings(db, "select effective_to_date from organization_leave.leave_rule_versions where id = '" + first.get("id") + "'")).containsExactly(today.plusDays(1).toString());
                refused(db, "23514", "tg_leave_rule_versions_insert", () -> insert(db, "leave_rule_versions", copy(second, "version_number", 3)));
                for (String assignment : List.of("effective_to_date = null", "effective_to_date = effective_from_date + 2", "basis_ref_text = 'Changed'", "updated_at = updated_at + interval '1 second'")) {
                    refused(db, "23001", "tg_leave_rule_versions_change", () -> run(db, "update organization_leave.leave_rule_versions set " + assignment + " where id = ?", first.get("id")));
                }
                var third = copy(second, "version_number", 3, "effective_from_date", today.plusDays(2)); insert(db, "leave_rule_versions", third);
                refused(db, "23514", "tg_leave_rule_versions_change", () -> voidVersion(db, second.get("id")));
                voidVersion(db, third.get("id")); voidVersion(db, second.get("id"));
                assertThat(strings(db, "select effective_to_date from organization_leave.leave_rule_versions where id = '" + first.get("id") + "'")).containsExactly((String) null);
                refused(db, "23001", "tg_leave_rule_versions_change", () -> voidVersion(db, second.get("id")));
                insert(db, "leave_rule_versions", copy(second, "version_number", 4)); // voids keep their numbers
                var future = validRow(db, "leave_rule_versions"); future.put("effective_from_date", today.plusDays(5));
                insert(db, "leave_rule_versions", future); voidVersion(db, future.get("id"));
                refused(db, "23514", "tg_leave_rule_versions_insert", () -> insert(db, "leave_rule_versions", copy(future, "version_number", 2, "effective_from_date", today.minusDays(1))));
                insert(db, "leave_rule_versions", copy(future, "version_number", 2, "effective_from_date", today));
            }
            db.rollback();
        }
    }

    @Test
    void exclusionsAndVersionBusinessKeysHaveIndependentRedAndGreenRows() throws Exception {
        try (Connection db = connect()) {
            for (Object entity : new Object[] {null, UUID.randomUUID()}) {
                var first = validRow(db, "leave_rule_versions"); first.put("employer_entity_id", entity);
                first.put("scope_kind", entity == null ? "ALL" : "EMPLOYER_ENTITY");
                LocalDate from = (LocalDate) first.get("effective_from_date");
                run(db, "alter table organization_leave.leave_rule_versions disable trigger user");
                first.put("effective_to_date", from.plusDays(10)); insert(db, "leave_rule_versions", first);
                var overlap = copy(first, "version_number", 2, "effective_from_date", from.plusDays(1), "effective_to_date", null);
                refused(db, "23P01", "ex_leave_rule_versions_overlap_" + (entity == null ? "all" : "entity"), () -> insert(db, "leave_rule_versions", overlap));
                var adjacent = copy(overlap, "effective_from_date", from.plusDays(10)); insert(db, "leave_rule_versions", adjacent);
                var duplicate = copy(adjacent, "effective_from_date", from.plusDays(20), "status", "VOIDED", "voided_by", UUID.randomUUID(), "voided_at", AT, "void_reason_text", "Synthetic");
                refused(db, "23505", "uk_leave_rule_versions_" + (entity == null ? "all" : "entity") + "_version_number", () -> insert(db, "leave_rule_versions", duplicate));
                duplicate.put("version_number", 3); insert(db, "leave_rule_versions", duplicate);
                run(db, "alter table organization_leave.leave_rule_versions enable trigger user");
            }
            var band = validRow(db, "leave_entitlement_rules"); band.put("service_years_to_count", 10); insert(db, "leave_entitlement_rules", band);
            refused(db, "23P01", "ex_leave_entitlement_rules_service_overlap", () -> insert(db, "leave_entitlement_rules", copy(band, "service_years_from_count", 5, "service_years_to_count", 20)));
            refused(db, "23505", "uk_leave_entitlement_rules_version_from", () -> insert(db, "leave_entitlement_rules", copy(band)));
            insert(db, "leave_entitlement_rules", copy(band, "service_years_from_count", 10, "service_years_to_count", null, "proration_kind", "CALENDAR_DAYS_FLOOR_DAY"));
            db.rollback();
        }
    }

    @Test
    void accountSealAndLedgerLotRulesHaveRedAndGreenRows() throws Exception {
        try (Connection db = connect()) {
            refused(db, "23503", "fk_leave_ledger_lines_account_relationship_type", () -> insert(db, "leave_ledger_lines", row("leave_ledger_lines")));
            refused(db, "23503", "fk_leave_entitlement_rules_rule_version_id", () -> insert(db, "leave_entitlement_rules", row("leave_entitlement_rules")));
            refused(db, "23503", "fk_leave_grant_basis_line_type", () -> insert(db, "leave_grant_basis", row("leave_grant_basis")));
            var year = validRow(db, "leave_accounts"); insert(db, "leave_accounts", year);
            var rolling = copy(year, "leave_year_number", null); insert(db, "leave_accounts", rolling);
            for (var account : List.of(year, rolling)) {
                refused(db, "23505", "uk_leave_accounts_relationship_type_year", () -> insert(db, "leave_accounts", copy(account)));
                refused(db, "23001", "tg_leave_accounts_change", () -> run(db, "update organization_leave.leave_accounts set employee_id = ? where id = ?", UUID.randomUUID(), account.get("id")));
                var grant = line(account, "GRANT", null); insert(db, "leave_ledger_lines", grant);
                for (String kind : List.of("HOLD", "RELEASE", "USE", "REVERSE_USE", "EXPIRE", "ADJUST")) {
                    var drawn = line(account, kind, grant.get("id")); var noLot = copy(drawn, "consumes_line_id", null);
                    refused(db, "23514", "tg_leave_ledger_lines_guard", () -> insert(db, "leave_ledger_lines", account == year ? drawn : noLot));
                    insert(db, "leave_ledger_lines", account == year ? noLot : drawn);
                }
                refused(db, "23514", "tg_leave_ledger_lines_guard", () -> insert(db, "leave_ledger_lines", copy(grant, "updated_at", AT.plusSeconds(1))));
            }
            var carry = line(rolling, "ROLLOVER_IN", null); carry.put("expiry_date", MONTH.plusDays(1)); insert(db, "leave_ledger_lines", carry);
            refused(db, "23503", "fk_leave_ledger_lines_consumes_line_id", () -> insert(db, "leave_ledger_lines", line(rolling, "USE", UUID.randomUUID())));
            var hold = line(rolling, "HOLD", carry.get("id")); insert(db, "leave_ledger_lines", hold);
            refused(db, "23514", "tg_leave_ledger_lines_guard", () -> insert(db, "leave_ledger_lines", line(rolling, "USE", hold.get("id"))));
            var other = validRow(db, "leave_ledger_lines"); insert(db, "leave_ledger_lines", other);
            refused(db, "23514", "tg_leave_ledger_lines_guard", () -> insert(db, "leave_ledger_lines", line(rolling, "USE", other.get("id"))));
            refused(db, "23001", "tg_leave_accounts_change", () -> insert(db, "leave_accounts", copy(year, "status", "SEALED", "sealed_at", AT)));
            refused(db, "23514", "ck_leave_accounts_sealed_columns", () -> sealAccount(db, rolling.get("id")));
            sealAccount(db, year.get("id"));
            for (String assignment : List.of("status = 'OPEN', sealed_at = null", "updated_at = updated_at", "sealed_at = sealed_at"))
                refused(db, "23001", "tg_leave_accounts_change", () -> run(db, "update organization_leave.leave_accounts set " + assignment + " where id = ?", year.get("id")));
            refused(db, "23001", "tg_leave_ledger_lines_guard", () -> insert(db, "leave_ledger_lines", line(year, "GRANT", null)));
            db.rollback();
        }
    }

    @Test
    void sourceAndEventKeysAndAdjustmentAlternativesAreEnforced() throws Exception {
        try (Connection db = connect()) {
            for (String carry : List.of("NONE", "CAPPED", "UNLIMITED")) {
                var version = validRow(db, "leave_rule_versions"); version.put("rollover_kind", carry);
                if (carry.equals("CAPPED")) version.put("rollover_cap_qty", 1);
                if (!carry.equals("NONE")) version.put("rollover_expiry_month_number", 12);
                version.put("exempt_threshold_minutes_count", 1); version.put("grant_valid_days_count", 1);
                for (String unit : List.of("MINUTE", "HALF_DAY", "DAY")) {
                    var scoped = copy(version, "employer_entity_id", UUID.randomUUID(), "scope_kind", "EMPLOYER_ENTITY", "request_unit_kind", unit);
                    insert(db, "leave_rule_versions", scoped);
                }
            }
            var type = validRow(db, "leave_types"); type.put("ledger_unit_kind", "MINUTE"); insert(db, "leave_types", type);
            refused(db, "23505", "uk_leave_types_code", () -> insert(db, "leave_types", copy(type)));
            insert(db, "leave_types", row("leave_types"));
            for (boolean rolling : List.of(false, true)) {
                var account = validRow(db, "leave_accounts"); if (rolling) account.put("leave_year_number", null); insert(db, "leave_accounts", account);
                var grant = line(account, "GRANT", null); insert(db, "leave_ledger_lines", grant);
                var source = line(account, "USE", rolling ? grant.get("id") : null); insert(db, "leave_ledger_lines", source);
                refused(db, "23505", "uk_leave_ledger_lines_source_key", () -> insert(db, "leave_ledger_lines", copy(source)));
                insert(db, "leave_ledger_lines", copy(source, "source_id", UUID.randomUUID()));
                insert(db, "leave_ledger_lines", copy(source, "source_kind", "HR_ADJUSTMENT"));
                insert(db, "leave_ledger_lines", copy(source, "line_kind", "HOLD"));
                if (rolling) {
                    var anotherLot = copy(grant, "source_id", UUID.randomUUID()); insert(db, "leave_ledger_lines", anotherLot);
                    insert(db, "leave_ledger_lines", copy(source, "consumes_line_id", anotherLot.get("id")));
                }
                var event = line(account, "GRANT", null); event.put("original_leave_year_number", 2026); insert(db, "leave_ledger_lines", event);
                var nextYear = copy(account, "leave_year_number", 2027); insert(db, "leave_accounts", nextYear);
                var replay = copy(event, "leave_account_id", nextYear.get("id"));
                refused(db, "23505", "uk_leave_ledger_lines_event_key", () -> insert(db, "leave_ledger_lines", replay));
                replay.put("original_leave_year_number", 2027); insert(db, "leave_ledger_lines", replay);
                for (int mode = 0; mode < 4; mode++) {
                    var adjust = line(account, "ADJUST", rolling ? grant.get("id") : null); adjust.remove("original_leave_year_number");
                    if (mode == 0 || mode == 3) { adjust.put("adjusted_by", UUID.randomUUID()); adjust.put("reason_text", "Synthetic manual correction"); }
                    if (mode == 1 || mode == 3) adjust.put("corrects_line_id", grant.get("id"));
                    if (mode == 2 || mode == 3) adjust.put("original_leave_year_number", 2026);
                    insert(db, "leave_ledger_lines", adjust);
                }
            }
            db.rollback();
        }
    }

    @Test
    void allSixTablesGuardUpdatesDeletesAndTruncates() throws Exception {
        try (Connection db = connect()) {
            for (String table : L1B) {
                var value = validRow(db, table); insert(db, table, value);
                String trigger = "tg_" + table + (table.equals("leave_ledger_lines") ? "_guard" : "_change");
                if (table.equals("leave_types")) {
                    run(db, "update organization_leave.leave_types set display_name = 'Corrected label' where id = ?", value.get("id"));
                    refused(db, "23001", trigger, () -> run(db, "update organization_leave.leave_types set code = 'CHANGED' where id = ?", value.get("id")));
                } else refused(db, "23001", trigger, () -> run(db, "update " + SCHEMA + table + " set id = id where id = ?", value.get("id")));
                refused(db, "23001", trigger, () -> run(db, "delete from " + SCHEMA + table + " where id = ?", value.get("id")));
                var point = db.setSavepoint();
                for (String other : TABLES) if (!other.equals(table)) run(db, "alter table " + SCHEMA + other + " disable trigger user");
                refused(db, "23001", "tg_" + table + "_truncate", () -> run(db, "truncate table " + SCHEMA + table + " cascade"));
                db.rollback(point); db.releaseSavepoint(point);
            }
            db.rollback();
        }
    }

    record Key(String table, String name, List<String> columns) {}
    @Test
    void deferredAndCompositeForeignKeysRejectMissingAndMismatchedIdentities() throws Exception {
        List<Key> keys = List.of(new Key("leave_requests", "fk_leave_requests_leave_type_id", List.of("leave_type_id")),
                new Key("monthly_confirmation_lines", "fk_monthly_confirmation_lines_leave_type_id", List.of("leave_type_id")),
                new Key("leave_request_holds", "fk_leave_request_holds_ledger_line_id", List.of("ledger_line_id")),
                new Key("leave_rule_versions", "fk_leave_rule_versions_leave_type_id", List.of("leave_type_id")),
                new Key("leave_entitlement_rules", "fk_leave_entitlement_rules_rule_version_id", List.of("rule_version_id")),
                new Key("leave_accounts", "fk_leave_accounts_leave_type_id", List.of("leave_type_id")),
                new Key("leave_accounts", "fk_leave_accounts_rule_version_type", List.of("rule_version_id", "leave_type_id")),
                new Key("leave_ledger_lines", "fk_leave_ledger_lines_account_relationship_type", List.of("leave_account_id", "employment_relationship_id", "leave_type_id")),
                new Key("leave_ledger_lines", "fk_leave_ledger_lines_consumes_line_id", List.of("consumes_line_id")),
                new Key("leave_ledger_lines", "fk_leave_ledger_lines_corrects_line_id", List.of("corrects_line_id")),
                new Key("leave_grant_basis", "fk_leave_grant_basis_line_type", List.of("leave_ledger_line_id", "leave_type_id")),
                new Key("leave_grant_basis", "fk_leave_grant_basis_rule_version_type", List.of("rule_version_id", "leave_type_id")));
        try (Connection db = connect()) {
            constraintMode(db); // Isolate each FK, including when another FK on the same type would refuse first.
            for (Key key : keys) {
                var valid = validRow(db, key.table());
                if (key.table().equals("monthly_confirmation_lines")) { valid.put("line_kind", "LEAVE"); valid.put("leave_type_id", insert(db, "leave_types", row("leave_types"))); valid.put("is_paid_leave", true); valid.put("minutes_count", 1); valid.put("day_hundredths_count", null); }
                if (key.columns().contains("consumes_line_id")) { valid.put("line_kind", "USE"); valid.put("consumes_line_id", insert(db, "leave_ledger_lines", validRow(db, "leave_ledger_lines"))); }
                if (key.columns().contains("corrects_line_id")) { valid.put("line_kind", "ADJUST"); valid.put("line_qty", 1); valid.put("corrects_line_id", insert(db, "leave_ledger_lines", validRow(db, "leave_ledger_lines"))); }
                var point = db.setSavepoint();
                for (Key competing : keys) if (competing.table().equals(key.table()) && !competing.name().equals(key.name()))
                    run(db, "alter table " + SCHEMA + key.table() + " drop constraint " + competing.name());
                for (String column : key.columns()) {
                    Object wrong = UUID.randomUUID();
                    if (column.equals("leave_type_id")) wrong = insert(db, "leave_types", row("leave_types"));
                    if (column.equals("rule_version_id")) wrong = insert(db, "leave_rule_versions", validRow(db, "leave_rule_versions"));
                    if (column.equals("leave_account_id")) wrong = insert(db, "leave_accounts", validRow(db, "leave_accounts"));
                    if (column.equals("leave_ledger_line_id")) wrong = insert(db, "leave_ledger_lines", validRow(db, "leave_ledger_lines"));
                    var invalid = copy(valid, column, wrong);
                    // For single-column keys the wrong value must be absent, not another valid parent.
                    if (key.columns().size() == 1) invalid.put(column, UUID.randomUUID());
                    refused(db, "23503", key.name(), () -> insert(db, key.table(), invalid));
                }
                insert(db, key.table(), valid); db.rollback(point); db.releaseSavepoint(point);
            }
            var expected = new ArrayList<>(keys.stream().map(Key::name).toList());
            expected.addAll(List.of("fk_leave_request_days_leave_request_id", "fk_leave_request_holds_leave_request_id", "fk_monthly_confirmation_lines_monthly_confirmation_id"));
            assertThat(strings(db, "select conname from pg_constraint where connamespace = 'organization_leave'::regnamespace and contype = 'f'")).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(strings(db, "select conname from pg_constraint where connamespace = 'organization_leave'::regnamespace and contype = 'f' and confdeltype <> 'r'")).isEmpty();
            db.rollback();
        }
    }

    @Test
    void entitlementAndBasisRowsBelongOnlyToTheirParentInsertTransaction() throws Exception {
        try (Connection db = connect()) {
            var basis = validRow(db, "leave_grant_basis"); Object type = basis.get("leave_type_id");
            var band = row("leave_entitlement_rules"); band.put("rule_version_id", basis.get("rule_version_id")); insert(db, "leave_entitlement_rules", band);
            insert(db, "leave_grant_basis", basis);
            refused(db, "23505", "uk_leave_grant_basis_leave_ledger_line_id", () -> insert(db, "leave_grant_basis", copy(basis)));
            db.commit();
            try {
                Object accountId = strings(db, "select leave_account_id from organization_leave.leave_ledger_lines where id = '" + basis.get("leave_ledger_line_id") + "'").getFirst();
                try (Connection locker = connect(); Connection writer = connect()) {
                    run(locker, "set local lock_timeout = '200ms'"); run(writer, "set local lock_timeout = '200ms'");
                    var pending = row("leave_ledger_lines"); pending.put("leave_account_id", UUID.fromString(accountId.toString()));
                    pending.put("leave_type_id", type); pending.put("employment_relationship_id", UUID.fromString(strings(db, "select employment_relationship_id from organization_leave.leave_accounts where id = '" + accountId + "'").getFirst()));
                    insert(writer, "leave_ledger_lines", pending);
                    refused(locker, "55P03", "lock timeout", () -> sealAccount(locker, UUID.fromString(accountId.toString())));
                    writer.rollback(); run(writer, "set local lock_timeout = '200ms'"); sealAccount(locker, UUID.fromString(accountId.toString()));
                    refused(writer, "55P03", "lock timeout", () -> insert(writer, "leave_ledger_lines", pending)); locker.rollback();
                    insert(writer, "leave_ledger_lines", pending); writer.rollback();
                }
                var successor = row("leave_rule_versions"); successor.put("leave_type_id", type); successor.put("version_number", 2);
                successor.put("effective_from_date", LocalDate.parse(strings(db, "select effective_from_date + 1 from organization_leave.leave_rule_versions where id = '" + basis.get("rule_version_id") + "'").getFirst()));
                insert(db, "leave_rule_versions", successor); voidVersion(db, successor.get("id")); // Closing/reopening must not make old rows writable via xmin.
                refused(db, "23001", "tg_leave_entitlement_rules_change", () -> insert(db, "leave_entitlement_rules", copy(band, "service_years_from_count", 10)));
                refused(db, "23001", "tg_leave_grant_basis_change", () -> insert(db, "leave_grant_basis", copy(basis)));
                var account = validRow(db, "leave_accounts"); insert(db, "leave_accounts", account);
                for (String kind : List.of("GRANT", "ADJUST", "ROLLOVER_IN", "HOLD", "RELEASE", "USE", "REVERSE_USE", "EXPIRE", "ROLLOVER_OUT")) {
                    var value = line(account, kind, null); Object id = insert(db, "leave_ledger_lines", value);
                    var input = copy(basis, "leave_ledger_line_id", id, "leave_type_id", account.get("leave_type_id"), "rule_version_id", account.get("rule_version_id"));
                    if (List.of("GRANT", "ADJUST").contains(kind)) insert(db, "leave_grant_basis", input);
                    else refused(db, "23514", "tg_leave_grant_basis_change", () -> insert(db, "leave_grant_basis", input));
                }
            } finally {
                db.rollback(); constraintMode(db);
                run(db, "delete from organization_leave.leave_grant_basis where leave_type_id = ?", type);
                run(db, "delete from organization_leave.leave_ledger_lines where leave_type_id = ?", type);
                run(db, "delete from organization_leave.leave_accounts where leave_type_id = ?", type);
                run(db, "delete from organization_leave.leave_entitlement_rules where rule_version_id = ?", basis.get("rule_version_id"));
                run(db, "delete from organization_leave.leave_rule_versions where leave_type_id = ?", type);
                run(db, "delete from organization_leave.leave_types where id = ?", type); db.commit();
            }
        }
    }
}
