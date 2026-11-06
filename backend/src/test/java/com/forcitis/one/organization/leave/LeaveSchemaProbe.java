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


/** Shared plain-SQL probe fixture for the signed leave schemas: the container, the migration and the row factories. */
@Testcontainers
abstract class LeaveSchemaProbe {
    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-05T02:00:00Z");
    static final LocalDate MONTH = LocalDate.of(2026, 11, 1);
    static final List<String> TABLES = List.of("leave_requests", "leave_request_days", "leave_request_holds",
            "overtime_requests", "attendance_records", "monthly_confirmations", "monthly_confirmation_lines",
            "leave_types", "leave_rule_versions", "leave_entitlement_rules", "leave_accounts", "leave_ledger_lines", "leave_grant_basis");
    static final String SCHEMA = "organization_leave.";

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    static Connection connect() throws SQLException {
        Connection db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db.setAutoCommit(false);
        return db;
    }

    static void run(Connection db, String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
        }
    }

    static List<String> strings(Connection db, String sql) throws SQLException {
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

    static void refused(Connection db, String state, String rule, Sql action) throws SQLException {
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

    static Map<String, Object> row(String table) {
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

    static UUID insert(Connection db, String table, Map<String, Object> row) throws SQLException {
        run(db, "insert into " + SCHEMA + table + " (" + String.join(", ", row.keySet()) + ") values ("
                + row.keySet().stream().map(key -> "?").collect(Collectors.joining(", ")) + ")", row.values().toArray());
        return (UUID) row.get("id");
    }

    /** Parent identities are synthetic: cross-context references intentionally need no workforce/identity data. */
    static Map<String, Object> validRow(Connection db, String table) throws SQLException {
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

    static final List<String> L1B = List.of("leave_types", "leave_rule_versions", "leave_entitlement_rules", "leave_accounts", "leave_ledger_lines", "leave_grant_basis");
    static void constraintMode(Connection db) throws SQLException {
        for (String table : L1B) run(db, "alter table " + SCHEMA + table + " disable trigger user");
    }

}
