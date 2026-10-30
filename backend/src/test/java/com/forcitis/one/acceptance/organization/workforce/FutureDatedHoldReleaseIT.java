package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #931 IMP-1030-PAR-C1 part (a), ruling of one-design on #732 (2026-10-30; ADR-0068 items 1.8 and 2.3, "不重跑，不再进入待放行"): releasing a hold
 * decides the hold, not the date. A separation whose original effective date is after the business today goes HELD to PENDING; its versions
 * are written at the original date in the release transaction; grants stay open and no EmployeeSeparated is published until the due-date job
 * makes the command EFFECTIVE (exactly one event, never held again). Release audit and SeparationHoldReleased are written at the release.
 * Dated today or earlier, the release executes at once (SeparationHoldReleaseIT covers "today" and "past" in full; here only the business-zone
 * edge of "today" is added). The business day is the Asia/Shanghai day, so a UTC date is never the test of "after today".
 * Grants are closed on the identity side (C2/C3 are not wired yet): the observable proxy used here is that no EmployeeSeparated exists and
 * the leaver's login session is still alive after the release.
 */
class FutureDatedHoldReleaseIT extends FutureHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(FutureDatedHoldReleaseIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** One day after TODAY (business zone) starts at this instant: 2026-11-03 00:00 Asia/Shanghai, still 2026-11-02 in UTC. */
    private static final Instant MIDNIGHT_OF_TOMORROW = Instant.parse("2026-11-02T16:00:00Z");
    private static final Instant ONE_MINUTE_BEFORE_IT = Instant.parse("2026-11-02T15:59:00Z");

    private void assertOneSeparatedEventOn(UUID command, LocalDate day) throws Exception {
        assertThat(eventsOf(EVENT_SEPARATED, command)).as("exactly one EmployeeSeparated").isEqualTo(1);
        String json = eventJsonOf(EVENT_SEPARATED, command).getFirst();
        assertThat(json).containsPattern("\"effectiveDate\"\\s*:\\s*\"" + day + "\"");
    }

    // ---- A1, A7: the release moves the command to PENDING ----

    @Test
    @DisplayName("#931 ruling #732、ADR-0068 第 2 条第 3 项：原生效日晚于业务今天的待放行离职被放行：命令 HELD 到 PENDING（不是 EFFECTIVE，applied_at 仍为空，生效日不变），暂停记录 RELEASED（放行人、原因、途径 INTERFACE），state_transitions 依次为 HELD、PENDING，命令不再回到 HELD")
    void releaseOfAFutureDatedHoldMovesTheCommandToPending() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        Held held = scenario.held();

        releaseAs(scenario.releaser(), held.hold(), REASON_TEXT);

        Map<String, Object> command = command(held.command());
        assertThat(command.get("status")).as("released before its date: PENDING, not EFFECTIVE").isEqualTo(PENDING);
        assertThat(command.get("applied_at")).as("not applied yet").isNull();
        assertThat(command.get("effective_date")).isEqualTo(DUE);
        Map<String, Object> hold = hold(held.hold());
        assertThat(hold.get("status")).isEqualTo(RELEASED);
        assertThat(hold.get("release_channel_kind")).isEqualTo(CHANNEL_INTERFACE);
        assertThat(hold.get("released_by")).isEqualTo(scenario.releaser().userId());
        assertThat(hold.get("release_reason")).isEqualTo(REASON_TEXT);
        assertThat(hold.get("released_at")).isNotNull();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).as("HELD then PENDING, held once").endsWith(HELD, PENDING).containsOnlyOnce(HELD)
                .doesNotContain(EFFECTIVE);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, held.hold())).endsWith(HELD, RELEASED);
        List<Map<String, Object>> steps = transitions(AGGREGATE_PERSONNEL_COMMAND, held.command());
        Map<String, Object> last = steps.getLast();
        assertThat(last.get("from_status")).isEqualTo(HELD);
        assertThat(last.get("to_status")).isEqualTo(PENDING);
        assertThat(rows("select 1 from " + SEPARATION_HOLDS + " where employee_id = ?", stored(held.employee()))).as("no second hold").hasSize(1);
    }

    // ---- A2: versions at the original date, in the release transaction ----

    @Test
    @DisplayName("#931 ruling #732、ADR-0068 第 1 条第 8 项：放行事务内按原生效日写 SEPARATED 员工版本并关闭任职版本（前一版本 EMPLOYED，时间线无缝，历史作废而非删除）；放行前这条未来版本已被作废；业务今天此人仍在职")
    void releaseWritesTheVersionsAtTheOriginalDate() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        UUID employee = scenario.held().employee();
        assertThat(employmentOn(employee, DUE.plusDays(1))).as("while held the future separation version is voided").isEqualTo(EMPLOYED);
        int versionsBefore = allVersions(EMPLOYEE_VERSIONS, employee).size();

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertSeparatedFrom(employee, DUE);
        assertThat(employmentOn(employee, TODAY)).as("in service today: the business judgment follows the version dates").isEqualTo(EMPLOYED);
        assertThat(employmentOn(employee, DUE.minusDays(1))).isEqualTo(EMPLOYED);
        assertThat(allVersions(EMPLOYEE_VERSIONS, employee)).as("history is superseded, never deleted").hasSizeGreaterThanOrEqualTo(versionsBefore);
        assertThat(commandCount(employee)).as("the command is not entered again").isEqualTo(1);
    }

    // ---- A3: no EmployeeSeparated, grants stay ----

    @Test
    @DisplayName("#931 ruling #732：放行时不发 EmployeeSeparated，授予不关闭（身份侧未接线，以「无事件」与「此人登录会话仍有效」观测）；他人命令不受影响")
    void releaseBeforeTheDatePublishesNoSeparationAndClosesNoGrant() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        assertThat(profileStatus(scenario.leaver())).as("held, the person is signed in").isEqualTo(200);

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertThat(eventsOf(EVENT_SEPARATED, scenario.held().command())).as("EmployeeSeparated waits for the effective date").isZero();
        assertThat(profileStatus(scenario.leaver())).as("grants and session of the leaver are not closed at the release").isEqualTo(200);
        assertThat(employmentOn(scenario.stays().employeeId(), DUE)).isEqualTo(EMPLOYED);
    }

    // ---- A4: audit and SeparationHoldReleased at the release ----

    @Test
    @DisplayName("#931 ruling #732：放行审计 organization.workforce.separation-hold.released 与 SeparationHoldReleased 在放行时写（一条），SeparationHoldReleased.effectiveDate 为原生效日，含暂停记录 ID；日作业之后不再增加")
    void auditAndReleasedEventAreWrittenAtTheRelease() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        releaseAs(scenario.releaser(), scenario.held().hold(), REASON_TEXT);

        assertThat(auditCount(AUDIT_HOLD_RELEASED)).as("one audit entry at the release").isEqualTo(audited + 1);
        List<Map<String, Object>> entries = releaseAuditBy(scenario.releaser());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().get("action")).isEqualTo(AUDIT_HOLD_RELEASED);
        assertThat(String.valueOf(entries.getFirst().get("subjectId"))).isEqualTo(scenario.held().employee().toString());
        List<String> released = eventJsonOf(EVENT_HOLD_RELEASED, scenario.held().command());
        assertThat(released).as("SeparationHoldReleased at the release").hasSize(1);
        assertThat(released.getFirst()).containsPattern("\"effectiveDate\"\\s*:\\s*\"" + DUE + "\"").contains(scenario.held().hold().toString());
        assertThat(eventsOf(EVENT_HELD, scenario.held().command())).isEqualTo(1);

        setToday(DUE);
        runDueJob();

        assertThat(auditCount(AUDIT_HOLD_RELEASED)).as("the job writes no second release audit").isEqualTo(audited + 1);
        assertThat(eventsOf(EVENT_HOLD_RELEASED, scenario.held().command())).as("nor a second SeparationHoldReleased").isEqualTo(1);
    }

    // ---- A5: the due date ----

    @Test
    @DisplayName("#931 ruling #732、ADR-0068 第 2 条第 3 项「不重跑，不再进入待放行」：到日作业在生效日执行：命令 EFFECTIVE，恰好一条 EmployeeSeparated（生效日为原日期）；同日再跑、次日再跑仍恰好一条；作业之后无第二条暂停记录、无第二条 SeparationHeld、状态路径 HELD、PENDING、EFFECTIVE，版本与审计不被重写（只证明结果；到日时此人版本已是 SEPARATED，重判与否在此场景下不可区分）")
    void theDueDateJobExecutesOnceAndNeverHoldsAgain() throws Exception {
        Scenario scenario = heldOnWithRemaining(0, DUE);
        Held held = scenario.held();
        releaseAs(scenario.releaser(), held.hold(), REASON_TEXT);
        List<Object> employeeVersions = currentVersionIds(EMPLOYEE_VERSIONS, held.employee());
        List<Object> assignmentVersions = currentVersionIds(ASSIGNMENT_VERSIONS, held.employee());
        int audited = auditCount(AUDIT_HOLD_RELEASED);

        setToday(DUE);
        runDueJob();

        Map<String, Object> command = command(held.command());
        assertThat(command.get("status")).isEqualTo(EFFECTIVE);
        assertThat(command.get("applied_at")).as("applied by the job").isNotNull();
        assertOneSeparatedEventOn(held.command(), DUE);
        runDueJob();
        assertOneSeparatedEventOn(held.command(), DUE);
        setToday(DUE.plusDays(1));
        runDueJob();
        assertOneSeparatedEventOn(held.command(), DUE);

        assertThat(commandStatus(held.command())).isEqualTo(EFFECTIVE);
        assertThat(holdStatus(held.hold())).as("the hold stays released").isEqualTo(RELEASED);
        assertThat(rows("select 1 from " + SEPARATION_HOLDS + " where employee_id = ?", stored(held.employee()))).as("never held again").hasSize(1);
        assertThat(eventsOf(EVENT_HELD, held.command())).as("no second SeparationHeld").isEqualTo(1);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).endsWith(HELD, PENDING, EFFECTIVE).containsOnlyOnce(HELD);
        assertThat(statusPath(AGGREGATE_SEPARATION_HOLD, held.hold())).endsWith(HELD, RELEASED);
        assertThat(currentVersionIds(EMPLOYEE_VERSIONS, held.employee())).as("the versions were written at the release; the job does not rewrite them").isEqualTo(employeeVersions);
        assertThat(currentVersionIds(ASSIGNMENT_VERSIONS, held.employee())).isEqualTo(assignmentVersions);
        assertSeparatedFrom(held.employee(), DUE);
        assertThat(auditCount(AUDIT_HOLD_RELEASED)).isEqualTo(audited);
        assertThat(employmentOn(scenario.stays().employeeId(), DUE)).isEqualTo(EMPLOYED);
    }

    @Test
    @DisplayName("#931 ruling #732：到日作业在生效日之前运行（今天、生效日前一天）：命令仍 PENDING，无 EmployeeSeparated，applied_at 为空，版本不变")
    void theJobBeforeTheDateLeavesTheCommandPending() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        Held held = scenario.held();
        releaseAs(scenario.releaser(), held.hold(), REASON_TEXT);
        List<Object> employeeVersions = currentVersionIds(EMPLOYEE_VERSIONS, held.employee());

        runDueJob();
        setToday(DUE.minusDays(1));
        runDueJob();

        Map<String, Object> command = command(held.command());
        assertThat(command.get("status")).isEqualTo(PENDING);
        assertThat(command.get("applied_at")).isNull();
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();
        assertThat(currentVersionIds(EMPLOYEE_VERSIONS, held.employee())).isEqualTo(employeeVersions);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).endsWith(HELD, PENDING);
    }

    @Test
    @DisplayName("#931 边界、ADR-0068 第 1 条第 8 项（零点后、业务时区）：生效日 11-03 的已放行命令，业务时区 11-02 23:59 作业不执行，11-03 00:00（UTC 仍是 11-02）作业执行，恰好一条事件")
    void theJobFollowsTheBusinessDayNotTheUtcDay() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, TODAY.plusDays(1));
        Held held = scenario.held();
        releaseAs(scenario.releaser(), held.hold(), REASON_TEXT);
        assertThat(commandStatus(held.command())).as("tomorrow is after today").isEqualTo(PENDING);

        clock.set(ONE_MINUTE_BEFORE_IT);
        runDueJob();
        assertThat(commandStatus(held.command())).as("23:59 in the business zone: still today").isEqualTo(PENDING);
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();

        clock.set(MIDNIGHT_OF_TOMORROW);
        runDueJob();
        runDueJob();
        assertThat(commandStatus(held.command())).as("00:00 in the business zone: the effective date has come").isEqualTo(EFFECTIVE);
        assertOneSeparatedEventOn(held.command(), TODAY.plusDays(1));
    }

    @Test
    @DisplayName("#931 ADR-0068 第 2 条第 2 项：仍为待放行（未放行）的离职，生效日到后作业不执行也不放行：命令与暂停记录保持 HELD，无 EmployeeSeparated，此人在职")
    void theJobDoesNotTouchACommandThatIsStillHeld() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        Held held = scenario.held();

        setToday(DUE);
        runDueJob();
        setToday(DUE.plusDays(1));
        runDueJob();

        assertThat(commandStatus(held.command())).isEqualTo(HELD);
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertThat(eventsOf(EVENT_SEPARATED, held.command())).isZero();
        assertThat(employmentOn(held.employee(), DUE.plusDays(1))).isEqualTo(EMPLOYED);
    }

    // ---- B: today / past unchanged; the business-zone edge of "today" ----

    @Test
    @DisplayName("#931 边界（B）、业务时区：生效日 11-03，放行时刻为业务时区 11-03 00:00（UTC 仍是 11-02）：生效日即业务今天，HELD 到 EFFECTIVE 在放行事务内，放行时一条 EmployeeSeparated，版本在原生效日")
    void releaseOnTheEffectiveDayInTheBusinessZoneExecutesAtOnce() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, TODAY.plusDays(1));
        clock.set(MIDNIGHT_OF_TOMORROW);
        Staff releaser = signedInAgain(scenario.releaser());

        releaseAs(releaser, scenario.held().hold(), REASON_TEXT);

        assertThat(commandStatus(scenario.held().command())).isEqualTo(EFFECTIVE);
        assertThat(command(scenario.held().command()).get("applied_at")).isNotNull();
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, scenario.held().command())).endsWith(HELD, EFFECTIVE).doesNotContain(PENDING);
        assertOneSeparatedEventOn(scenario.held().command(), TODAY.plusDays(1));
        assertSeparatedFrom(scenario.held().employee(), TODAY.plusDays(1));
        assertThat(eventsOf(EVENT_HOLD_RELEASED, scenario.held().command())).isEqualTo(1);
    }

    @Test
    @DisplayName("#931 边界：放行时刻为业务时区 11-02 23:59，生效日 11-03 仍晚于业务今天：HELD 到 PENDING，无 EmployeeSeparated")
    void releaseOneMinuteBeforeTheEffectiveDayGoesPending() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, TODAY.plusDays(1));
        clock.set(ONE_MINUTE_BEFORE_IT);
        Staff releaser = signedInAgain(scenario.releaser());

        releaseAs(releaser, scenario.held().hold(), REASON_TEXT);

        assertThat(commandStatus(scenario.held().command())).isEqualTo(PENDING);
        assertThat(eventsOf(EVENT_SEPARATED, scenario.held().command())).isZero();
        assertSeparatedFrom(scenario.held().employee(), TODAY.plusDays(1));
    }
}
