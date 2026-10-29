package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.organization.workforce.api.HoldKind;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;

final class SyncRunRows {
    static final String SELECT = """
            select id, trigger_kind, status, started_at, finished_at, source_org_unit_count, source_employee_count,
                   incoming_active_org_unit_count, incoming_employed_count, previous_active_org_unit_count,
                   previous_employed_count, org_unit_change_count, employee_change_count, position_change_count,
                   isolated_row_count, issue_count, employed_without_wecom_count, held_reason, failure_reason,
                   released_by, released_at, released_by_run_id, hold_kind_codes, released_kind_codes, version, event_count,
                   (select coalesce(array_agg(employee_id order by employee_id), '{}') from organization_workforce.sync_run_held_separations h
                     where h.sync_run_id = sync_runs.id) as held_separation_employee_ids
              from organization_workforce.sync_runs
            """;

    private SyncRunRows() {
    }

    static SyncRun aggregate(ResultSet rs) throws SQLException {
        return SyncRun.restore(new SyncRunId(rs.getObject("id", UUID.class)), SyncTrigger.valueOf(rs.getString("trigger_kind")), rs.getString("status"),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(), instant(rs, "finished_at"), stats(rs), rs.getString("held_reason"),
                rs.getString("failure_reason"), user(rs, "released_by"), instant(rs, "released_at"), runId(rs, "released_by_run_id"),
                kinds(rs, "hold_kind_codes"), kinds(rs, "released_kind_codes"), separations(rs), rs.getLong("version"), rs.getLong("event_count"));
    }

    static SyncRunRecord record(ResultSet rs) throws SQLException {
        return new SyncRunRecord(new SyncRunId(rs.getObject("id", UUID.class)), SyncTrigger.valueOf(rs.getString("trigger_kind")),
                SyncRunStatus.valueOf(rs.getString("status")), rs.getObject("started_at", OffsetDateTime.class).toInstant(), instant(rs, "finished_at"),
                stats(rs), rs.getString("held_reason"), rs.getString("failure_reason"), user(rs, "released_by"),
                instant(rs, "released_at"), runId(rs, "released_by_run_id"), kinds(rs, "hold_kind_codes"), kinds(rs, "released_kind_codes"),
                separations(rs));
    }

    private static Set<HoldKind> kinds(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        Set<HoldKind> kinds = new HashSet<>();
        if (array != null) {
            for (String code : (String[]) array.getArray()) {
                kinds.add(HoldKind.valueOf(code));
            }
        }
        return kinds;
    }

    private static List<EmployeeId> separations(ResultSet rs) throws SQLException {
        Array array = rs.getArray("held_separation_employee_ids");
        List<EmployeeId> people = new ArrayList<>();
        if (array != null) {
            for (UUID id : (UUID[]) array.getArray()) {
                people.add(new EmployeeId(id));
            }
        }
        return people;
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
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static UserId user(ResultSet rs, String column) throws SQLException {
        UUID value = rs.getObject(column, UUID.class);
        return value == null ? null : new UserId(value);
    }

    private static SyncRunId runId(ResultSet rs, String column) throws SQLException {
        UUID value = rs.getObject(column, UUID.class);
        return value == null ? null : new SyncRunId(value);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
