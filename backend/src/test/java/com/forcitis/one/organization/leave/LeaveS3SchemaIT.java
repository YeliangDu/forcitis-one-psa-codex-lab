package com.forcitis.one.organization.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

/** #1592: plain SQL red/green rows for the signed S3 slice A; no application validation. */
class LeaveS3SchemaIT extends LeaveSchemaProbe {
    private static Map<String, Object> copy(Map<String, Object> source, Object... pairs) {
        var next = new LinkedHashMap<>(source);
        next.put("id", UUID.randomUUID());
        for (int i = 0; i < pairs.length; i += 2) next.put((String) pairs[i], pairs[i + 1]);
        return next;
    }

    @Test
    void policyGrantQuantityAndTextChecksHaveRedAndGreenBoundaries() throws Exception {
        try (Connection db = connect()) {
            var valid = validRow(db, "leave_policy_grants");
            for (int qty : List.of(0, -1)) {
                refused(db, "23514", "ck_leave_policy_grants_grant_qty", () ->
                        insert(db, "leave_policy_grants", copy(valid, "grant_qty", qty)));
            }
            for (String column : List.of("request_key", "basis_text")) {
                int limit = column.equals("request_key") ? 100 : 500;
                for (String text : List.of("", " ", " padded", "padded ", "X".repeat(limit + 1))) {
                    refused(db, "23514", "ck_leave_policy_grants_" + column, () ->
                            insert(db, "leave_policy_grants", copy(valid, column, text)));
                }
                for (String text : List.of("X", "X".repeat(limit))) {
                    insert(db, "leave_policy_grants", copy(valid, "request_key", UUID.randomUUID().toString(), column, text));
                }
            }
            insert(db, "leave_policy_grants", copy(valid, "grant_qty", Integer.MAX_VALUE));
            db.rollback();
        }
    }

    @Test
    void policyGrantEffectiveDateRejectsBothInfinitiesAndAcceptsAFiniteDate() throws Exception {
        try (Connection db = connect()) {
            var valid = validRow(db, "leave_policy_grants");
            for (String value : List.of("infinity", "-infinity")) {
                var date = new PGobject();
                date.setType("date");
                date.setValue(value);
                refused(db, "23514", "ck_leave_policy_grants_effective_date", () ->
                        insert(db, "leave_policy_grants", copy(valid, "effective_date", date)));
            }
            insert(db, "leave_policy_grants", valid);
            db.rollback();
        }
    }

    @Test
    void policyGrantReplayKeyIsUniqueWithinItsAccount() throws Exception {
        try (Connection db = connect()) {
            var first = validRow(db, "leave_policy_grants");
            insert(db, "leave_policy_grants", first);
            refused(db, "23505", "uk_leave_policy_grants_account_request_key", () ->
                    insert(db, "leave_policy_grants", copy(first, "grant_qty", 2)));
            insert(db, "leave_policy_grants", copy(first, "request_key", "another-command"));
            var anotherAccount = validRow(db, "leave_policy_grants");
            anotherAccount.put("request_key", first.get("request_key"));
            insert(db, "leave_policy_grants", anotherAccount);
            db.rollback();
        }
    }

    @Test
    void policyGrantCompositeKeyRequiresTheAccountsRelationshipAndTypeAndRestrictsParentDeletion() throws Exception {
        try (Connection db = connect()) {
            var valid = validRow(db, "leave_policy_grants");
            for (String column : List.of("leave_account_id", "employment_relationship_id", "leave_type_id")) {
                Object wrong = column.equals("leave_type_id")
                        ? insert(db, "leave_types", row("leave_types")) : UUID.randomUUID();
                refused(db, "23503", "fk_leave_policy_grants_account_relationship_type", () ->
                        insert(db, "leave_policy_grants", copy(valid, column, wrong)));
            }
            insert(db, "leave_policy_grants", valid);
            // The account has its own immutability trigger; isolate the FK's RESTRICT action.
            run(db, "alter table organization_leave.leave_accounts disable trigger user");
            // ON DELETE RESTRICT raises restrict_violation (23001); only NO ACTION would raise 23503.
            refused(db, "23001", "fk_leave_policy_grants_account_relationship_type", () ->
                    run(db, "delete from organization_leave.leave_accounts where id = ?", valid.get("leave_account_id")));
            var unreferenced = validRow(db, "leave_accounts");
            insert(db, "leave_accounts", unreferenced);
            run(db, "delete from organization_leave.leave_accounts where id = ?", unreferenced.get("id"));
            db.rollback();
        }
    }

    @Test
    void policyGrantsAllowInsertButRefuseUpdateDeleteAndTruncate() throws Exception {
        try (Connection db = connect()) {
            var grant = validRow(db, "leave_policy_grants");
            insert(db, "leave_policy_grants", grant);
            refused(db, "23001", "append-only", () -> run(db,
                    "update organization_leave.leave_policy_grants set grant_qty = grant_qty + 1 where id = ?", grant.get("id")));
            refused(db, "23001", "append-only", () -> run(db,
                    "delete from organization_leave.leave_policy_grants where id = ?", grant.get("id")));
            refused(db, "23001", "append-only", () -> run(db, "truncate table organization_leave.leave_policy_grants"));
            assertThat(strings(db, "select grant_qty from organization_leave.leave_policy_grants where id = '" + grant.get("id") + "'"))
                    .containsExactly("1");
            db.rollback();
        }
    }

