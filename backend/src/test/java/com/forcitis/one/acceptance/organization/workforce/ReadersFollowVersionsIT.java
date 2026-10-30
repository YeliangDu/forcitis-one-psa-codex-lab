package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.jayway.jsonpath.JsonPath;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #958 (W2 design section 3.4; ADR-0068 item 1; W2 section 0 "登记即写版本、按日期读"): the organisation directory reads employment,
 * fee-earner, org unit and reporting line from the employee and assignment versions in force on the business day, not from the sync-written
 * employees/assignments snapshot. A command that has taken effect is seen the same day; a future-dated one only from its date, with no
 * due-date job run; a withdrawn one never. A person with no employee version at all is still read from the snapshot until the initial
 * migration (#960).
 *
 * <p>Wherever the person has versions, the snapshot is left saying the old thing (or is emptied) so that a reader of the snapshot fails.
 * The due-date job is never called: W2 section 0 makes it no precondition of who is employed where.
 */
class ReadersFollowVersionsIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(ReadersFollowVersionsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private OrgDirectory directory;

    @Test
    @DisplayName("#958 W2 第 3.4 节：今天生效的调岗当天可见：部门、直属领导按任职版本取，按企业微信登录查人也一样；快照仍是旧部门、无领导")
    void aTransferEffectiveTodayIsSeenTheSameDay() throws Exception {
        UUID person = employed(REGULAR);
        UUID leader = employed(REGULAR);
        UUID other = newUnit();

        transfer(person, TODAY, other, leader);

        assertThat(profile(person).orgUnitId().value()).as("org unit of the day").isEqualTo(other);
        assertThat(directory.findSupervisor(employeeIdOf(person)).map(EmployeeId::value)).as("supervisor of the day").contains(leader);
        Optional<EmployeeProfile> byLogin = directory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId(wecomUserIdOf(person))));
        assertThat(byLogin).as("the login lookup reads the same versions").isPresent();
        assertThat(byLogin.get().orgUnitId().value()).isEqualTo(other);
        assertThat(snapshotUnit(person)).as("the snapshot was not what made it true").isEqualTo(unit);
    }

    @Test
    @DisplayName("#958 W2 第 0 节、第 3.4 节：未来日期的调岗到日前不可见、到日即可见，不跑生效日作业")
    void aFutureTransferIsSeenFromItsDateOnlyWithoutTheJob() throws Exception {
        UUID person = employed(REGULAR);
        UUID leader = employed(REGULAR);
        UUID other = newUnit();
        LocalDate due = TODAY.plusDays(5);

        transfer(person, due, other, leader);

        assertThat(profile(person).orgUnitId().value()).as("today").isEqualTo(unit);
        assertThat(directory.findSupervisor(employeeIdOf(person))).as("no supervisor before the date").isEmpty();
        setToday(due.minusDays(1));
        assertThat(profile(person).orgUnitId().value()).as("the day before").isEqualTo(unit);
        assertThat(directory.findSupervisor(employeeIdOf(person))).as("the day before").isEmpty();
        clock.set(due.minusDays(1).atTime(23, 30).atZone(BUSINESS_ZONE).toInstant());
        assertThat(profile(person).orgUnitId().value()).as("23:30 the day before in the business zone").isEqualTo(unit);
        clock.set(due.atTime(0, 30).atZone(BUSINESS_ZONE).toInstant());
        assertThat(profile(person).orgUnitId().value()).as("00:30 on the date in the business zone, still the day before in UTC").isEqualTo(other);
        setToday(due);
        assertThat(profile(person).orgUnitId().value()).as("on the date, job not run").isEqualTo(other);
        assertThat(directory.findSupervisor(employeeIdOf(person)).map(EmployeeId::value)).as("on the date, job not run").contains(leader);
    }

    @Test
    @DisplayName("#958 W2 第 2 节（撤回 = 作废未来版本）：撤回的未来调岗到日也不可见，读取只看未作废的行")
    void aWithdrawnFutureTransferIsNeverSeen() throws Exception {
        UUID person = employed(REGULAR);
        UUID other = newUnit();
        LocalDate due = TODAY.plusDays(5);
        UUID command = transfer(person, due, other, employed(REGULAR));

        commands().withdraw(command, "synthetic: transfer cancelled");

        setToday(due);
        assertThat(profile(person).orgUnitId().value()).isEqualTo(unit);
        assertThat(directory.findSupervisor(employeeIdOf(person))).isEmpty();
    }

    @Test
    @DisplayName("#958 W2 第 2 节、第 3.4 节：从过去某日起的更正当天可见：计费人员标记按员工版本、直属领导按任职版本，被作废的旧行不再算；快照仍为非计费、无领导")
    void aCorrectionIsSeenTheSameDay() throws Exception {
        UUID person = employed(REGULAR);
        UUID leader = employed(REGULAR);

        correct(person, TODAY.minusDays(10), leader, true);

        EmployeeProfile seen = profile(person);
        assertThat(seen.feeEarner()).as("fee-earner from the employee version of the day").isTrue();
        assertThat(seen.orgUnitId().value()).isEqualTo(unit);
        assertThat(directory.findSupervisor(employeeIdOf(person)).map(EmployeeId::value)).as("supervisor from the assignment version of the day").contains(leader);
        assertThat(snapshotFeeEarner(person)).as("the snapshot was not what made it true").isFalse();
    }

    @Test
    @DisplayName("#958 W2 第 3.4 节：转正当天之后仍按版本读出在职、部门与计费标记，即便快照已不再列出此人（快照任职行删除、状态写离职）")
    void aRegularizedPersonIsReadFromVersionsEvenWithoutTheSnapshot() throws Exception {
        UUID person = employed(PROBATION);
        correct(person, TODAY.minusDays(10), null, true);
        enter(person, REGULARIZE, TODAY);
        snapshotForgets(person);
        freshSnapshot();

        Optional<EmployeeProfile> seen = directory.findEmployee(employeeIdOf(person));

        assertThat(seen).as("a person with versions does not need the snapshot's assignment row").isPresent();
        assertThat(seen.get().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(seen.get().orgUnitId().value()).isEqualTo(unit);
        assertThat(seen.get().feeEarner()).isTrue();
        assertThat(directory.employmentOf(employeeIdOf(person))).as("the snapshot says separated and is current, the versions say employed")
                .isEqualTo(EmploymentVerdict.EMPLOYED);
    }

    @Test
    @DisplayName("#958 W2 第 0、1 节：离职生效当天：本人不再按在职读出，下属的直属领导查不到；快照仍写在职")
    void aSeparationEffectiveTodayIsSeenTheSameDay() throws Exception {
        UUID leader = employed(REGULAR);
        UUID subordinate = employed(REGULAR);
        correct(subordinate, TODAY.minusDays(10), leader, false);
        assertThat(directory.findSupervisor(employeeIdOf(subordinate)).map(EmployeeId::value)).as("before the separation").contains(leader);

        separate(leader, TODAY.minusDays(1));

        assertThat(directory.employmentOf(employeeIdOf(leader))).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(directory.findEmployee(employeeIdOf(leader)).map(EmployeeProfile::employmentStatus)).as("absent or not employed")
                .isNotEqualTo(Optional.of(EmploymentStatus.EMPLOYED));
        assertThat(directory.findSupervisor(employeeIdOf(subordinate))).as("a separated leader is nobody's supervisor").isEmpty();
        assertThat(rows("select employment_status from employees where id = ?", stored(leader)).get(0).get("employment_status"))
                .as("the snapshot still says employed").isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#958 W2 第 0 节：未来日期的离职：最后工作日仍是下属的直属领导，次日起不是，不跑生效日作业")
    void aFutureSeparationIsSeenFromTheDayAfterTheLastWorkingDay() throws Exception {
        UUID leader = employed(REGULAR);
        UUID subordinate = employed(REGULAR);
        correct(subordinate, TODAY.minusDays(10), leader, false);
        LocalDate lastWorkingDay = TODAY.plusDays(3);

        separate(leader, lastWorkingDay);

        setToday(lastWorkingDay);
        freshSnapshot();
        assertThat(directory.findSupervisor(employeeIdOf(subordinate)).map(EmployeeId::value)).as("on the last working day").contains(leader);
        assertThat(directory.employmentOf(employeeIdOf(leader))).as("on the last working day, snapshot current").isEqualTo(EmploymentVerdict.EMPLOYED);
        setToday(lastWorkingDay.plusDays(1));
        assertThat(directory.findSupervisor(employeeIdOf(subordinate))).as("the day after, job not run").isEmpty();
        assertThat(directory.employmentOf(employeeIdOf(leader))).isEqualTo(EmploymentVerdict.SEPARATED);
    }

    @Test
    @DisplayName("#958 完成标准（无版本者保持现状至 #960）：没有任何员工版本的人仍按快照读出部门、计费标记、直属领导与离职")
    void peopleWithoutVersionsKeepTodaysBehaviour() throws Exception {
        UUID leader = newEmployeeId();
        employee(leader, unit, null);
        UUID person = newEmployeeId();
        employee(person, unit, leader);
        setSnapshotFeeEarner(person, true);
        UUID gone = newEmployeeId();
        employee(gone, unit, null);
        snapshotSeparated(gone);

        EmployeeProfile seen = profile(person);
        assertThat(seen.orgUnitId().value()).isEqualTo(unit);
        assertThat(seen.feeEarner()).isTrue();
        assertThat(seen.employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(directory.findSupervisor(employeeIdOf(person)).map(EmployeeId::value)).contains(leader);
        assertThat(directory.employmentOf(employeeIdOf(gone))).isEqualTo(EmploymentVerdict.SEPARATED);
    }

    @Test
    @DisplayName("#958 W2 第 3.4 节：无版本的人（按快照）以有版本的领导为直属领导：领导按命令离职当天起不再是其直属领导")
    void aSnapshotPersonsLeaderWithVersionsIsJudgedByVersions() throws Exception {
        UUID leader = employed(REGULAR);
        UUID person = newEmployeeId();
        employee(person, unit, leader);
        assertThat(directory.findSupervisor(employeeIdOf(person)).map(EmployeeId::value)).as("before the separation").contains(leader);

        separate(leader, TODAY.minusDays(1));

        assertThat(directory.findSupervisor(employeeIdOf(person))).isEmpty();
    }

    @Test
    @DisplayName("#958 消费方（身份）：调岗当天，本人会话资料里的所属部门即新部门")
    void theSessionsOwnProfileShowsTheTransferredUnit() throws Exception {
        UUID person = employed(REGULAR);
        UUID other = newUnit();
        transfer(person, TODAY, other, null);

        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").with(session(signIn(person)))).andReturn().getResponse();

        assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
        assertThat(JsonPath.<String>read(current.getContentAsString(), "$.orgUnitId")).isEqualTo(other.toString());
    }

    @Test
    @DisplayName("#958 one-design 数据设计第 4 条（尚未任职）：有员工版本、当天没有任职版本的人，目录查不到，不回落到快照的部门")
    void aPersonWithAnEmployeeVersionButNoAssignmentVersionIsNotYetAssigned() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        migratedEmployeeVersion(person, SINCE);

        assertThat(directory.findEmployee(employeeIdOf(person))).isEmpty();
    }

    @Test
    @DisplayName("#958 one-design 数据设计第 4 条（按人：任意一条员工版本即按版本读）：快照列为在职、只有未来员工版本的人（提前登记），今天不按在职读出")
    void onlyFutureVersionsStillMeanReadFromVersions() throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        migratedEmployeeVersion(person, TODAY.plusDays(5));
        freshSnapshot();

        assertThat(directory.findEmployee(employeeIdOf(person))).isEmpty();
        assertThat(directory.employmentOf(employeeIdOf(person))).isNotEqualTo(EmploymentVerdict.EMPLOYED);
    }

    @Test
    @DisplayName("#958 one-design 数据设计第 4 条（无版本的领导按快照）：有版本者的直属领导只在快照里且快照写离职时，查不到直属领导")
    void aSnapshotLeaderSeparatedInTheSnapshotIsNobodysSupervisor() throws Exception {
        UUID leader = newEmployeeId();
        employee(leader, unit, null);
        UUID subordinate = employed(REGULAR);
        correct(subordinate, TODAY.minusDays(10), leader, false);
        assertThat(directory.findSupervisor(employeeIdOf(subordinate)).map(EmployeeId::value)).as("before").contains(leader);

        snapshotSeparated(leader);

        assertThat(directory.findSupervisor(employeeIdOf(subordinate))).isEmpty();
    }

    // ---- helpers ----

    private EmployeeProfile profile(UUID person) {
        Optional<EmployeeProfile> found = directory.findEmployee(employeeIdOf(person));
        assertThat(found).as("the directory finds " + person).isPresent();
        return found.get();
    }

    private UUID transfer(UUID person, LocalDate effective, UUID toUnit, UUID reportsTo) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, person);
        fields.put(COMMAND_KIND, TRANSFER);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ORG_UNIT_ID, toUnit);
        if (reportsTo != null) {
            fields.put(TARGET_REPORTS_TO_EMPLOYEE_ID, reportsTo);
        }
        return commands().enter(fields);
    }

    /** A correction from the day on; a null leader leaves the reporting line out of it. */
    private UUID correct(UUID person, LocalDate from, UUID reportsTo, boolean feeEarner) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, person);
        fields.put(COMMAND_KIND, CORRECTION);
        fields.put(EFFECTIVE_DATE, from);
        fields.put(IS_TARGET_FEE_EARNER, feeEarner);
        if (reportsTo != null) {
            fields.put(TARGET_REPORTS_TO_EMPLOYEE_ID, reportsTo);
        }
        return commands().enter(fields);
    }

    /** One migrated employed version from the day on, with no assignment version (the shape #960 forbids, here to pin the reading rule). */
    private void migratedEmployeeVersion(UUID person, LocalDate from) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(person), from, from, at, at);
        }
    }

    /** A successful sync run that ends now: the snapshot counts as current, so an employment verdict of NOT_LISTED or SNAPSHOT_UNAVAILABLE is a real answer. */
    private void freshSnapshot() throws Exception {
        syncRun("SUCCEEDED", clock.instant().minusSeconds(120), clock.instant().minusSeconds(60));
    }

    private UUID snapshotUnit(UUID person) throws Exception {
        return fromStored(rows("select org_unit_id from assignments where employee_id = ?", stored(person)).get(0).get("org_unit_id"));
    }

    private boolean snapshotFeeEarner(UUID person) throws Exception {
        return (boolean) rows("select is_fee_earner from employees where id = ?", stored(person)).get(0).get("is_fee_earner");
    }

    private void setSnapshotFeeEarner(UUID person, boolean value) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set is_fee_earner = ? where id = ?", value, stored(person));
        }
    }

    private void snapshotSeparated(UUID person) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update employees set employment_status = 'SEPARATED' where id = ?", stored(person));
        }
    }

    /** The snapshot no longer lists the person: the assignment row is gone and the root says separated (the root stays, versions point at it). */
    private void snapshotForgets(UUID person) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "delete from assignments where employee_id = ?", stored(person));
            update(workforce, "update employees set employment_status = 'SEPARATED', is_fee_earner = false where id = ?", stored(person));
        }
    }
}
