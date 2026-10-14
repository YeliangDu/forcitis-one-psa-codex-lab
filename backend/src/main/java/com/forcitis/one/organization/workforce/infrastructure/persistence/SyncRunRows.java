package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

final class SyncRunRows {
    static final String SELECT = """
            select id, trigger_kind, status, started_at, finished_at, source_org_unit_count, source_employee_count,
                   incoming_active_org_unit_count, incoming_employed_count, previous_active_org_unit_count,
                   previous_employed_count, org_unit_change_count, employee_change_count, position_change_count,
                   isolated_row_count, issue_count, employed_without_wecom_count, held_reason, failure_reason,
                   released_by, released_at, released_by_run_id, version, event_count
              from organization_workforce.sync_runs
            """;

    private SyncRunRows() {
    }

    static SyncRun aggregate(ResultSet rs) throws SQLException {
        return SyncRun.restore(new SyncRunId(rs.getLong("id")), SyncTrigger.valueOf(rs.getString("trigger_kind")), rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(), instant(rs, "finished_at"), stats(rs), rs.getString("held_reason"),
                rs.getString("failure_reason"), user(rs, "released_by"), instant(rs, "released_at"), runId(rs, "released_by_run_id"),
                rs.getLong("version"), rs.getLong("event_count"));
    }

    static SyncRunRecord record(ResultSet rs) throws SQLException {
        return new SyncRunRecord(new SyncRunId(rs.getLong("id")), SyncTrigger.valueOf(rs.getString("trigger_kind")),
                SyncRunStatus.valueOf(rs.getString("status")), rs.getTimestamp("started_at").toInstant(), instant(rs, "finished_at"),
                stats(rs), rs.getString("held_reason"), rs.getString("failure_reason"), user(rs, "released_by"),
                instant(rs, "released_at"), runId(rs, "released_by_run_id"));
    }

    private static RunStats stats(ResultSet rs) throws SQLException {
        Integer sourceUnits = integer(rs, "source_org_unit_count");
        if (sourceUnits == null) {
            return null;
        }
        return new RunStats(sourceUnits, integer(rs, "source_employee_count"), integer(rs, "incoming_active_org_unit_count"),
                integer(rs, "incoming_employed_count"), integer(rs, "previous_active_org_unit_count"),
                integer(rs, "previous_employed_count"), integer(rs, "org_unit_change_count"), integer(rs, "employee_change_count"),
                integer(rs, "position_change_count"), integer(rs, "isolated_row_count"), integer(rs, "issue_count"),
                integer(rs, "employed_without_wecom_count"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static UserId user(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new UserId(value);
    }

    private static SyncRunId runId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new SyncRunId(value);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
