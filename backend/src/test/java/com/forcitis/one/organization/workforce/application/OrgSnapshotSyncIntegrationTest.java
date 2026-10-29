package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.TestIds;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.organization.workforce.events.SyncRunReleased;
import com.forcitis.one.platform.audit.api.AuditEntry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.kernel.WorkContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import com.forcitis.one.kernel.event.AggregateReference;

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
    @DisplayName("INT-DDD-H2 同步应用快照的多聚合写已登记，事件 relatedAggregates 列出快照")
    void applyingTheSnapshotIsARegisteredMultiAggregateWrite() throws java.io.IOException {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1)), List.of(post(100)), people(2));

        SyncResult result = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(com.forcitis.one.fixture.doubles.AggregateWriteGuard.multiAggregateTransactions())
                .as("the guard sees the run and the snapshot written together").contains(java.util.Set.of("OrgSnapshot", "SyncRun"));
        assertThat(com.forcitis.one.fixture.doubles.AggregateWriteGuard.entries(java.nio.file.Files.readAllLines(java.nio.file.Path.of("architecture/multi-aggregate-exceptions.txt"))))
                .as("and that pair is registered").anyMatch(set -> set.containsAll(java.util.Set.of("OrgSnapshot", "SyncRun")));
        var snapshot = new AggregateReference("OrgSnapshot", result.syncRunId().value());
        assertThat(events.stream(com.forcitis.one.organization.workforce.events.SyncRunSucceeded.class)).singleElement()
                .satisfies(e -> assertThat(e.envelope().relatedAggregates()).containsExactly(snapshot));
        assertThat(events.stream(OrgSnapshotChanged.class)).singleElement().satisfies(e -> assertThat(e.envelope().relatedAggregates()).containsExactly(snapshot));
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
        assertThat(jdbc.sql("select head_employee_id from organization_workforce.org_units where id = " + sqlOrgUnit(2)).query(UUID.class).single()).isEqualTo(TestAccounts.employee(1).value());
        assertThat(jdbc.sql("select reports_to_employee_id from organization_workforce.assignments where employee_id = " + sqlEmployee(3)).query(UUID.class).single()).isEqualTo(TestAccounts.employee(1).value());
        assertThat(events.stream(OrgSnapshotChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.changedEmployees()).containsExactlyInAnyOrder(user(1), user(2), user(3));
            assertThat(e.changedOrgUnits()).containsExactlyInAnyOrder(unitId(1), unitId(2), unitId(3));
            assertThat(e.separatedEmployees()).isEmpty();
            assertThat(e.envelope().businessDate()).as("the snapshot cut-off day is the Shanghai day the run started")
                    .isEqualTo(runStartDay());
            assertThat(e.effectiveFrom()).isEqualTo(e.envelope().businessDate());
        });
    }

    private LocalDate runStartDay() {
        return jdbc.sql("select started_at from organization_workforce.sync_runs").query((rs, n) -> rs.getObject(1, OffsetDateTime.class))
                .single().atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate();
    }

    @Test
    @DisplayName("B2b-H2 a run that crosses Shanghai midnight dates its event and its history rows with the same day: the one the source was read on")
    void snapshotDayIsTheSameForTheEventAndTheHistoryRowsAcrossMidnight() {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        Instant nextMidnight = LocalDate.now(shanghai).plusDays(1).atStartOfDay(shanghai).toInstant();
        clock.advance(Duration.between(Instant.now(), nextMidnight.minusSeconds(60)));
        LocalDate readDay = LocalDate.ofInstant(clock.instant(), shanghai);
        orgSource.snapshot = tree(person(1, 2, 100L, null));
        orgSource.afterRead = () -> clock.advance(Duration.ofMinutes(5));

        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(LocalDate.ofInstant(clock.instant(), shanghai)).as("the run really crossed midnight").isEqualTo(readDay.plusDays(1));
        LocalDate orgUnitFrom = jdbc.sql("select min(effective_from_date) from organization_workforce.org_unit_versions").query(LocalDate.class).single();
        LocalDate assignmentFrom = jdbc.sql("select min(effective_from_date) from organization_workforce.assignment_versions").query(LocalDate.class).single();
        assertThat(events.stream(OrgSnapshotChanged.class)).singleElement().satisfies(e -> {
            assertThat(e.envelope().businessDate()).isEqualTo(readDay).isEqualTo(orgUnitFrom).isEqualTo(assignmentFrom);
            assertThat(e.effectiveFrom()).isEqualTo(readDay);
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

        assertThat(jdbc.sql("select employment_status from organization_workforce.employees where id = " + sqlEmployee(10)).query(String.class).single())
                .isEqualTo("SEPARATED");
        assertThat(jdbc.sql("select separated_at is not null from organization_workforce.employees where id = " + sqlEmployee(10)).query(Boolean.class).single()).isTrue();
        assertThat(events.stream(OrgSnapshotChanged.class).filter(e -> !e.separatedEmployees().isEmpty()))
                .singleElement().satisfies(e -> assertThat(e.separatedEmployees()).containsExactly(user(10)));
    }

    @Test
    @DisplayName("a run that was killed mid-way is closed as failed by the next run, a recent one is left alone")
    void interruptedRunsAreClosed() {
        UUID old = TestIds.uuid(1);
        UUID recent = TestIds.uuid(2);
        insertRunning(old, clock.instant().minus(java.time.Duration.ofHours(1)));
        insertRunning(recent, clock.instant().minus(java.time.Duration.ofMinutes(1)));
        orgSource.snapshot = tree(person(1, 2, 100L, null));

        SyncResult result = sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(stateOf(old)).isEqualTo("FAILED:interrupted: the run never finished");
        assertThat(stateOf(recent)).isEqualTo("RUNNING:");
        assertThat(stateOf(result.syncRunId().value())).isEqualTo("SUCCEEDED:");
    }

    private void insertRunning(UUID id, java.time.Instant startedAt) {
        jdbc.sql("insert into organization_workforce.sync_runs (id, trigger_kind, status, started_at) values (:id, 'SCHEDULED', 'RUNNING', :startedAt)")
                .param("id", id).param("startedAt", java.sql.Timestamp.from(startedAt)).update();
    }

    private String stateOf(UUID id) {
        return jdbc.sql("select status || ':' || coalesce(failure_reason, '') from organization_workforce.sync_runs where id = :id")
                .param("id", id).query(String.class).single();
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
        assertThat(jdbc.sql("select status from organization_workforce.org_units where id = " + sqlOrgUnit(2)).query(String.class).single()).isEqualTo("ACTIVE");
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
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01 a control character in the reason of a release is replaced by a space before it reaches the audit text")
    void releaseReasonLosesControlCharacters() {
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(people(7).toArray(SourcePerson[]::new));
        SyncResult held = sync.execute(SyncTrigger.SCHEDULED, false);

        releaseAsSyncRunExecutor(held.syncRunId(), "line one\nline\ttwo\rend");

        org.mockito.ArgumentCaptor<AuditEntry> entry = org.mockito.ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit, atLeastOnce()).record(entry.capture());
        assertThat(entry.getAllValues()).filteredOn(e -> e.action().equals("organization.workforce.sync-run.released") && held.syncRunId().value().equals(e.subject().id())).singleElement()
                .satisfies(released -> assertThat(released.detail()).startsWith("reason: line one line two end; ").doesNotContainPattern("\\p{Cntrl}"));
    }

    @Test
    @DisplayName("RULE-358 an administrator releases the held run: the source is read afresh, the guard is bypassed, the actor is audited")
    void releaseAppliesTheHeldChange() {
        orgSource.snapshot = tree(people(10).toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(people(7).toArray(SourcePerson[]::new));
        SyncResult held = sync.execute(SyncTrigger.SCHEDULED, false);

        ReleaseResult released = releaseAsSyncRunExecutor(held.syncRunId(), "source repaired by hand");

        assertThat(released).isInstanceOfSatisfying(ReleaseResult.Released.class, r -> assertThat(r.releasedRun().status()).isEqualTo("SUCCEEDED"));
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where employment_status = 'SEPARATED'").query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("select status || ':' || released_by from organization_workforce.sync_runs where id = ?").param(held.syncRunId().value())
                .query(String.class).single()).isEqualTo("RELEASED:" + TestAccounts.account(9001L).value());
        assertThat(events.stream(SyncRunReleased.class)).singleElement().satisfies(e -> {
            assertThat(e.syncRunId()).isEqualTo(held.syncRunId());
            assertThat(e.envelope().aggregateId()).isEqualTo(held.syncRunId().value());
        });
        assertThat(events.stream(OrgSnapshotChanged.class).filter(e -> e.separatedEmployees().size() == 3)).hasSize(1);
        assertThat(registry.get("forcitis.organization.workforce.sync.held.runs").gauge().value()).isEqualTo(0.0);

        assertThat(releaseAsSyncRunExecutor(held.syncRunId(), "source repaired by hand")).isInstanceOf(ReleaseResult.NotHeld.class);
        assertThat(releaseAsSyncRunExecutor(new SyncRunId(TestIds.uuid(424242)), "source repaired by hand")).isInstanceOf(ReleaseResult.NotFound.class);
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
        // the audit mock is shared and identity reacts to earlier events asynchronously: only entries about this test's own runs count
        java.util.Set<java.util.UUID> thisTest = java.util.Set.of(held.syncRunId().value(), recovered.syncRunId().value());
        verify(audit, never()).record(argThat((AuditEntry e) -> e.action().startsWith("organization.workforce") && thisTest.contains(e.subject().id())));
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
                try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "test.organization.workforce")) {
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
                .query(UUID.class).list()).containsExactly(TestAccounts.employee(6).value());
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

        assertThat(jdbc.sql("select name || '/' || short_name from organization_workforce.org_units where id = " + sqlOrgUnit(2)).query(String.class).single())
                .isEqualTo("Renamed in the source/Delivery");

        oldSystem.unavailable = true;
        double failuresBefore = registry.counter("forcitis.organization.workforce.short.name.refresh.failures").count();
        assertThat(refreshShortNames.execute()).isZero();
        assertThat(registry.counter("forcitis.organization.workforce.short.name.refresh.failures").count()).isEqualTo(failuresBefore + 1);
        assertThat(jdbc.sql("select short_name from organization_workforce.org_units where id = " + sqlOrgUnit(1)).query(String.class).single()).isEqualTo("HQ");

        oldSystem.unavailable = false;
        oldSystem.names = Map.of(unitId(1), "HQ", unitId(2), "");
        assertThat(refreshShortNames.execute()).isEqualTo(1);
    }

    /** The release reads the caller and its permissions from the session: account 9001 holding sync-run:execute only. */
    private ReleaseResult releaseAsSyncRunExecutor(SyncRunId held, String reason) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(TestAccounts.account(9001L).value().toString(), "x",
                ReleaseSyncRun.SYNC_RUN_EXECUTE));
        try {
            return release.execute(held, reason);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
