package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.ChangeKind;
import com.forcitis.one.organization.workforce.domain.EntityKind;
import com.forcitis.one.organization.workforce.domain.IssueKind;
import com.forcitis.one.organization.workforce.domain.SyncDiff;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import com.forcitis.one.organization.workforce.domain.SyncRunRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        return jdbc.sql(SyncRunRows.SELECT + " where id = :id").param("id", syncRunId.value())
                .query((rs, n) -> SyncRunRows.record(rs)).optional();
    }

    @Override
    public List<SyncRunRecord> findRecent(int limit) {
        return jdbc.sql(SyncRunRows.SELECT + " order by started_at desc, id desc limit :limit").param("limit", limit)
                .query((rs, n) -> SyncRunRows.record(rs)).list();
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
    public Optional<Instant> findLastSucceededAt() {
        List<Timestamp> latest = jdbc.sql("select max(finished_at) from organization_workforce.sync_runs where status = 'SUCCEEDED'")
                .query((rs, n) -> rs.getTimestamp(1)).list();
        return latest.stream().filter(Objects::nonNull).findFirst().map(Timestamp::toInstant);
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
}
