package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.ChangeKind;
import com.forcitis.one.organization.workforce.domain.EntityKind;
import com.forcitis.one.organization.workforce.domain.IssueKind;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSyncRunQueries implements SyncRunQueries {
    private final JdbcClient jdbc;

    JdbcSyncRunQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SyncRunRecord> find(SyncRunId syncRunId) {
        return jdbc.sql(SyncRunRows.SELECT + """
                 where id = :id
                """).param("id", syncRunId.value())
                .query((rs, n) -> SyncRunRows.record(rs)).optional();
    }

    @Override
    public List<SyncRunRecord> findBefore(Position before, int limit) {
        if (before == null) {
            return jdbc.sql(SyncRunRows.SELECT + """
                     order by started_at desc, id desc limit :limit
                    """).param("limit", limit)
                    .query((rs, n) -> SyncRunRows.record(rs)).list();
        }
        return jdbc.sql(SyncRunRows.SELECT + """
                 where (started_at, id) < (:startedAt, :id) order by started_at desc, id desc limit :limit
                """)
                .param("startedAt", OffsetDateTime.ofInstant(before.startedAt(), ZoneOffset.UTC)).param("id", before.syncRunId().value()).param("limit", limit)
                .query((rs, n) -> SyncRunRows.record(rs)).list();
    }

    @Override
    public List<SyncDiff> findDiffs(SyncRunId syncRunId) {
        return jdbc.sql("""
                select entity_kind, subject_key, change_kind, changed_field_codes, issue_kind, detail
                  from organization_workforce.sync_diffs where sync_run_id = :id order by diff_number
                """).param("id", syncRunId.value()).query((rs, n) -> {
            java.sql.Array fields = rs.getArray("changed_field_codes");
            String issue = rs.getString("issue_kind");
            return new SyncDiff(EntityKind.valueOf(rs.getString("entity_kind")), rs.getString("subject_key"),
                    ChangeKind.valueOf(rs.getString("change_kind")),
                    fields == null ? List.<String>of() : List.of((String[]) fields.getArray()),
                    issue == null ? null : IssueKind.valueOf(issue), rs.getString("detail"));
        }).list();
    }

    @Override
    public Optional<Instant> findLastSucceededAt() {
        List<OffsetDateTime> latest = jdbc.sql("""
                select max(finished_at) from organization_workforce.sync_runs where status = 'SUCCEEDED'
                """)
                .query((rs, n) -> rs.getObject(1, OffsetDateTime.class)).list();
        return latest.stream().filter(Objects::nonNull).findFirst().map(OffsetDateTime::toInstant);
    }

    @Override
    public List<SyncRunRecord> findHeld() {
        return jdbc.sql(SyncRunRows.SELECT + """
                 where status = 'HELD' order by started_at, id
                """).query((rs, n) -> SyncRunRows.record(rs)).list();
    }

    @Override
    public int countHeld() {
        return jdbc.sql("""
                select count(*) from organization_workforce.sync_runs where status = 'HELD'
                """).query(Integer.class).single();
    }

    @Override
    public Optional<Integer> findLatestEmployedWithoutWecomCount() {
        return jdbc.sql("""
                select employed_without_wecom_count from organization_workforce.sync_runs
                 where status = 'SUCCEEDED' order by started_at desc, id desc limit 1
                """).query(Integer.class).optional();
    }
}
