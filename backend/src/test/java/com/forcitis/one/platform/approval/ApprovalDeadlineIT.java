package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.fixture.doubles.TestWorkCalendar;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.EscalationTarget;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.EnforceDeadlines;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskBecameOverdue;
import com.forcitis.one.platform.approval.events.TaskClosed;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * D-1a-110..117: a task created on a workday is due at 23:59 of the next workday, reminded at 09:00 of the workday after that and escalated
 * at 09:00 of the workday after the reminder; claimed tasks go to the holder's superior, unclaimed ones to the process administrator; a
 * deadline never approves anything.
 */
@Import(ApprovalDeadlineIT.Time.class)
class ApprovalDeadlineIT extends ApprovalIntegrationSupport {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    /** Friday. */
    static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);

    @TestConfiguration
    static class Time {

        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock(FRIDAY.atTime(10, 0).atZone(SHANGHAI).toInstant(), SHANGHAI);
        }

        @Bean
        @Primary
        TestWorkCalendar workCalendar() {
            return new TestWorkCalendar();
        }
    }

    @Autowired
    MutableClock clock;

    @Autowired
    TestWorkCalendar calendar;

    @Autowired
    EnforceDeadlines enforceDeadlines;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    MeterRegistry meters;

    @BeforeEach
    void resetTime() {
        calendar.reset();
        clock.set(FRIDAY.atTime(10, 0).atZone(SHANGHAI).toInstant());
        jdbc.update("update platform_approval.approval_tasks set status = 'CLOSED' where status in ('PENDING', 'CLAIMED')");
    }

    @AfterEach
    void putTheCalendarBack() {
        calendar.reset();
    }

    record Scene(String key, ApprovalInstanceId instance, ApprovalTaskId task, long initiator, long manager, long head) {
    }

    Scene scene(boolean managerHasSuperior) {
        long unit = newId();
        long head = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, head)
                .person(manager, unit, managerHasSuperior ? head : null);
        String key = newKey("deadline");
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString()))))));
        return new Scene(key, instance, openTaskId(instance), initiator, manager, head);
    }

    Instant column(Scene s, String column) {
        return jdbc.queryForObject("select " + column + " from platform_approval.approval_tasks where task_id = ?", java.time.OffsetDateTime.class,
                s.task().value()).toInstant();
    }

    Instant dueOf(Scene s) {
        return column(s, "due_at");
    }

    Instant remindAtOf(Scene s) {
        return column(s, "remind_at");
    }

    Instant escalateAtOf(Scene s) {
        return column(s, "escalate_at");
    }

    boolean isOverdue(Scene s) {
        return jdbc.queryForObject("select overdue_at is not null from platform_approval.approval_tasks where task_id = ?", Boolean.class,
                s.task().value());
    }

    static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(SHANGHAI).toInstant();
    }

    List<String> candidatesOf(Scene s) {
        return taskService.getIdentityLinksForTask(s.task().value().toString()).stream().filter(l -> "candidate".equals(l.getType())).map(l -> l.getUserId())
                .toList();
    }

    @Test
    @DisplayName("D-1a-110/111/112 a task created on Friday is due at 23:59 of Monday, reminded Tuesday 09:00 and escalated Wednesday 09:00; the weekend does not count")
    void weekendDoesNotCount() {
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 19), 23, 59));
        assertThat(remindAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 20), 9, 0));
        assertThat(escalateAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 21), 9, 0));
    }

    @Test
    @DisplayName("D-1a-111 a public holiday after the creation day moves every point to the next workday")
    void holidayMovesTheDeadline() {
        calendar.holiday(LocalDate.of(2026, 10, 19));
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 20), 23, 59));
        assertThat(remindAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 21), 9, 0));
        assertThat(escalateAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 22), 9, 0));
    }

    @Test
    @DisplayName("D-1a-111 a make-up Saturday is a workday, so T+1 from Friday is Saturday 23:59, and the reminder skips Sunday")
    void makeUpWorkdayCounts() {
        calendar.makeUpWorkday(LocalDate.of(2026, 10, 17));
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 17), 23, 59));
        assertThat(remindAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 19), 9, 0));
        assertThat(escalateAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 20), 9, 0));
    }

    @Test
    @DisplayName("D-1a-111 T is the workday the task is created in: created on a Saturday it counts from Monday")
    void createdOnADayOffCountsFromTheNextWorkday() {
        clock.set(at(LocalDate.of(2026, 10, 17), 10, 0));
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 20), 23, 59));
        assertThat(remindAtOf(s)).isEqualTo(at(LocalDate.of(2026, 10, 21), 9, 0));
    }

    @Test
    @DisplayName("D-1a-84 the number of workdays and the escalation interval come from the node of the process version deployed; a newer version applies to later starts only")
    void numbersComeFromTheNode() {
        Scene before = scene(true);
        deployVariant("deadline3");
        Scene after = scene(true);

        assertThat(dueOf(before)).isEqualTo(at(LocalDate.of(2026, 10, 19), 23, 59));
        assertThat(dueOf(after)).as("Mon, Tue, Wed").isEqualTo(at(LocalDate.of(2026, 10, 21), 23, 59));
        assertThat(remindAtOf(after)).isEqualTo(at(LocalDate.of(2026, 10, 22), 9, 0));
        assertThat(escalateAtOf(after)).as("Fri, Mon after the reminder day Thursday").isEqualTo(at(LocalDate.of(2026, 10, 26), 9, 0));
    }

    @Test
    @DisplayName("RULE-383 a calendar that cannot answer fails the start of the approval instead of assuming every day is a workday; nothing is left behind")
    void calendarFailureFailsTheStart() {
        calendar.failWith("year 2026 is not maintained");
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("deadline-calendar");

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString())))))))
                .hasStackTraceContaining("not maintained");

        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key)).isZero();
        assertThat(runtime.createProcessInstanceQuery().processInstanceBusinessKey(key).count()).isZero();
    }

    @Test
    @DisplayName("D-1a-112 nothing happens before the reminder time; after it the holder is reminded exactly once and the task is NOT approved")
    void reminderOnceAndNeverAnApproval() {
        Scene s = scene(true);

        clock.set(remindAtOf(s).minusSeconds(1));
        enforceDeadlines.execute();
        assertThat(isOverdue(s)).isFalse();
        verify(wecom, after(300).times(0)).send(argThat(card -> card.subject().equals(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_OVERDUE, s.task().value()))));

        clock.set(remindAtOf(s));
        enforceDeadlines.execute();
        assertThat(isOverdue(s)).isTrue();
        enforceDeadlines.execute();

        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.subject().equals(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_OVERDUE, s.task().value()))
                && card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(s.manager()))))));
        assertThat(jdbc.queryForObject("select status from platform_approval.approval_tasks where task_id = ?", String.class, s.task().value()))
                .isEqualTo("PENDING");
        assertThat(requestStatus(s.key())).isEqualTo("RUNNING");
        assertThat(approvals.isFinished(s.instance())).isFalse();
        List<ApprovalTaskBecameOverdue> overdue = events.overdue().stream().filter(e -> e.taskId().equals(s.task())).toList();
        assertThat(overdue).hasSize(1);
        assertThat(overdue.getFirst().holders()).containsExactly(TestAccounts.account(s.manager()));
        assertThat(overdue.getFirst().dueAt()).isEqualTo(dueOf(s));
        assertThat(overdue.getFirst().envelope().aggregateType()).isEqualTo("ApprovalRequest");
        assertThat(overdue.getFirst().envelope().aggregateId()).isEqualTo(requestIdOf(s.key()));
        assertThat(overdue.getFirst().envelope().aggregateVersion()).as("round started = 1, assigned = 2, overdue = 3").isEqualTo(3);
        assertThat(overdue.getFirst().envelope().businessDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(overdue.getFirst().envelope().actorType().name()).as("a job acts as the system").isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("D-1a-84 a task decided in time is never reminded")
    void decidedInTimeIsNotReminded() {
        Scene s = scene(true);
        decideTasks.execute(TestAccounts.account(s.manager()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());

        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();
        assertThat(isOverdue(s)).isFalse();
        assertThat(events.overdue().stream().filter(e -> e.taskId().equals(s.task()))).isEmpty();
    }

    @Test
    @DisplayName("D-1a-113 a claimed task that is still unhandled at the escalation time goes to the claimant's superior; the claimant stays a candidate")
    void aClaimedTaskEscalatesToTheSuperior() {
        Scene s = scene(true);
        claimTask.execute(s.task(), TestAccounts.account(s.manager()), java.util.OptionalLong.empty());
        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();

        assertThat(candidatesOf(s)).containsExactlyInAnyOrder(TestAccounts.account(s.manager()).value().toString(), TestAccounts.account(s.head()).value().toString());
        assertThat(openTask(s.instance()).getAssignee()).as("the claim is released so the superior can take it").isNull();
        assertThat(jdbc.queryForObject("select status from platform_approval.approval_tasks where task_id = ?", String.class, s.task().value()))
                .isEqualTo("PENDING");
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.subject().equals(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ESCALATED, s.task().value()))
                && card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(s.head()))))));
        ApprovalTaskEscalated escalated = events.escalated().stream().filter(e -> e.taskId().equals(s.task())).findFirst().orElseThrow();
        assertThat(escalated.escalatedTo()).containsExactly(TestAccounts.account(s.head()));
        assertThat(escalated.target()).isEqualTo(EscalationTarget.SUPERVISOR);
        assertThat(escalated.envelope().aggregateVersion()).as("round, assigned, claimed, overdue, escalated").isEqualTo(5);
        assertThat(requestStatus(s.key())).isEqualTo("RUNNING");

        decideTasks.execute(TestAccounts.account(s.head()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, "covering")), java.util.OptionalLong.empty());
        assertThat(requestStatus(s.key())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("D-1a-113 D-1a-61 an unclaimed candidate group escalates to every holder of the process administrator role, any of whom may then decide")
    void anUnclaimedTaskEscalatesToTheAdministrator() {
        Scene s = scene(true);
        long secondAdministrator = newId();
        organisation.person(secondAdministrator, 1, null).role("process_administrator", ADMINISTRATOR, secondAdministrator);
        double before = meters.counter("forcitis.platform.approval.task.escalation.administrator").count();
        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();

        assertThat(candidatesOf(s)).containsExactlyInAnyOrder(TestAccounts.account(s.manager()).value().toString(), TestAccounts.account(ADMINISTRATOR).value().toString(),
                TestAccounts.account(secondAdministrator).value().toString());
        ApprovalTaskEscalated escalated = events.escalated().stream().filter(e -> e.taskId().equals(s.task())).findFirst().orElseThrow();
        assertThat(escalated.target()).isEqualTo(EscalationTarget.PROCESS_ADMINISTRATOR);
        assertThat(escalated.escalatedTo()).containsExactlyInAnyOrder(TestAccounts.account(ADMINISTRATOR), TestAccounts.account(secondAdministrator));
        assertThat(meters.counter("forcitis.platform.approval.task.escalation.administrator").count()).isEqualTo(before + 1);
        org.mockito.Mockito.verify(wecom, org.mockito.Mockito.timeout(10_000)).markHandled(org.mockito.ArgumentMatchers.eq(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ASSIGNED, s.task().value())),
                org.mockito.ArgumentMatchers.argThat(note -> note.contains("已超时，已升级给")));
        decideTasks.execute(TestAccounts.account(ADMINISTRATOR), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, "covering")), java.util.OptionalLong.empty());
        assertThat(requestStatus(s.key())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("D-1a-113 a claimed task whose holder has no superior goes to the process administrator and raises the alert counter")
    void noSuperiorGoesToTheAdministrator() {
        Scene s = scene(false);
        claimTask.execute(s.task(), TestAccounts.account(s.manager()), java.util.OptionalLong.empty());
        double before = meters.counter("forcitis.platform.approval.task.escalation.administrator").count();
        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();

        assertThat(meters.counter("forcitis.platform.approval.task.escalation.administrator").count()).isEqualTo(before + 1);
        assertThat(candidatesOf(s)).contains(TestAccounts.account(ADMINISTRATOR).value().toString());
        assertThat(events.escalated().stream().filter(e -> e.taskId().equals(s.task())).findFirst().orElseThrow().target())
                .isEqualTo(EscalationTarget.PROCESS_ADMINISTRATOR);
    }

    @Test
    @DisplayName("D-1a-112 a late job run records the reminder and the escalation in order, once each")
    void lateRunCatchesUp() {
        Scene s = scene(true);
        clock.set(escalateAtOf(s).plusSeconds(10));

        enforceDeadlines.execute();
        enforceDeadlines.execute();

        assertThat(events.overdue().stream().filter(e -> e.taskId().equals(s.task()))).hasSize(1);
        assertThat(events.escalated().stream().filter(e -> e.taskId().equals(s.task()))).hasSize(1);
        verify(wecom, after(500).times(1)).send(argThat(card -> card.subject().equals(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_OVERDUE, s.task().value()))));
        verify(wecom, times(1)).send(argThat(card -> card.subject().equals(com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ESCALATED, s.task().value()))));
    }

    @Test
    @DisplayName("M-7 an open task of an earlier round does not fail the job every run: it is closed, as an event, under the lock of its request")
    void aLeftoverTaskOfAnEarlierRoundIsClosed() {
        Scene s = scene(true);
        tx.executeWithoutResult(t -> approvals.withdraw(com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess.KEY, document(s.key()),
                TestAccounts.account(s.initiator()), "wrong hours"));
        start(s.key(), s.initiator(), s.manager());
        jdbc.update("update platform_approval.approval_tasks set status = 'PENDING', completed_at = null, due_at = ?, remind_at = ?, escalate_at = ?"
                + " where task_id = ?", java.sql.Timestamp.from(Instant.parse("2019-01-01T00:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")), java.sql.Timestamp.from(Instant.parse("2021-01-01T00:00:00Z")),
                s.task().value());
        double failed = meters.counter("forcitis.platform.approval.deadline.failed").count();

        enforceDeadlines.execute();

        assertThat(jdbc.queryForObject("select status from platform_approval.approval_tasks where task_id = ?", String.class, s.task().value()))
                .isEqualTo("CLOSED");
        assertThat(events.of(TaskClosed.class)).anySatisfy(e -> assertThat(e.taskId()).isEqualTo(s.task()));
        assertThat(meters.counter("forcitis.platform.approval.deadline.failed").count()).isEqualTo(failed);
        enforceDeadlines.execute();
        assertThat(meters.counter("forcitis.platform.approval.deadline.failed").count()).isEqualTo(failed);
    }
}
