package com.forcitis.one.acceptance.platform.identity.data_scope;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * F-1 card section 7.1, second block (#729): rows of platform_identity.permission_adjustments on the database the application's own
 * migrations build, through {@link CustomOrgUnitRows} (one never-committed transaction per test, probes under savepoints). A row is a
 * column map; the shapes below are the card's valid ones for each kind and origin (constraint table rows 9-36), and a test changes one
 * column and expects the named constraint to refuse it. Times are literals, never the database clock.
 */
final class PermissionAdjustmentRows {

    static final String TABLE = "platform_identity.permission_adjustments";
    static final String CHECK_VIOLATION = "23514";
    static final String FOREIGN_KEY_VIOLATION = "23503";

    static final String T0 = CustomOrgUnitRows.T0;
    static final String T1 = CustomOrgUnitRows.T1;
    static final String T2 = CustomOrgUnitRows.T2;
    static final String T3 = "2026-11-05T02:00:00Z";

    private static final Set<String> TIMESTAMPS = Set.of("escalation_due_at", "escalated_at", "upheld_at", "uphold_expires_at", "expired_at",
            "regeneration_decided_at", "adjusted_at", "closed_at", "created_at", "updated_at");

    final CustomOrgUnitRows db;

    PermissionAdjustmentRows(CustomOrgUnitRows db) {
        this.db = db;
    }

    /** A column map in insertion order; null values are written as SQL null. */
    static final class Row {
        final Map<String, Object> columns = new LinkedHashMap<>();

        Row with(String column, Object value) {
            columns.put(column, value);
            return this;
        }

        Row copy() {
            Row copy = new Row();
            copy.columns.putAll(columns);
            return copy.with("permission_adjustment_id", UUID.randomUUID());
        }

        UUID id() {
            return (UUID) columns.get("permission_adjustment_id");
        }
    }

    private static Row base(String kind, String origin) {
        return new Row().with("permission_adjustment_id", UUID.randomUUID()).with("kind", kind).with("origin_kind", origin).with("status", "OPEN")
                .with("escalation_due_at", T1).with("created_at", T0).with("updated_at", T0);
    }

    /** Sensitive role review raised by a sync that moved the holder to another org unit (ADR-0032 item 5). */
    Row sensitiveOrgUnitChange() throws SQLException {
        return base("SENSITIVE_ROLE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", db.account()).with("trigger_sync_run_id", UUID.randomUUID())
                .with("trigger_field_kind", "ORG_UNIT").with("before_org_unit_id", UUID.randomUUID()).with("after_org_unit_id", UUID.randomUUID());
    }

    /** Sensitive role review raised by a position change only: the org unit stays the same (constraint table row 35). */
    Row sensitivePositionChange() throws SQLException {
        UUID unit = UUID.randomUUID();
        return base("SENSITIVE_ROLE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", db.account()).with("trigger_sync_run_id", UUID.randomUUID())
                .with("trigger_field_kind", "POSITION").with("before_org_unit_id", unit).with("after_org_unit_id", unit)
                .with("before_position_id", UUID.randomUUID()).with("after_position_id", UUID.randomUUID());
    }

    /** Scope review origin 1: a holder of custom org units moved; the item goes to the new unit's head (ADR-0032 item 4 clause 2). */
    Row scopeReviewTransfer() throws SQLException {
        return base("SCOPE_REVIEW", "ASSIGNMENT_CHANGE").with("user_id", db.account()).with("trigger_sync_run_id", UUID.randomUUID())
                .with("trigger_field_kind", "ORG_UNIT").with("before_org_unit_id", UUID.randomUUID()).with("after_org_unit_id", UUID.randomUUID())
                .with("recipient_employee_id", UUID.randomUUID());
    }

    /** Scope review origin 2: a role version still names an org unit that became inactive (RULE-387); about a role, not a person. */
    Row scopeReviewInactiveUnit() throws SQLException {
        return base("SCOPE_REVIEW", "ORG_UNIT_INACTIVE").with("role_id", db.role()).with("org_unit_id", UUID.randomUUID())
                .with("trigger_sync_run_id", UUID.randomUUID());
    }

    /** A sensitive role review regenerated when the upheld item it names expired (ADR-0044 item 3); the original is inserted first. */
    Row regenerated() throws SQLException {
        Row original = expired(sensitiveOrgUnitChange());
        insert(original);
        return base("SENSITIVE_ROLE_REVIEW", "UPHOLD_EXPIRY").with("user_id", original.columns.get("user_id"))
                .with("original_permission_adjustment_id", original.id());
    }

    /** The row upheld by another account; a sensitive role review also gets a business owner and an expiry, a scope review neither. */
    Row upheld(Row row) throws SQLException {
        row.with("status", "UPHELD").with("upheld_at", T1).with("upheld_by", db.account()).with("uphold_reason", "still needed for the audit")
                .with("updated_at", T1);
        if ("SENSITIVE_ROLE_REVIEW".equals(row.columns.get("kind"))) {
            row.with("business_owner_employee_id", UUID.randomUUID()).with("uphold_expires_at", T2);
        }
        return row;
    }

    /** An upheld sensitive role review the expiry job expired at its expiry and decided on. */
    Row expired(Row row) throws SQLException {
        return upheld(row).with("status", "EXPIRED").with("expired_at", T2).with("regeneration_decided_at", T2).with("updated_at", T2);
    }

    /** Marked adjusted by another account, linked to one audit entry (ADR-0044 item 2 clause 1). */
    Row adjusted(Row row) throws SQLException {
        return row.with("status", "ADJUSTED").with("adjusted_at", T1).with("adjusted_by", db.account()).with("adjusted_audit_entry_id", UUID.randomUUID())
                .with("updated_at", T1);
    }

    Row closed(Row row, String status) {
        return row.with("status", status).with("closed_at", T1).with("closure_sync_run_id", UUID.randomUUID()).with("updated_at", T1);
    }

    static String insertSql(Row row) {
        StringJoiner names = new StringJoiner(", ");
        StringJoiner values = new StringJoiner(", ");
        for (String column : row.columns.keySet()) {
            names.add(column);
            values.add(TIMESTAMPS.contains(column) ? "?::timestamptz" : "?");
        }
        return "insert into " + TABLE + " (" + names + ") values (" + values + ")";
    }

    void insert(Row row) throws SQLException {
        db.setup(insertSql(row), row.columns.values().toArray());
    }

    void accepted(Row row) throws SQLException {
        db.accepted(insertSql(row), row.columns.values().toArray());
    }

    void refusedBy(String sqlState, String constraint, Row row) throws SQLException {
        db.refusedByConstraint(sqlState, constraint, insertSql(row), row.columns.values().toArray());
    }

    void refusedByCheck(String constraint, Row row) throws SQLException {
        refusedBy(CHECK_VIOLATION, constraint, row);
    }
}
