package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.fixture.doubles.TestWorkCalendar;
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
import com.forcitis.one.platform.approval.events.ApprovalTaskOverdue;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
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

/** Approval deadline: due at the end of the n-th workday after the submission day, a reminder when due, an escalation later, never an approval. */
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
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(key, new UserId(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager)))))));
        return new Scene(key, instance, openTaskId(instance), initiator, manager, head);
    }

    Instant dueOf(Scene s) {
        return jdbc.queryForObject("select due_at from platform_approval.approval_tasks where task_id = ?", java.time.OffsetDateTime.class,
                s.task().value()).toInstant();
    }

    Instant escalateAtOf(Scene s) {
        return jdbc.queryForObject("select escalate_at from platform_approval.approval_tasks where task_id = ?", java.time.OffsetDateTime.class,
                s.task().value()).toInstant();
    }

    boolean isOverdue(Scene s) {
        return jdbc.queryForObject("select overdue_at is not null from platform_approval.approval_tasks where task_id = ?", Boolean.class,
                s.task().value());
    }

    static Instant endOf(LocalDate day) {
        return day.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
    }

    @Test
    @DisplayName("platform.approval deadline: T+1 from a Friday submission is the end of Monday, the weekend does not count")
    void weekendDoesNotCount() {
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(endOf(LocalDate.of(2026, 10, 19)));
        assertThat(escalateAtOf(s)).as("one more workday after the deadline").isEqualTo(endOf(LocalDate.of(2026, 10, 20)));
    }

    @Test
    @DisplayName("platform.approval deadline: a public holiday after the submission day moves the deadline to the next workday")
    void holidayMovesTheDeadline() {
        calendar.holiday(LocalDate.of(2026, 10, 19));
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(endOf(LocalDate.of(2026, 10, 20)));
        assertThat(escalateAtOf(s)).isEqualTo(endOf(LocalDate.of(2026, 10, 21)));
    }

    @Test
    @DisplayName("platform.approval deadline: a make-up Saturday is a workday, so T+1 from Friday is the end of Saturday")
    void makeUpWorkdayCounts() {
        calendar.makeUpWorkday(LocalDate.of(2026, 10, 17));
        Scene s = scene(true);

        assertThat(dueOf(s)).isEqualTo(endOf(LocalDate.of(2026, 10, 17)));
        assertThat(escalateAtOf(s)).as("Sunday is not a workday: the next one is Monday").isEqualTo(endOf(LocalDate.of(2026, 10, 19)));
    }

    @Test
    @DisplayName("platform.approval deadline: the number of workdays and the escalation interval come from the decision table version deployed")
    void numbersComeFromTheDecisionTable() {
        deployVariant("deadline3");
        Scene s = scene(true);

        assertThat(dueOf(s)).as("Mon, Tue, Wed").isEqualTo(endOf(LocalDate.of(2026, 10, 21)));
        assertThat(escalateAtOf(s)).as("Thu, Fri").isEqualTo(endOf(LocalDate.of(2026, 10, 23)));
    }

    @Test
    @DisplayName("platform.approval deadline: a calendar that cannot answer fails the submission instead of assuming every day is a workday; nothing is left behind")
    void calendarFailureFailsTheStart() {
        calendar.failWith("year 2026 is not maintained");
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("deadline-calendar");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.executeWithoutResult(s -> approvals.start(startRequest(key, new UserId(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager))))))))
                .hasStackTraceContaining("not maintained");

        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_key = ?", Integer.class, key)).isZero();
        assertThat(runtime.createProcessInstanceQuery().processInstanceBusinessKey("TIMESHEET:" + key).count()).isZero();
    }

    @Test
    @DisplayName("platform.approval deadline: nothing happens before the deadline; after it the holder is reminded exactly once and the task is NOT approved")
    void reminderOnceAndNeverAnApproval() {
        Scene s = scene(true);

        clock.set(dueOf(s).minusSeconds(1));
        enforceDeadlines.execute();
        assertThat(isOverdue(s)).isFalse();
        verify(wecom, after(300).never()).send(argThat(card -> card.deduplicationKey().equals("approval-task:" + s.task().value() + ":overdue")));

        clock.set(dueOf(s).plusSeconds(1));
        enforceDeadlines.execute();
        assertThat(isOverdue(s)).isTrue();
        enforceDeadlines.execute();

        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.deduplicationKey().equals("approval-task:" + s.task().value() + ":overdue")
                && card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(s.manager()))))));
        assertThat(jdbc.queryForObject("select status from platform_approval.approval_tasks where task_id = ?", String.class, s.task().value()))
                .isEqualTo("PENDING");
        assertThat(requestStatus(s.key())).isEqualTo("RUNNING");
        assertThat(approvals.isFinished(s.instance())).isFalse();
        eventually(() -> {
            List<ApprovalTaskOverdue> overdue = events.overdue().stream().filter(e -> e.taskId().equals(s.task())).toList();
            assertThat(overdue).hasSize(1);
            assertThat(overdue.getFirst().holders()).containsExactly(new UserId(s.manager()));
            assertThat(overdue.getFirst().dueAt()).isEqualTo(dueOf(s));
            assertThat(overdue.getFirst().envelope().aggregateType()).isEqualTo("ApprovalTask");
            assertThat(overdue.getFirst().envelope().aggregateId()).isEqualTo(s.task().value());
            assertThat(overdue.getFirst().envelope().aggregateVersion()).as("assigned = 1, overdue = 2").isEqualTo(2);
            assertThat(overdue.getFirst().envelope().businessDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        });
    }

    @Test
    @DisplayName("platform.approval deadline: a task decided in time is never reminded")
    void decidedInTimeIsNotReminded() {
        Scene s = scene(true);
        decideTasks.execute(new UserId(s.manager()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null)));

        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();
        assertThat(isOverdue(s)).isFalse();
        assertThat(events.overdue().stream().filter(e -> e.taskId().equals(s.task()))).isEmpty();
    }

    @Test
    @DisplayName("platform.approval deadline: still unhandled after the escalation point, the task goes to the holder's superior, who may then act")
    void escalationToTheSuperior() {
        Scene s = scene(true);
        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();

        assertThat(taskService.getIdentityLinksForTask(s.task().value()).stream().filter(l -> "candidate".equals(l.getType()))
                .map(l -> l.getUserId()).toList()).containsExactlyInAnyOrder(String.valueOf(s.manager()), String.valueOf(s.head()));
        verify(wecom, timeout(10_000).times(1)).send(argThat(card -> card.deduplicationKey().equals("approval-task:" + s.task().value() + ":escalated")
                && card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(s.head()))))));
        eventually(() -> {
            ApprovalTaskEscalated escalated = events.escalated().stream().filter(e -> e.taskId().equals(s.task())).findFirst().orElseThrow();
            assertThat(escalated.escalatedTo()).containsExactly(new UserId(s.head()));
            assertThat(escalated.target()).isEqualTo(EscalationTarget.SUPERVISOR);
            assertThat(escalated.envelope().aggregateVersion()).as("assigned, overdue, escalated").isEqualTo(3);
        });
        assertThat(requestStatus(s.key())).isEqualTo("RUNNING");

        decideTasks.execute(new UserId(s.head()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, "covering")));
        assertThat(requestStatus(s.key())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("platform.approval deadline: a claimed task escalates too — the claim is released so the superior can take it")
    void claimedTaskEscalates() {
        Scene s = scene(true);
        claimTask.execute(s.task(), new UserId(s.manager()));
        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();

        assertThat(openTask(s.instance()).getAssignee()).isNull();
        assertThat(jdbc.queryForObject("select status from platform_approval.approval_tasks where task_id = ?", String.class, s.task().value()))
                .isEqualTo("PENDING");
        decideTasks.execute(new UserId(s.head()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null)));
        assertThat(requestStatus(s.key())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("platform.approval deadline: with no superior known the task goes to the process administrator and the alert counter rises")
    void noSuperiorGoesToTheAdministrator() {
        Scene s = scene(false);
        double before = meters.counter("forcitis.platform.approval.task.escalation.administrator").count();
        clock.set(escalateAtOf(s).plusSeconds(1));

        enforceDeadlines.execute();

        assertThat(meters.counter("forcitis.platform.approval.task.escalation.administrator").count()).isEqualTo(before + 1);
        assertThat(taskService.getIdentityLinksForTask(s.task().value()).stream().filter(l -> "candidate".equals(l.getType()))
                .map(l -> l.getUserId()).toList()).contains(String.valueOf(ADMINISTRATOR));
        eventually(() -> assertThat(events.escalated().stream().filter(e -> e.taskId().equals(s.task())).findFirst().orElseThrow()
                .target()).isEqualTo(EscalationTarget.PROCESS_ADMINISTRATOR));
    }

    @Test
    @DisplayName("platform.approval deadline: a late job run records the reminder and the escalation in order, once each")
    void lateRunCatchesUp() {
        Scene s = scene(true);
        clock.set(escalateAtOf(s).plusSeconds(10));

        enforceDeadlines.execute();
        enforceDeadlines.execute();

        eventually(() -> {
            assertThat(events.overdue().stream().filter(e -> e.taskId().equals(s.task()))).hasSize(1);
            assertThat(events.escalated().stream().filter(e -> e.taskId().equals(s.task()))).hasSize(1);
        });
        verify(wecom, after(500).times(1)).send(argThat(card -> card.deduplicationKey().equals("approval-task:" + s.task().value() + ":overdue")));
        verify(wecom, times(1)).send(argThat(card -> card.deduplicationKey().equals("approval-task:" + s.task().value() + ":escalated")));
    }
}
