package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.domain.ChangeKind;
import com.forcitis.one.organization.workforce.domain.EntityKind;
import com.forcitis.one.organization.workforce.domain.IssueKind;
import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunId;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL touches only the organization_workforce schema (sync_runs, sync_diffs). */
@Repository
class JdbcSyncRunRepository implements SyncRunRepository {

    private static final String SELECT_RUN = """
            select id, trigger_kind, status, started_at, finished_at, source_org_unit_count, source_employee_count,
                   incoming_active_org_unit_count, incoming_employed_count, previous_active_org_unit_count,
                   previous_employed_count, org_unit_change_count, employee_change_count, position_change_count,
                   isolated_row_count, issue_count, employed_without_wecom_count, held_reason, failure_reason,
                   released_by, released_at, released_by_run_id
              from organization_workforce.sync_runs
            """;

    private final JdbcClient jdbc;

    JdbcSyncRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SyncRunId begin(SyncTrigger trigger, Instant now) {
        // A run that was killed mid-way (restart, crash) never finishes; close it so it does not look alive forever.
        jdbc.sql("""
                update organization_workforce.sync_runs set status = 'FAILED', finished_at = :now,
                       failure_reason = 'interrupted: the run never finished', updated_at = :now
                 where status = 'RUNNING' and started_at < cast(:now as timestamptz) - interval '15 minutes'
                """).param("now", Timestamp.from(now)).update();
        long id = jdbc.sql("""
                insert into organization_workforce.sync_runs (trigger_kind, status, started_at, created_at, updated_at)
                values (:trigger, 'RUNNING', :now, :now, :now) returning id
                """).param("trigger", trigger.name()).param("now", Timestamp.from(now)).query(Long.class).single();
        return new SyncRunId(id);
    }

    @Override
    public void markSucceeded(SyncRunId syncRunId, Instant now, RunStats stats) {
        writeStats(syncRunId, now, stats, "SUCCEEDED", null);
        // the source is back to normal figures: older holds are obsolete (no administrator needed, no user recorded).
        jdbc.sql("""
                update organization_workforce.sync_runs set status = 'RELEASED', released_at = :now,
                       released_by_run_id = :run, updated_at = :now
                 where status = 'HELD' and id <> :run
                """).param("now", Timestamp.from(now)).param("run", syncRunId.value()).update();
    }

    @Override
    public SyncRunId markHeld(SyncRunId syncRunId, Instant now, RunStats stats, String reason) {
        Optional<Long> earlier = jdbc.sql("select id from organization_workforce.sync_runs where status = 'HELD' and id <> :id order by id limit 1")
                .param("id", syncRunId.value()).query(Long.class).optional();
        if (earlier.isPresent()) {
            writeStats(new SyncRunId(earlier.get()), now, stats, "HELD", reason);
            jdbc.sql("delete from organization_workforce.sync_runs where id = :id").param("id", syncRunId.value()).update();
            return new SyncRunId(earlier.get());
        }
        writeStats(syncRunId, now, stats, "HELD", reason);
        return syncRunId;
    }

    private void writeStats(SyncRunId id, Instant now, RunStats s, String status, String heldReason) {
        jdbc.sql("""
                update organization_workforce.sync_runs set status = :status, finished_at = :now,
                       source_org_unit_count = :a, source_employee_count = :b, incoming_active_org_unit_count = :c,
                       incoming_employed_count = :d, previous_active_org_unit_count = :e, previous_employed_count = :f,
                       org_unit_change_count = :g, employee_change_count = :h, position_change_count = :i,
                       isolated_row_count = :j, issue_count = :k, employed_without_wecom_count = :l,
                       held_reason = :heldReason, updated_at = :now
                 where id = :id
                """)
                .param("status", status).param("now", Timestamp.from(now)).param("id", id.value())
                .param("a", s.sourceOrgUnitCount()).param("b", s.sourceEmployeeCount())
                .param("c", s.incomingActiveOrgUnitCount()).param("d", s.incomingEmployedCount())
                .param("e", s.previousActiveOrgUnitCount()).param("f", s.previousEmployedCount())
                .param("g", s.orgUnitChangeCount()).param("h", s.employeeChangeCount()).param("i", s.positionChangeCount())
                .param("j", s.isolatedRowCount()).param("k", s.issueCount()).param("l", s.employedWithoutWecomCount())
                .param("heldReason", heldReason).update();
    }

    @Override
    public void markFailed(SyncRunId syncRunId, Instant now, String reason) {
        jdbc.sql("""
                update organization_workforce.sync_runs set status = 'FAILED', finished_at = :now, failure_reason = :reason,
                       updated_at = :now where id = :id
                """).param("now", Timestamp.from(now)).param("reason", truncate(reason)).param("id", syncRunId.value()).update();
    }

    @Override
    public void markReleased(SyncRunId held, SyncRunId releasedByRun, UserId releasedBy, Instant now) {
        jdbc.sql("""
                update organization_workforce.sync_runs set status = 'RELEASED', released_by = :by, released_at = :now,
                       released_by_run_id = :run, updated_at = :now where id = :id
                """).param("by", releasedBy.value()).param("now", Timestamp.from(now))
                .param("run", releasedByRun.value()).param("id", held.value()).update();
    }

