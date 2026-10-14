package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import static com.forcitis.one.organization.workforce.domain.Fixtures.sourceUnit;

import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unitId;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.organization.workforce.events.SyncRunReleased;
import com.forcitis.one.platform.audit.api.AuditEntry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.forcitis.one.kernel.WorkContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;

class OrgSnapshotSyncIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ReleaseSyncRun release;

    @Autowired
    RefreshShortNames refreshShortNames;

    @Autowired
    MeterRegistry registry;

    @Autowired
    ApplicationEvents events;

    private static List<SourcePerson> people(int n) {
        List<SourcePerson> people = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            people.add(person(i, 2, 100L, i == 1 ? null : 1L));
        }
        return people;
    }

    @Test
    @DisplayName("RULE-357/373 the first run builds the snapshot in one transaction, logs run and differences, announces the change")
    void firstRunBuildsTheSnapshot() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)), List.of(head(2, 1)),
                List.of(post(100), post(101)), people(3));

        SyncResult result = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(result.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(count("org_units")).isEqualTo(3);
        assertThat(count("positions")).isEqualTo(2);
        assertThat(count("employees")).isEqualTo(3);
        assertThat(count("assignments")).isEqualTo(3);
        assertThat(jdbc.sql("select status || ':' || org_unit_change_count || ':' || employee_change_count || ':' || incoming_employed_count from organization_workforce.sync_runs")
                .query(String.class).single()).isEqualTo("SUCCEEDED:3:3:3");
        assertThat(count("sync_diffs")).isEqualTo(8);
        assertThat(jdbc.sql("select head_employee_id from organization_workforce.org_units where id = 2").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("select reports_to_employee_id from organization_workforce.assignments where employee_id = 3").query(Long.class).single()).isEqualTo(1L);
        assertThat(events.stream(OrgSnapshotChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.changedEmployees()).containsExactlyInAnyOrder(user(1), user(2), user(3));
            assertThat(e.changedOrgUnits()).containsExactlyInAnyOrder(unitId(1), unitId(2), unitId(3));
            assertThat(e.separatedEmployees()).isEmpty();
        });
    }

    @Test
    @DisplayName("RULE-357 a run without changes writes no rows and publishes no event")
    void unchangedSourceChangesNothing() {
        orgSource.snapshot = tree(person(1, 2, 100L, null));
        sync.execute(SyncTrigger.SCHEDULED, false);

        SyncResult second = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(second.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(count("sync_runs")).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from organization_workforce.sync_diffs where sync_run_id = ?").param(second.syncRunId().value())
                .query(Integer.class).single()).isZero();
        assertThat(events.stream(OrgSnapshotChanged.class)).hasSize(1);
    }

    @Test
    @DisplayName("RULE-363/366 a leaver is separated once, the event names them, and re-running never repeats it")
    void leaverIsSeparatedOnce() {
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null), person(3, 2, 100L, null), person(4, 2, 100L, null),
                person(5, 2, 100L, null), person(6, 2, 100L, null), person(7, 2, 100L, null), person(8, 2, 100L, null),
                person(9, 2, 100L, null), person(10, 2, 100L, null));
        sync.execute(SyncTrigger.SCHEDULED, false);

        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 2, 100L, null), person(3, 2, 100L, null), person(4, 2, 100L, null),
                person(5, 2, 100L, null), person(6, 2, 100L, null), person(7, 2, 100L, null), person(8, 2, 100L, null),
                person(9, 2, 100L, null), person(10, 2, 100L, null, null, "wx-10"));
        sync.execute(SyncTrigger.SCHEDULED, false);
        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(jdbc.sql("select employment_status from organization_workforce.employees where id = 10").query(String.class).single())
                .isEqualTo("SEPARATED");
        assertThat(jdbc.sql("select separated_at is not null from organization_workforce.employees where id = 10").query(Boolean.class).single()).isTrue();
        assertThat(events.stream(OrgSnapshotChanged.class).filter(e -> !e.separatedEmployees().isEmpty()))
                .singleElement().satisfies(e -> assertThat(e.separatedEmployees()).containsExactly(user(10)));
    }

    @Test
    @DisplayName("a run that was killed mid-way is closed as failed by the next run, a recent one is left alone")
    void interruptedRunsAreClosed() {
        jdbc.sql("insert into organization_workforce.sync_runs (trigger_kind, status, started_at) values ('SCHEDULED', 'RUNNING', now() - interval '1 hour')").update();
        jdbc.sql("insert into organization_workforce.sync_runs (trigger_kind, status, started_at) values ('SCHEDULED', 'RUNNING', now() - interval '1 minute')").update();
        orgSource.snapshot = tree(person(1, 2, 100L, null));

        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(jdbc.sql("select status || ':' || coalesce(failure_reason, '') from organization_workforce.sync_runs order by id").query(String.class).list())
                .containsExactly("FAILED:interrupted: the run never finished", "RUNNING:", "SUCCEEDED:");
    }

    @Test
    @DisplayName("RULE-354 a broken org tree fails the run, writes nothing and publishes nothing")
    void brokenTreeLeavesTheSnapshotAlone() {
        orgSource.snapshot = tree(person(1, 2, 100L, null));
        sync.execute(SyncTrigger.SCHEDULED, false);
        long before = events.stream(OrgSnapshotChanged.class).count();

        orgSource.failure = new com.forcitis.one.kernel.ExternalSourceBrokenException("org unit 9 has no parent in the organisation tree");
        SyncResult result = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(result.status()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(count("org_units")).isEqualTo(3);
        assertThat(jdbc.sql("select failure_reason from organization_workforce.sync_runs order by id desc limit 1").query(String.class).single())
                .contains("ExternalSourceBrokenException");
        assertThat(events.stream(OrgSnapshotChanged.class).count()).isEqualTo(before);
        assertThat(jdbc.sql("select status from organization_workforce.org_units where id = 2").query(String.class).single()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("an unreachable the source fails the run, records why and keeps the snapshot; the next run recovers")
    void unavailableSourceFailsTheRun() {
        orgSource.snapshot = tree(person(1, 2, 100L, null));
        sync.execute(SyncTrigger.SCHEDULED, false);

        orgSource.unavailable = true;
        SyncResult down = sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.unavailable = false;
        SyncResult up = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(down.status()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(down.note()).contains("ExternalSystemUnavailableException");
        assertThat(count("employees")).isEqualTo(1);
        assertThat(up.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(registry.get("forcitis.organization.workforce.sync.runs").tag("status", "FAILED").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("RULE-358/366 shrink guard: a drop over 20 % holds the run, nothing is written, one held row is refreshed, alerts can fire")
    void shrinkHoldsTheRun() {
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);

        orgSource.snapshot = tree(people(7).toArray(SourcePerson[]::new));
        SyncResult held = sync.execute(SyncTrigger.SCHEDULED, false);
        SyncResult heldAgain = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(held.status()).isEqualTo(SyncRunStatus.HELD);
        assertThat(held.note()).contains("employed people 10 -> 7");
        assertThat(heldAgain.syncRunId()).as("the stuck hold is refreshed, not duplicated").isEqualTo(held.syncRunId());
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where employment_status = 'EMPLOYED'").query(Integer.class).single()).isEqualTo(10);
        assertThat(count("sync_runs")).isEqualTo(2);
        assertThat(registry.get("forcitis.organization.workforce.sync.held.runs").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("forcitis.organization.workforce.sync.runs").tag("status", "HELD").counter().count()).isGreaterThanOrEqualTo(2);
        assertThat(registry.get("forcitis.organization.workforce.sync.lag.seconds").gauge().value()).isBetween(0.0, 120.0);
        assertThat(events.stream(OrgSnapshotChanged.class)).hasSize(1);
    }

    @Test
    @DisplayName("RULE-358 an administrator releases the held run: the source is read afresh, the guard is bypassed, the actor is audited")
    void releaseAppliesTheHeldChange() {
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(people(7).toArray(SourcePerson[]::new));
        SyncResult held = sync.execute(SyncTrigger.SCHEDULED, false);

        ReleaseResult released = release.execute(held.syncRunId().value(), 9001L, "source repaired by hand", true, false);

        assertThat(released).isInstanceOf(ReleaseResult.Released.class);
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where employment_status = 'SEPARATED'").query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("select status || ':' || released_by from organization_workforce.sync_runs where id = ?").param(held.syncRunId().value())
                .query(String.class).single()).isEqualTo("RELEASED:9001");
        assertThat(events.stream(SyncRunReleased.class)).singleElement().satisfies(e -> {
            assertThat(e.syncRunId()).isEqualTo(held.syncRunId());
            assertThat(e.envelope().aggregateId()).isEqualTo(String.valueOf(held.syncRunId().value()));
        });
        assertThat(events.stream(OrgSnapshotChanged.class).filter(e -> e.separatedEmployees().size() == 3)).hasSize(1);
        assertThat(registry.get("forcitis.organization.workforce.sync.held.runs").gauge().value()).isEqualTo(0.0);

        assertThat(release.execute(held.syncRunId().value(), 9001L, "source repaired by hand", true, false)).isInstanceOf(ReleaseResult.NotHeld.class);
        assertThat(release.execute(424242L, 9001L, "source repaired by hand", true, false)).isInstanceOf(ReleaseResult.NotFound.class);
    }

    @Test
    @DisplayName("a held run that the source itself repairs is closed by the next normal run, no administrator needed")
    void recoveredSourceClosesTheHold() {
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(people(7).toArray(SourcePerson[]::new));
        SyncResult held = sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));

        SyncResult recovered = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(recovered.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(jdbc.sql("select status from organization_workforce.sync_runs where id = ?").param(held.syncRunId().value()).query(String.class).single())
                .isEqualTo("RELEASED");
        assertThat(registry.get("forcitis.organization.workforce.sync.held.runs").gauge().value()).isEqualTo(0.0);
        // identity reacts to earlier events asynchronously; what must not appear is a workforce audit entry
        verify(audit, never()).record(argThat((AuditEntry e) -> e.action().startsWith("organization.workforce")));
    }

    @Test
    @DisplayName("RULE-365 an isolated row is reported in the run, the rest of the batch is written")
    void isolatedRowDoesNotStopTheBatch() {
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 99, 100L, null));

        SyncResult result = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(result.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(count("employees")).isEqualTo(1);
        assertThat(jdbc.sql("select isolated_row_count from organization_workforce.sync_runs").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select issue_kind from organization_workforce.sync_diffs where change_kind = 'ISSUE'").query(String.class).single())
                .isEqualTo("ISOLATED_MISSING_ORG_UNIT");
    }

    @Test
    @DisplayName("RULE-374 the number of employed people without a WeCom user id is recorded and exposed as a gauge")
    void dataQualityCountIsOutput() {
        orgSource.snapshot = tree(person(1, 2, 100L, null, "13900000001", null), person(2, 2, 100L, null));

        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(jdbc.sql("select employed_without_wecom_count from organization_workforce.sync_runs").query(Integer.class).single()).isEqualTo(1);
        assertThat(registry.get("forcitis.organization.workforce.employees.without.wecom").gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("two runs at the same time never double-write: the writer lock serialises them")
    void concurrentRunsDoNotDuplicate() throws Exception {
        orgSource.snapshot = tree(people(20).toArray(SourcePerson[]::new));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<SyncResult> run = () -> {
                try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.test")) {
                    return sync.execute(SyncTrigger.SCHEDULED, false);
                }
            };
            Future<SyncResult> a = pool.submit(run);
            Future<SyncResult> b = pool.submit(run);

            assertThat(a.get().status()).isEqualTo(SyncRunStatus.SUCCEEDED);
            assertThat(b.get().status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("employees")).isEqualTo(20);
        assertThat(jdbc.sql("select count(*) from organization_workforce.sync_diffs where entity_kind = 'EMPLOYEE' and change_kind = 'CREATED'")
                .query(Integer.class).single()).isEqualTo(20);
    }

    @Test
    @DisplayName("the unique WeCom key never trips the write: a user id moving from a leaver to a new hire in one run")
    void wecomUserIdCanMoveBetweenPeopleInOneRun() {
        orgSource.snapshot = tree(person(1, 2, 100L, null, "13900000001", "wx-shared"), person(2, 2, 100L, null), person(3, 2, 100L, null),
                person(4, 2, 100L, null), person(5, 2, 100L, null));
        sync.execute(SyncTrigger.SCHEDULED, false);

        orgSource.snapshot = tree(person(1, 2, 100L, null, null, "wx-shared"), person(2, 2, 100L, null), person(3, 2, 100L, null),
                person(4, 2, 100L, null), person(5, 2, 100L, null), person(6, 2, 100L, null, "13900000006", "wx-shared"));
        SyncResult result = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(result.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(jdbc.sql("select id from organization_workforce.employees where wecom_user_id = 'wx-shared' and employment_status = 'EMPLOYED'")
                .query(Long.class).list()).containsExactly(6L);
    }

    @Test
    @DisplayName("short names: refreshed daily from the old system, only changed units are touched, the source sync never overwrites them")
    void shortNamesAreRefreshedAndKept() {
        orgSource.snapshot = tree();
        sync.execute(SyncTrigger.SCHEDULED, false);

        oldSystem.names = Map.of(unitId(1), "HQ", unitId(2), "Delivery", unitId(777), "Unknown unit");
        assertThat(refreshShortNames.execute()).isEqualTo(2);
        assertThat(refreshShortNames.execute()).isZero();

        orgSource.snapshot = source(List.of(unit(1, null), new SourceOrgUnit(sourceUnit(2), "C2", "Renamed in the source",
                sourceUnit(1), 2, SourceRecordState.ACTIVE, List.of(), null, null, false, null),
                unit(3, 2L)), List.of(), List.of(post(100), post(101)), List.of());
        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(jdbc.sql("select name || '/' || short_name from organization_workforce.org_units where id = 2").query(String.class).single())
                .isEqualTo("Renamed in the source/Delivery");

        oldSystem.unavailable = true;
        assertThat(refreshShortNames.execute()).isZero();
        assertThat(jdbc.sql("select short_name from organization_workforce.org_units where id = 1").query(String.class).single()).isEqualTo("HQ");

        oldSystem.unavailable = false;
        oldSystem.names = Map.of(unitId(1), "HQ", unitId(2), "");
        assertThat(refreshShortNames.execute()).isEqualTo(1);
    }
}