    @Test
    void policyGrantLedgerSourceRequiresGrantKindAndKeepsExistingSources() throws Exception {
        try (Connection db = connect()) {
            var valid = validRow(db, "leave_ledger_lines");
            valid.put("source_kind", "POLICY_GRANT");
            // This is a year account, so no lot guard masks the kind CHECK.
            for (String kind : List.of("ROLLOVER_IN", "HOLD", "RELEASE", "USE", "REVERSE_USE", "ADJUST", "EXPIRE", "ROLLOVER_OUT")) {
                var invalid = copy(valid, "line_kind", kind, "line_qty", 1);
                if (kind.equals("ADJUST")) invalid.put("original_leave_year_number", 2026);
                refused(db, "23514", "ck_leave_ledger_lines_policy_grant_kind", () -> insert(db, "leave_ledger_lines", invalid));
            }
            refused(db, "23514", "ck_leave_ledger_lines_source_kind", () ->
                    insert(db, "leave_ledger_lines", copy(valid, "source_kind", "UNKNOWN")));
            for (String source : List.of("LEAVE_REQUEST", "OVERTIME_REQUEST", "HR_ADJUSTMENT", "SEALED_YEAR_CORRECTION",
                    "RELATIONSHIP_EVENT", "YEAR_OPEN", "YEAR_CLOSE", "COMP_TIME_EXPIRY", "MIGRATION", "POLICY_GRANT")) {
                insert(db, "leave_ledger_lines", copy(valid, "source_kind", source));
            }
            insert(db, "leave_ledger_lines", copy(valid, "source_kind", "LEAVE_REQUEST", "line_kind", "USE"));
            db.rollback();
        }
    }

    @Test
    void takenUntilIsPresentExactlyForRecalledAndStaysWithinTheInclusivePeriod() throws Exception {
        try (Connection db = connect()) {
            var valid = validRow(db, "leave_requests");
            refused(db, "23514", "ck_leave_requests_taken_until_at", () ->
                    insert(db, "leave_requests", copy(valid, "status", "RECALLED")));
            for (String status : List.of("DRAFT", "SUBMITTED", "IN_APPROVAL", "APPROVED", "REJECTED", "WITHDRAWN", "CANCELLED")) {
                var next = copy(valid, "status", status, "approval_instance_id", UUID.randomUUID(), "employment_relationship_id", UUID.randomUUID());
                refused(db, "23514", "ck_leave_requests_taken_until_at", () ->
                        insert(db, "leave_requests", copy(next, "taken_until_at", AT)));
                insert(db, "leave_requests", next);
            }
            for (var outside : List.of(AT.minusSeconds(1), AT.plusHours(1).plusSeconds(1))) {
                refused(db, "23514", "ck_leave_requests_taken_until_at", () ->
                        insert(db, "leave_requests", copy(valid, "status", "RECALLED", "taken_until_at", outside)));
            }
            for (var inside : List.of(AT, AT.plusMinutes(30), AT.plusHours(1))) {
                insert(db, "leave_requests", copy(valid, "status", "RECALLED", "taken_until_at", inside,
                        "employment_relationship_id", UUID.randomUUID()));
            }
            db.rollback();
        }
    }

    @Test
    void partialRecallBlocksOnlyTheTakenPartAndWholeRecallBlocksNothing() throws Exception {
        try (Connection db = connect()) {
            var recalled = validRow(db, "leave_requests");
            recalled.put("status", "RECALLED");
            recalled.put("end_at", AT.plusDays(2));
            recalled.put("taken_until_at", AT.plusDays(1));
            insert(db, "leave_requests", recalled);
            for (String status : List.of("SUBMITTED", "IN_APPROVAL", "APPROVED", "RECALLED")) {
                var inside = copy(recalled, "status", status, "approval_instance_id", UUID.randomUUID(),
                        "start_at", AT.plusHours(1), "end_at", AT.plusHours(2),
                        "taken_until_at", status.equals("RECALLED") ? AT.plusHours(2) : null);
                refused(db, "23P01", "ex_leave_requests_live_period_overlap", () -> insert(db, "leave_requests", inside));
                // A row can overlap the original requested end, starting exactly where the taken part ends.
                var after = copy(inside, "start_at", AT.plusDays(1), "end_at", AT.plusDays(2),
                        "taken_until_at", status.equals("RECALLED") ? AT.plusDays(2) : null);
                var point = db.setSavepoint();
                insert(db, "leave_requests", after);
                db.rollback(point);
                db.releaseSavepoint(point);
            }
            var draft = copy(recalled, "status", "DRAFT", "taken_until_at", null);
            insert(db, "leave_requests", draft);
            refused(db, "23P01", "ex_leave_requests_live_period_overlap", () -> run(db,
                    "update organization_leave.leave_requests set status = 'RECALLED', taken_until_at = ? where id = ?",
                    AT.plusDays(1), draft.get("id")));
            run(db, "update organization_leave.leave_requests set status = 'RECALLED', taken_until_at = start_at where id = ?", draft.get("id"));
            var whole = copy(recalled, "employment_relationship_id", UUID.randomUUID(), "taken_until_at", AT);
            insert(db, "leave_requests", whole);
            insert(db, "leave_requests", copy(whole, "status", "APPROVED", "taken_until_at", null));
            db.rollback();
        }
    }
}
