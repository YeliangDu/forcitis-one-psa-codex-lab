package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000", "forcitis.organization.workforce.sync-purge.enabled=true"})
@Testcontainers
class SyncPurgeJobIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    SyncPurgeJob job;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void empty() {
        jdbc.sql("truncate table organization_workforce.sync_diffs, organization_workforce.sync_runs restart identity cascade").update();
    }

    private long run(String status, String startedAgo, String releasedAgo) {
        return jdbc.sql("""
                insert into organization_workforce.sync_runs (trigger_kind, status, started_at, released_at)
                values ('SCHEDULED', :status, now() - cast(:started as interval), case when cast(:released as text) is null then null else now() - cast(:released as interval) end)
                returning id
                """).param("status", status).param("started", startedAgo).param("released", releasedAgo, java.sql.Types.VARCHAR).query(Long.class).single();
    }

    private void diff(long runId) {
        jdbc.sql("insert into organization_workforce.sync_diffs (sync_run_id, entity_kind, subject_key, change_kind) values (:run, 'EMPLOYEE', '1', 'CREATED')")
                .param("run", runId).update();
    }

    private boolean runExists(long id) {
        return jdbc.sql("select count(*) from organization_workforce.sync_runs where id = :id").param("id", id).query(Long.class).single() == 1;
    }

    private long diffsOf(long id) {
        return jdbc.sql("select count(*) from organization_workforce.sync_diffs where sync_run_id = :id").param("id", id).query(Long.class).single();
    }

    @Test
    @DisplayName("K7 diffs go after one year and runs after two; the run keeps its row while its diffs are already gone")
    void diffsAndRunsHaveTheirOwnKeepPeriod() {
        long recent = run("SUCCEEDED", "30 days", null);
        long middle = run("SUCCEEDED", "400 days", null);
        long old = run("FAILED", "800 days", null);
        for (long id : new long[] {recent, middle, old}) {
            diff(id);
        }

        job.purge();

        assertThat(runExists(recent)).isTrue();
        assertThat(diffsOf(recent)).isEqualTo(1);
        assertThat(runExists(middle)).isTrue();
        assertThat(diffsOf(middle)).isZero();
        assertThat(runExists(old)).isFalse();
    }

    @Test
    @DisplayName("K7 a held run is kept until it is released, then one more keep period counted from the release; a running one is never removed")
    void heldRunsAreCountedFromTheirRelease() {
        long heldLong = run("HELD", "900 days", null);
        long running = run("RUNNING", "900 days", null);
        long releasedLongAgo = run("RELEASED", "900 days", "800 days");
        long releasedRecently = run("RELEASED", "900 days", "100 days");
        diff(heldLong);
        diff(releasedRecently);

        job.purge();

        assertThat(runExists(heldLong)).isTrue();
        assertThat(diffsOf(heldLong)).isEqualTo(1);
        assertThat(runExists(running)).isTrue();
        assertThat(runExists(releasedLongAgo)).isFalse();
        assertThat(runExists(releasedRecently)).isTrue();
        assertThat(diffsOf(releasedRecently)).isEqualTo(1);
    }
}
