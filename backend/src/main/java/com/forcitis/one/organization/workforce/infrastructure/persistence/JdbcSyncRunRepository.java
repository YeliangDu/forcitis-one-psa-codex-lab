package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncRunRepository;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSyncRunRepository implements SyncRunRepository {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcSyncRunRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public SyncRun insertRunning(SyncTrigger trigger, Instant now) {
        SyncRun draft = SyncRun.unsaved(trigger, now);
        SyncRunId id = new SyncRunId(ids.next());
        jdbc.sql("""
                insert into organization_workforce.sync_runs (id, trigger_kind, status, started_at, created_at, updated_at, version, event_count)
                values (:id, :trigger, :status, :now, :now, :now, 0, 0)
                """).param("id", id.value()).param("trigger", trigger.name()).param("status", draft.status().name())
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();
        return draft.withId(id);
    }

    @Override
    public Optional<SyncRun> find(SyncRunId syncRunId) {
        return jdbc.sql(SyncRunRows.SELECT + """
                 where id = :id
                """).param("id", syncRunId.value())
                .query((rs, n) -> SyncRunRows.aggregate(rs)).optional();
    }

    @Override
    public List<SyncRun> findAbandoned(Instant cutoff) {
        return jdbc.sql(SyncRunRows.SELECT + """
                 where status = 'RUNNING' and started_at < :cutoff order by started_at, id
                """)
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)).query((rs, n) -> SyncRunRows.aggregate(rs)).list();
    }

    @Override
    public List<SyncRun> findHeldExcept(SyncRunId syncRunId) {
        return jdbc.sql(SyncRunRows.SELECT + """
                 where status = 'HELD' and id <> :id order by started_at, id
                """).param("id", syncRunId.value())
                .query((rs, n) -> SyncRunRows.aggregate(rs)).list();
    }

    @Override
    public void save(SyncRun run) {
        RunStats s = run.stats();
        int updated = jdbc.sql("""
                update organization_workforce.sync_runs set status = :status, finished_at = :finishedAt,
                       source_org_unit_count = :a, source_employee_count = :b, incoming_active_org_unit_count = :c,
                       incoming_employed_count = :d, previous_active_org_unit_count = :e, previous_employed_count = :f,
                       org_unit_change_count = :g, employee_change_count = :h, position_change_count = :i,
                       isolated_row_count = :j, issue_count = :k, employed_without_wecom_count = :l,
                       held_reason = :heldReason, failure_reason = :failureReason, released_by = :releasedBy,
                       released_at = :releasedAt, released_by_run_id = :releasedByRun, hold_kind_codes = :holdKinds, released_kind_codes = :releasedKinds,
                       version = :nextVersion, event_count = :eventSequence, updated_at = :updatedAt
                 where id = :id and version = :version and status = :loadedStatus
                """)
                .param("status", run.status().name())
                .param("finishedAt", run.finishedAt() == null ? null : OffsetDateTime.ofInstant(run.finishedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("a", s == null ? null : s.sourceOrgUnitCount(), Types.INTEGER)
                .param("b", s == null ? null : s.sourceEmployeeCount(), Types.INTEGER)
                .param("c", s == null ? null : s.incomingActiveOrgUnitCount(), Types.INTEGER)
                .param("d", s == null ? null : s.incomingEmployedCount(), Types.INTEGER)
                .param("e", s == null ? null : s.previousActiveOrgUnitCount(), Types.INTEGER)
                .param("f", s == null ? null : s.previousEmployedCount(), Types.INTEGER)
                .param("g", s == null ? null : s.orgUnitChangeCount(), Types.INTEGER)
                .param("h", s == null ? null : s.employeeChangeCount(), Types.INTEGER)
                .param("i", s == null ? null : s.positionChangeCount(), Types.INTEGER)
                .param("j", s == null ? null : s.isolatedRowCount(), Types.INTEGER)
                .param("k", s == null ? null : s.issueCount(), Types.INTEGER)
                .param("l", s == null ? null : s.employedWithoutWecomCount(), Types.INTEGER)
                .param("heldReason", run.heldReason(), Types.VARCHAR)
                .param("failureReason", truncate(run.failureReason()), Types.VARCHAR)
                .param("releasedBy", run.releasedBy() == null ? null : run.releasedBy().value(), Types.OTHER)
                .param("releasedAt", run.releasedAt() == null ? null : OffsetDateTime.ofInstant(run.releasedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("releasedByRun", run.releasedByRunId() == null ? null : run.releasedByRunId().value(), Types.OTHER)
                .param("holdKinds", run.holdKinds().stream().map(Enum::name).toArray(String[]::new))
                .param("releasedKinds", run.releasedKinds().stream().map(Enum::name).toArray(String[]::new))
                .param("nextVersion", run.versionAfterSave()).param("eventSequence", run.eventSequenceAfterSave())
                .param("id", run.syncRunId().value()).param("version", run.version()).param("loadedStatus", run.loadedStatus().name())
                .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "sync run " + run.syncRunId().value() + " was changed by someone else");
        }
        jdbc.sql("""
                delete from organization_workforce.sync_run_held_separations where sync_run_id = :id
                """).param("id", run.syncRunId().value()).update();
        for (var person : run.heldSeparations()) {
            jdbc.sql("""
                    insert into organization_workforce.sync_run_held_separations (sync_run_held_separation_id, sync_run_id, employee_id) values (:rowId, :id, :employee)
                    """)
                    .param("rowId", ids.next()).param("id", run.syncRunId().value()).param("employee", person.value()).update();
        }
    }

    @Override
    public void delete(SyncRun run) {
        int removed = jdbc.sql("""
                delete from organization_workforce.sync_runs where id = :id and version = :version and status = :loadedStatus
                """)
                .param("id", run.syncRunId().value()).param("version", run.version()).param("loadedStatus", run.loadedStatus().name()).update();
        if (removed != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "sync run " + run.syncRunId().value() + " was changed by someone else");
        }
    }

    @Override
    public void saveDiffs(SyncRunId syncRunId, List<SyncDiff> diffs) {
        int number = 0;
        for (SyncDiff d : diffs) {
            jdbc.sql("""
                    insert into organization_workforce.sync_diffs (id, sync_run_id, diff_number, entity_kind, subject_key, change_kind,
                           changed_field_codes, issue_kind, detail)
                    values (:id, :run, :number, :entity, :subject, :change, :fields, :issue, :detail)
                    """)
                    .param("id", ids.next()).param("run", syncRunId.value()).param("number", ++number).param("entity", d.entityKind().name()).param("subject", d.subjectKey())
                    .param("change", d.changeKind().name())
                    .param("fields", d.changedFields().isEmpty() ? null : d.changedFields().toArray(String[]::new), java.sql.Types.ARRAY)
                    .param("issue", d.issueKind() == null ? null : d.issueKind().name())
                    .param("detail", truncate(d.detail())).update();
        }
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 2000 ? text : text.substring(0, 2000);
    }
}
