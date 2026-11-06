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


/** #1470: plain SQL probes of the L1b version, account, ledger and basis rules; same fixture as LeaveMigrationIT. */
@Testcontainers
class LeaveLedgerMigrationIT extends LeaveSchemaProbe {
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
                // every column of both keys matters: a replay that differs only in quantity is still a replay
                refused(db, "23505", "uk_leave_ledger_lines_source_key", () -> insert(db, "leave_ledger_lines", copy(source, "line_qty", 5)));
                refused(db, "23505", "uk_leave_ledger_lines_event_key", () -> insert(db, "leave_ledger_lines", copy(event, "leave_account_id", nextYear.get("id"), "source_kind", "HR_ADJUSTMENT", "line_qty", 5)));
                // the same event and original year of another line kind, another relationship or another type is a different entitlement line
                insert(db, "leave_ledger_lines", copy(event, "leave_account_id", nextYear.get("id"), "line_kind", "ROLLOVER_IN"));
                var otherRelationship = copy(account, "employment_relationship_id", UUID.randomUUID(), "leave_year_number", 2027); insert(db, "leave_accounts", otherRelationship);
                insert(db, "leave_ledger_lines", copy(event, "leave_account_id", otherRelationship.get("id"), "employment_relationship_id", otherRelationship.get("employment_relationship_id")));
                var otherType = validRow(db, "leave_accounts"); otherType.put("employment_relationship_id", account.get("employment_relationship_id")); insert(db, "leave_accounts", otherType);
                insert(db, "leave_ledger_lines", copy(event, "leave_account_id", otherType.get("id"), "leave_type_id", otherType.get("leave_type_id")));
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