    @Override
    public void saveDiffs(SyncRunId syncRunId, List<SyncDiff> diffs) {
        for (SyncDiff d : diffs) {
            jdbc.sql("""
                    insert into organization_workforce.sync_diffs (sync_run_id, entity_kind, subject_key, change_kind,
                           changed_fields, issue_kind, detail)
                    values (:run, :entity, :subject, :change, :fields, :issue, :detail)
                    """)
                    .param("run", syncRunId.value()).param("entity", d.entityKind().name()).param("subject", d.subjectKey())
                    .param("change", d.changeKind().name())
                    .param("fields", d.changedFields().isEmpty() ? null : String.join(",", d.changedFields()))
                    .param("issue", d.issueKind() == null ? null : d.issueKind().name())
                    .param("detail", truncate(d.detail())).update();
        }
    }

    @Override
    public Optional<SyncRun> find(SyncRunId syncRunId) {
        return jdbc.sql(SELECT_RUN + " where id = :id").param("id", syncRunId.value()).query((rs, n) -> run(rs)).optional();
    }

    @Override
    public List<SyncRun> findRecent(int limit) {
        return jdbc.sql(SELECT_RUN + " order by started_at desc, id desc limit :limit").param("limit", limit)
                .query((rs, n) -> run(rs)).list();
    }

    @Override
    public List<SyncDiff> findDiffs(SyncRunId syncRunId) {
        return jdbc.sql("""
                select entity_kind, subject_key, change_kind, changed_fields, issue_kind, detail
                  from organization_workforce.sync_diffs where sync_run_id = :id order by id
                """).param("id", syncRunId.value()).query((rs, n) -> {
            String fields = rs.getString("changed_fields");
            String issue = rs.getString("issue_kind");
            return new SyncDiff(EntityKind.valueOf(rs.getString("entity_kind")), rs.getString("subject_key"),
                    ChangeKind.valueOf(rs.getString("change_kind")),
                    fields == null ? List.of() : Arrays.asList(fields.split(",")),
                    issue == null ? null : IssueKind.valueOf(issue), rs.getString("detail"));
        }).list();
    }

    @Override
    public Optional<SyncRun> findLatest() {
        return jdbc.sql(SELECT_RUN + " order by id desc limit 1").query((rs, n) -> run(rs)).optional();
    }

    @Override
    public Optional<Instant> findLastSucceededAt() {
        List<Timestamp> latest = jdbc.sql("select max(finished_at) from organization_workforce.sync_runs where status = 'SUCCEEDED'")
                .query((rs, n) -> rs.getTimestamp(1)).list();
        return latest.stream().filter(java.util.Objects::nonNull).findFirst().map(Timestamp::toInstant);
    }

    @Override
    public int countHeld() {
        return jdbc.sql("select count(*) from organization_workforce.sync_runs where status = 'HELD'").query(Integer.class).single();
    }

    @Override
    public Optional<Integer> findLatestEmployedWithoutWecomCount() {
        return jdbc.sql("""
                select employed_without_wecom_count from organization_workforce.sync_runs
                 where status = 'SUCCEEDED' order by id desc limit 1
                """).query(Integer.class).optional();
    }

    private static SyncRun run(ResultSet rs) throws SQLException {
        RunStats stats = null;
        Integer sourceUnits = integer(rs, "source_org_unit_count");
        if (sourceUnits != null) {
            stats = new RunStats(sourceUnits, integer(rs, "source_employee_count"), integer(rs, "incoming_active_org_unit_count"),
                    integer(rs, "incoming_employed_count"), integer(rs, "previous_active_org_unit_count"),
                    integer(rs, "previous_employed_count"), integer(rs, "org_unit_change_count"),
                    integer(rs, "employee_change_count"), integer(rs, "position_change_count"),
                    integer(rs, "isolated_row_count"), integer(rs, "issue_count"), integer(rs, "employed_without_wecom_count"));
        }
        long releasedBy = rs.getLong("released_by");
        UserId releasedByUser = rs.wasNull() ? null : new UserId(releasedBy);
        long releasedByRun = rs.getLong("released_by_run_id");
        SyncRunId releasedByRunId = rs.wasNull() ? null : new SyncRunId(releasedByRun);
        Timestamp finished = rs.getTimestamp("finished_at");
        Timestamp released = rs.getTimestamp("released_at");
        return new SyncRun(new SyncRunId(rs.getLong("id")), SyncTrigger.valueOf(rs.getString("trigger_kind")),
                SyncRunStatus.valueOf(rs.getString("status")), rs.getTimestamp("started_at").toInstant(),
                finished == null ? null : finished.toInstant(), stats, rs.getString("held_reason"),
                rs.getString("failure_reason"), releasedByUser, released == null ? null : released.toInstant(), releasedByRunId);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 2000 ? text : text.substring(0, 2000);
    }
}
