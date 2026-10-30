package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #967 (one-design design on #967, issue comment 6530608108; ADR-0068 item 2.2 line 67 and line 89; item 3): the due-date job judges an
 * un-held future-dated separation once more on its effective date, with the REAL SeparationReview (no mock). The leaver is judged as of the
 * last working day (effective date − 1), everyone else as of today; held means: future version voided, HELD, SeparationHeld, no
 * EmployeeSeparated. Released ones are not judged again; a late run does not judge at all and voids nothing already in force.
 */
class DueSeparationRejudgementIT extends FutureHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(DueSeparationRejudgementIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A plain account (no administrator role) whose separation the 人事专员 enters to take effect on DUE: PENDING, versions written. */
    private UUID pendingSeparationOf(Staff leaver) throws Exception {
        actIn(hr);
        UUID command = separate(leaver.employeeId(), DUE.minusDays(1));
        assertThat(commandStatus(command)).as("nothing to hold at entry").isEqualTo(PENDING);
        assertThat(employmentOn(leaver.employeeId(), DUE)).as("the future version is written at entry").isEqualTo(SEPARATED);
        return command;
    }

    private long holdRows(UUID command) throws Exception {
        return (long) rows("select count(*) as n from " + SEPARATION_HOLDS + " where personnel_command_id = ?", command).get(0).get("n");
    }

    @Test
    @DisplayName("#967 ADR-0068 行 67：录入后才成为可用超级管理员的离职人，生效日作业复判为暂停：命令待放行、未来离职版本作废（当天仍在职）、一行待放行、一个 SeparationHeld、没有 EmployeeSeparated")
    void aLeaverWhoBecameAnAdministratorAfterEntryIsHeldOnTheDueDay() throws Exception {
        administrator();
        administrator();
        Staff leaver = account();
        UUID command = pendingSeparationOf(leaver);
        grantByOperations(leaver.employeeId(), SUPER_ADMIN);

        setToday(DUE);
        runDueJob();

        assertThat(commandStatus(command)).isEqualTo(HELD);
        assertThat(employmentOn(leaver.employeeId(), DUE)).as("the future separated version is voided").isEqualTo(EMPLOYED);
        assertThat(allVersions(EMPLOYEE_VERSIONS, leaver.employeeId())).as("voided, not deleted")
                .anySatisfy(row -> {
                    assertThat(row.get("employment_status")).isEqualTo(SEPARATED);
                    assertThat(row.get("superseded_at")).isNotNull();
                });
        List<Map<String, Object>> assignments = currentVersions(ASSIGNMENT_VERSIONS, leaver.employeeId());
        assertContiguous(assignments);
        assertThat(inForceOn(assignments, DUE)).as("the assignment closed at the separation day is reopened").isNotNull();
        assertThat(holdRows(command)).isEqualTo(1);
        assertThat(holdStatus(holdIdOf(command))).isEqualTo(HELD);
        assertThat(eventsOf(EVENT_HELD, command)).isEqualTo(1);
        assertThat(eventsOf(EVENT_SEPARATED, command)).isZero();
    }

    @Test
    @DisplayName("#967 ADR-0068 行 67：复判无需暂停的未来离职，生效日作业照常生效：一个 EmployeeSeparated、版本不改、没有待放行行")
    void nothingToHoldTakesEffect() throws Exception {
        administrator();
        administrator();
        Staff leaver = account();
        UUID command = pendingSeparationOf(leaver);
        List<Object> versions = currentVersionIds(EMPLOYEE_VERSIONS, leaver.employeeId());

        setToday(DUE);
        runDueJob();

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(currentVersionIds(EMPLOYEE_VERSIONS, leaver.employeeId())).isEqualTo(versions);
        assertThat(holdRows(command)).isZero();
        assertThat(eventsOf(EVENT_SEPARATED, command)).isEqualTo(1);
        assertThat(eventsOf(EVENT_HELD, command)).isZero();
    }

    @Test
    @DisplayName("#967 ADR-0068 行 89：放行过的未来离职不再复判：到日离职人仍是管理员也照常生效，只有一行（已放行）待放行，不再发 SeparationHeld")
    void aReleasedSeparationIsNotJudgedAgain() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);
        UUID command = scenario.held().command();
        assertThat(commandStatus(command)).isEqualTo(PENDING);
        int heldEvents = eventsOf(EVENT_HELD, command);

        setToday(DUE);
        runDueJob();

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(holdRows(command)).isEqualTo(1);
        assertThat(holdStatus(scenario.held().hold())).isEqualTo(RELEASED);
        assertThat(eventsOf(EVENT_HELD, command)).isEqualTo(heldEvents);
        assertThat(eventsOf(EVENT_SEPARATED, command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#967 设计第 3 条（补跑）：作业在生效日之后才跑时不复判：即使离职人已成管理员也照常生效，已生效的离职版本不作废，没有待放行行")
    void aLateRunDoesNotJudgeAndVoidsNothing() throws Exception {
        administrator();
        administrator();
        Staff leaver = account();
        UUID command = pendingSeparationOf(leaver);
        grantByOperations(leaver.employeeId(), SUPER_ADMIN);
        List<Object> versions = currentVersionIds(EMPLOYEE_VERSIONS, leaver.employeeId());

        setToday(DUE.plusDays(2));
        runDueJob();

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(currentVersionIds(EMPLOYEE_VERSIONS, leaver.employeeId())).as("nothing already in force is voided").isEqualTo(versions);
        assertThat(employmentOn(leaver.employeeId(), DUE)).isEqualTo(SEPARATED);
        assertThat(holdRows(command)).isZero();
        assertThat(eventsOf(EVENT_SEPARATED, command)).isEqualTo(1);
    }

    @Test
    @DisplayName("#967 设计（离职人按最后工作日判）、ADR-0068 第 3 条：最后工作日在停薪留职的离职人不是可用管理员，生效日复判不暂停")
    void aLeaverOnUnpaidLeaveOnTheLastWorkingDayIsNotHeld() throws Exception {
        administrator();
        administrator();
        Staff leaver = account();
        actIn(hr);
        Map<String, Object> leave = new HashMap<>();
        leave.put(EMPLOYEE_ID, leaver.employeeId());
        leave.put(COMMAND_KIND, UNPAID_LEAVE_START);
        leave.put(EFFECTIVE_DATE, TODAY.plusDays(2));
        commands().enter(leave);
        UUID command = pendingSeparationOf(leaver);
        grantByOperations(leaver.employeeId(), SUPER_ADMIN);
        assertThat(onUnpaidLeaveOn(leaver.employeeId(), DUE.minusDays(1))).as("on leave on the last working day").isTrue();

        setToday(DUE);
        runDueJob();

        assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
        assertThat(holdRows(command)).isZero();
        assertThat(eventsOf(EVENT_SEPARATED, command)).isEqualTo(1);
    }
}
