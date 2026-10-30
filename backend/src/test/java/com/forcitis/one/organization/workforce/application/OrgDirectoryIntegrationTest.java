package com.forcitis.one.organization.workforce.application;

import java.util.UUID;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.organization.workforce.domain.Fixtures.sourceUnit;

import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unitId;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
import com.forcitis.one.platform.audit.api.AuditEntry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;

class OrgDirectoryIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    OrgDirectory directory;

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ApplicationEvents events;

    private void synced() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L),
                        new SourceOrgUnit(sourceUnit(4), "C4", "Stopped", sourceUnit(1), 4, SourceRecordState.DISABLED, List.of(), null, null, false, null)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        sync.execute(SyncTrigger.SCHEDULED, false);
    }

    @Test
    @DisplayName("RULE-322/324 an employee is found by the exact WeCom user id from the snapshot, without asking the source")
    void findsByWecomUserFromTheSnapshot() {
        synced();

        Optional<EmployeeProfile> found = directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-2")));

        assertThat(found).isPresent();
        assertThat(found.get().employeeId()).isEqualTo(user(2));
        assertThat(found.get().orgUnitId()).isEqualTo(unitId(3));
        assertThat(found.get().wecomUserId()).isEqualTo(new WecomUserId("wx-2"));
        assertThat(found.get().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(orgSource.singleLookups.get()).isZero();
        assertThat(directory.findEmployee(user(2))).isPresent();
        assertThat(directory.findEmployee(user(999))).isEmpty();
    }

    @Autowired
    RegisterEmployeeOnLogin registerOnLogin;

    @Test
    @DisplayName("login miss: two first logins of the same person at once register them once - one row and one EmployeeRegistered")
    void concurrentFirstLoginsRegisterOnce() throws Exception {
        synced();
        orgSource.singleLookupResult = List.of(person(3001, 2, 100L, 1L, "13900003001", "wx-new-hire"));
        int callers = 6;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(callers);
        List<java.util.concurrent.Future<Optional<?>>> results = new java.util.ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                try (com.forcitis.one.kernel.WorkContext.Scope acting = com.forcitis.one.kernel.WorkContext.enter(java.util.UUID.randomUUID(), null, "test.login.race")) {
                    start.await();
                    return registerOnLogin.execute(new WecomUserId("wx-new-hire"));
                }
            }));
        }
        start.countDown();
        for (var result : results) {
            assertThat(result.get(30, java.util.concurrent.TimeUnit.SECONDS)).isPresent();
        }
        pool.shutdown();

        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where id = " + sqlEmployee(3001)).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select event_count from organization_workforce.employees where id = " + sqlEmployee(3001)).query(Long.class).single())
                .as("the employee announced EmployeeRegistered once").isEqualTo(1);
    }

    private void notYetInPost(long person) {
        java.time.LocalDate start = java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC).plusDays(30);
        jdbc.sql("""
                insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                  employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())
                """).param(TestAccounts.employee(person).value()).param(start).param(start).update();
    }

    @Test
    @DisplayName("#958 login miss: a known employee not in post by versions (not yet assigned, separated by command) is not registered again from a source that still lists them, by WeCom user id or login name")
    void aKnownEmployeeNotInPostByVersionsIsNotRegisteredAgain() {
        synced();
        notYetInPost(2);
        orgSource.singleLookupResult = List.of(person(2, 3, 101L, 1L, "13900000002", "wx-2"));
        int assignmentVersions = count("assignment_versions");
        String loginName = jdbc.sql("select login_name from organization_workforce.employees where id = " + sqlEmployee(2)).query(String.class).single();

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-2")))).isEmpty();
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName(loginName))).isEmpty();

        assertThat(orgSource.singleLookups.get()).as("the source is not asked").isZero();
        assertThat(count("assignment_versions")).as("nothing is written").isEqualTo(assignmentVersions);
    }

    @Test
    @DisplayName("#958 login miss: the source gives a known employee not in post by versions another key than the stored one: still not registered again, not admitted as employed")
    void aKnownEmployeeUnderAChangedKeyIsNotRegisteredAgain() {
        synced();
        notYetInPost(2);
        jdbc.sql("update organization_workforce.employees set wecom_user_id = 'wx-2-old', login_name = 'old-login' where id = " + sqlEmployee(2)).update();
        orgSource.singleLookupResult = List.of(person(2, 3, 101L, 1L, "13900000002", "wx-2"));
        long registered = events.stream(EmployeeRegistered.class).count();

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-2")))).isEmpty();

        assertThat(events.stream(EmployeeRegistered.class).count()).as("no second EmployeeRegistered").isEqualTo(registered);
    }

    @Test
    @DisplayName("login miss: an unknown WeCom user id asks the same the source view for this one person, registers them and audits it")
    void loginMissRegistersTheNewHire() {
        synced();
        orgSource.singleLookupResult = List.of(person(3001, 2, 100L, 1L, "13900003001", "wx-new-hire"));

        Optional<EmployeeProfile> found = directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-new-hire")));

        assertThat(found).isPresent();
        assertThat(found.get().employeeId()).isEqualTo(user(3001));
        assertThat(found.get().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(count("employees")).isEqualTo(4);
        assertThat(jdbc.sql("select reports_to_employee_id from organization_workforce.assignments where employee_id = " + sqlEmployee(3001)).query(UUID.class).single()).isEqualTo(TestAccounts.employee(1).value());
        assertThat(events.stream(EmployeeRegistered.class).filter(e -> e.envelope().aggregateType().equals("Employee")
                && e.envelope().aggregateId().equals(TestAccounts.employee(3001).value()) && e.envelope().aggregateVersion() == 1)).hasSize(1);

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-new-hire")))).isPresent();
        assertThat(orgSource.singleLookups.get()).as("the second login is answered by the snapshot").isEqualTo(1);
    }

    @Test
    @DisplayName("login miss: the same checks as the sync - unknown org unit or position, ambiguity and a down the source all answer 'unknown'")
    void loginMissFailsClosed() {
        synced();

        orgSource.singleLookupResult = List.of(person(3002, 99, 100L, null, "13900003002", "wx-no-unit"));
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-no-unit")))).isEmpty();

        orgSource.singleLookupResult = List.of(person(3003, 2, 555L, null, "13900003003", "wx-no-post"));
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-no-post")))).isEmpty();

        orgSource.singleLookupResult = List.of(person(3004, 2, 100L, null, "13900003004", "wx-twice"), person(3005, 2, 100L, null, "13900003005", "wx-twice"));
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-twice")))).isEmpty();

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-nobody")))).isEmpty();

        orgSource.singleLookupResult = List.of(person(3006, 2, 100L, null, "13900003006", "wx-while-down"));
        orgSource.unavailable = true;
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-while-down")))).isEmpty();

        assertThat(count("employees")).isEqualTo(3);
        verify(audit, never()).record(argThat((AuditEntry e) -> e.action().contains("registered-on-login")));
    }

    @Test
    @DisplayName("login name: an employed person is found in the snapshot without asking the source")
    void loginNameIsAnsweredBySnapshot() {
        synced();

        Optional<EmployeeProfile> found = directory.findEmployeeByLogin(new LoginLookup.ByLoginName("1392"));

        assertThat(found).isPresent();
        assertThat(found.get().employeeId()).isEqualTo(user(2));
        assertThat(found.get().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(orgSource.singleLookups.get()).isZero();
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("  "))).isEmpty();
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName(null))).isEmpty();
    }

    @Test
    @DisplayName("login miss by login name: an unknown login name asks the source for this one person, registers them and audits it - like the WeCom lookup")
    void loginNameMissRegistersTheNewHire() {
        synced();
        orgSource.singleLookupResult = List.of(person(3101, 2, 100L, 1L, "13900003101", "wx-by-login"));

        Optional<EmployeeProfile> found = directory.findEmployeeByLogin(new LoginLookup.ByLoginName("13900003101"));

        assertThat(found).isPresent();
        assertThat(found.get().employeeId()).isEqualTo(user(3101));
        assertThat(found.get().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(count("employees")).isEqualTo(4);
        assertThat(events.stream(EmployeeRegistered.class).filter(e -> e.envelope().aggregateType().equals("Employee")
                && e.envelope().aggregateId().equals(TestAccounts.employee(3101).value()) && e.envelope().aggregateVersion() == 1)).hasSize(1);
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("13900003101"))).isPresent();
        assertThat(orgSource.singleLookups.get()).as("the second lookup is answered by the snapshot").isEqualTo(1);
    }

    @Test
    @DisplayName("login miss by login name: the same checks as the sync - unknown org unit or position, ambiguity and a down source all answer 'unknown'")
    void loginNameMissFailsClosed() {
        synced();

        orgSource.singleLookupResult = List.of(person(3102, 99, 100L, null, "13900003102", "wx-a"));
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("13900003102"))).isEmpty();

        orgSource.singleLookupResult = List.of(person(3103, 2, 555L, null, "13900003103", "wx-b"));
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("13900003103"))).isEmpty();

        orgSource.singleLookupResult = List.of(person(3104, 2, 100L, null, "same-login", "wx-c"), person(3105, 2, 100L, null, "same-login", "wx-d"));
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("same-login"))).isEmpty();

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("nobody"))).isEmpty();

        orgSource.singleLookupResult = List.of(person(3106, 2, 100L, null, "13900003106", "wx-e"));
        orgSource.unavailable = true;
        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByLoginName("13900003106"))).isEmpty();

        assertThat(count("employees")).isEqualTo(3);
        verify(audit, never()).record(argThat((AuditEntry e) -> e.action().contains("registered-on-login")));
    }

    @Test
    @DisplayName("login miss: a person without a login name is registered as separated, so the login check refuses them")
    void loginMissRegistersALeaverAsSeparated() {
        synced();
        orgSource.singleLookupResult = List.of(person(3007, 2, 100L, null, null, "wx-leaver"));

        Optional<EmployeeProfile> found = directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-leaver")));

        assertThat(found).isPresent();
        assertThat(found.get().employmentStatus()).isEqualTo(EmploymentStatus.SEPARATED);
    }

    @Test
    @DisplayName("login miss: a leader who is unknown or not employed is not stored (RULE-372)")
    void loginMissValidatesTheSupervisor() {
        synced();
        orgSource.singleLookupResult = List.of(person(3008, 2, 100L, 99999L, "13900003008", "wx-bad-leader"));

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-bad-leader")))).isPresent();

        assertThat(jdbc.sql("select reports_to_employee_id from organization_workforce.assignments where employee_id = " + sqlEmployee(3008)).query(UUID.class).optional()).isEmpty();
        assertThat(directory.findSupervisor(user(3008))).isEmpty();
    }

    @Test
    @DisplayName("RULE-372 the direct leader is the person-level leader; empty when none or not employed")
    void supervisor() {
        synced();

        assertThat(directory.findSupervisor(user(3))).contains(user(2));
        assertThat(directory.findSupervisor(user(2))).contains(user(1));
        assertThat(directory.findSupervisor(user(1))).isEmpty();

        jdbc.sql("update organization_workforce.employees set employment_status = 'SEPARATED' where id = " + sqlEmployee(2)).update();
        assertThat(directory.findSupervisor(user(3))).as("leader separated since the last sync").isEmpty();
        assertThat(directory.findSupervisor(user(2))).as("the person themselves is not employed").isEmpty();
    }

    @Test
    @DisplayName("RULE-356 org unit head and regional receivables manager come from the source; empty when none")
    void headAndManager() {
        synced();

        assertThat(directory.findOrgUnitHead(unitId(2))).contains(user(1));
        assertThat(directory.findRegionalReceivablesManager(unitId(2))).contains(user(2));
        assertThat(directory.findOrgUnitHead(unitId(3))).isEmpty();
        assertThat(directory.findRegionalReceivablesManager(unitId(3))).isEmpty();
        assertThat(directory.findOrgUnitHead(unitId(404))).isEmpty();
    }

    @Test
    @DisplayName("RULE-355 an org unit profile is active only while the source keeps it enabled; disabled and revoked are not active")
    void orgUnitProfileActiveFlag() {
        synced();

        assertThat(directory.findOrgUnit(unitId(2))).get().satisfies(u -> {
            assertThat(u.active()).isTrue();
            assertThat(u.parentOrgUnitId()).isEqualTo(unitId(1));
            assertThat(u.name()).isEqualTo("Unit 2");
        });
        assertThat(directory.findOrgUnit(unitId(4))).get().satisfies(u -> assertThat(u.active()).isFalse());

        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        sync.execute(SyncTrigger.SCHEDULED, false);
        assertThat(jdbc.sql("select status from organization_workforce.org_units where id = " + sqlOrgUnit(4)).query(String.class).single()).isEqualTo("REVOKED");
        assertThat(directory.findOrgUnit(unitId(4))).get().satisfies(u -> assertThat(u.active()).isFalse());
        assertThat(directory.findOrgUnit(unitId(404))).isEmpty();
    }

    @Test
    @DisplayName("RULE-360 ancestors come nearest first and exclude the unit itself")
    void ancestorsNearestFirst() {
        synced();

        assertThat(directory.findAncestors(unitId(3))).containsExactly(unitId(2), unitId(1));
        assertThat(directory.findAncestors(unitId(2))).containsExactly(unitId(1));
        assertThat(directory.findAncestors(unitId(1))).isEmpty();
        assertThat(directory.findAncestors(unitId(404))).isEmpty();
    }
}
