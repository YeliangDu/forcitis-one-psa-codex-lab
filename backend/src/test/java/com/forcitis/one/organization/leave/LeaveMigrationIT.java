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

/** #1400 / #1470: plain SQL probes of the signed L1a and L1b schemas (the L1b version, account and ledger rules are in LeaveLedgerMigrationIT); no application validation masks a database refusal. */
@Testcontainers
class LeaveMigrationIT extends LeaveSchemaProbe {
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
                check("leave_rule_versions", "hr_lead_threshold_days_count", "hr_lead_threshold_days_count", 0),
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
                check("leave_ledger_lines", "policy_grant_kind", "source_kind", "POLICY_GRANT", "line_kind", "USE"),
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
                check("leave_policy_grants", "grant_qty", "grant_qty", 0),
                check("leave_policy_grants", "request_key", "request_key", " "),
                check("leave_policy_grants", "basis_text", "basis_text", " "),
                check("leave_policy_grants", "effective_date", "effective_date", new java.sql.Date(org.postgresql.PGStatement.DATE_POSITIVE_INFINITY)),
                check("leave_requests", "period", "end_at", AT),
                check("leave_requests", "status", "status", "UNKNOWN"),
                check("leave_requests", "taken_until_at", "status", "RECALLED"),
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
            for (String table : List.of("leave_request_days", "leave_request_holds", "attendance_records", "monthly_confirmations", "monthly_confirmation_lines", "leave_policy_grants")) {
                var first = validRow(db, table);
                var same = new LinkedHashMap<>(first);
                same.put("id", UUID.randomUUID());
                String key = switch (table) {
                    case "leave_request_days" -> "request_work_date";
                    case "leave_request_holds" -> "ledger_line_id"; // overlaps the request/line key: probe that one in isolation below
                    case "attendance_records" -> "source_external_record_key";
                    case "monthly_confirmations" -> "employee_month";
                    case "monthly_confirmation_lines" -> "identity";
                    case "leave_policy_grants" -> "account_request_key";
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
                    case "leave_policy_grants" -> same.put("request_key", "another-key");
                    default -> throw new IllegalArgumentException(table);
                }
                insert(db, table, same);
            }
            assertThat(strings(db, "select indexname from pg_indexes where schemaname = 'organization_leave' and indexname like 'uk_%' and tablename not in ('leave_types', 'leave_rule_versions', 'leave_entitlement_rules', 'leave_accounts', 'leave_ledger_lines', 'leave_grant_basis')"))
                    .containsExactlyInAnyOrder("uk_leave_requests_approval_instance_id", "uk_overtime_requests_approval_instance_id",
                            "uk_leave_request_days_request_work_date", "uk_leave_request_holds_request_line", "uk_leave_request_holds_ledger_line_id",
                            "uk_attendance_records_source_external_record_key", "uk_monthly_confirmations_employee_month", "uk_monthly_confirmation_lines_identity",
                            "uk_leave_policy_grants_account_request_key");
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
                    if (inactive.equals("RECALLED")) draft.put("taken_until_at", draft.get("start_at"));
                    UUID id = insert(db, "leave_requests", draft);
                    refused(db, "23P01", "ex_leave_requests_live_period_overlap", () ->
                            run(db, "update organization_leave.leave_requests set status = 'SUBMITTED', taken_until_at = null where id = ?", id));
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
    @DisplayName("#1400 / #1592 RULE-519 attendance, holds and policy grants allow inserts but reject every update, delete and truncate")
    void appendOnlyFactsCannotBeRewrittenOrCleared() throws Exception {
        try (Connection db = connect()) {
            for (String table : List.of("attendance_records", "leave_request_holds", "leave_policy_grants")) {
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
    @DisplayName("#1400 / #1592 signed schema has exactly fourteen tables; nullable comments, no carved-out or biometric/location fields, three completed deferred UUID keys")
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

}
