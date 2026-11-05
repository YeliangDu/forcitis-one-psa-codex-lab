package com.forcitis.one.organization.workforce.application;

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

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrgDirectoryIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    OrgDirectory directory;

    @Autowired
    AttributionSubjects attributionSubjects;

    private void synced() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L),
                        new SourceOrgUnit(sourceUnit(4), "C4", "Stopped", sourceUnit(1), 4, SourceRecordState.DISABLED, List.of(), null, null, false, null)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
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

    private void notYetInPost(long person) {
        java.time.LocalDate start = java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC).plusDays(30);
        jdbc.sql("""
                insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id, employee_id, effective_from_date, revision_number, employment_status,
                  employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())
                """).param(EmploymentRelationshipSeed.relationship(jdbc, TestAccounts.employee(person).value())).param(TestAccounts.employee(person).value()).param(start).param(start).update();
    }

    @Test
    @DisplayName("#958 an employee who is not in post by versions still exists for legal-entity attribution (existence is the root row, not the assignment in force)")
    void anEmployeeNotInPostByVersionsStillExistsForAttribution() {
        synced();
        notYetInPost(2);
        assertThat(directory.findEmployee(user(2))).isEmpty();

        assertThat(attributionSubjects.require(com.forcitis.one.organization.workforce.api.AttributionKind.EMPLOYEE, TestAccounts.employee(2).value())).isNotNull();
    }

    @Test
    @DisplayName("ADR-0069 s9 a login lookup never creates an employee: an unknown WeCom user id answers empty, the source is not asked and no row is written")
    void anUnknownWecomUserIsNotRegistered() {
        synced();
        orgSource.singleLookupResult = List.of(person(3001, 2, 100L, 1L, "13900003001", "wx-new-hire"));
        int employees = count("employees");
        int assignmentVersions = count("assignment_versions");

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-new-hire")))).isEmpty();

        assertThat(orgSource.singleLookups.get()).isZero();
        assertThat(count("employees")).isEqualTo(employees);
        assertThat(count("assignment_versions")).isEqualTo(assignmentVersions);
    }

    @Test
    @DisplayName("#958 a known employee not in post by versions is not found by WeCom user id and nothing is written")
    void aKnownEmployeeNotInPostByVersionsIsNotFound() {
        synced();
        notYetInPost(2);
        int assignmentVersions = count("assignment_versions");

        assertThat(directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx-2")))).isEmpty();

        assertThat(count("assignment_versions")).isEqualTo(assignmentVersions);
    }

    @Test
    @DisplayName("ADR-0069 s7 employees keeps no login_name: the column is gone, so nothing can keep a second identity for a person")
    void employeesHasNoLoginNameColumn() {
        assertThat(jdbc.sql("select count(*) from information_schema.columns where table_schema = 'organization_workforce' and table_name = 'employees' and column_name = 'login_name'")
                .query(Integer.class).single()).isZero();
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

        // Since #934 nothing revokes a unit the source dropped; the row is put in the revoked state directly to check the profile.
        jdbc.sql("update organization_workforce.org_units set status = 'REVOKED', revoked_at = now() where id = " + sqlOrgUnit(4)).update();
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
