package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestWorkCalendar;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.EnforceDeadlines;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * #1546: on a countersign node one person takes one share, also when a share is escalated. The escalation of a share skips a supervisor who holds or
 * decided another share of the round and goes to the next one up; with nobody left it stays overdue with its holder and raises the alert; and a person
 * who already has another share cannot claim this one.
 */
@Import(CountersignEscalationDistinctPeopleIT.Time.class)
class CountersignEscalationDistinctPeopleIT extends ApprovalIntegrationSupport {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
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
    ApprovalEngine engine;

    @Autowired
    MeterRegistry meters;

    @BeforeEach
    void resetTime() {
        calendar.reset();
        clock.set(FRIDAY.atTime(10, 0).atZone(SHANGHAI).toInstant());
        jdbc.update("update platform_approval.approval_tasks set status = 'CLOSED' where status in ('PENDING', 'CLAIMED')");
    }

    /** top supervises a; a supervises b (so b's own supervisor is a); the round has one share for a and one for b. */
    record Scene(ApprovalInstanceId instance, ApprovalTaskId aShare, ApprovalTaskId bShare, long top, long a, long b) {
    }

    private Scene scene(boolean aHasSuperior) {
        long unit = newId();
        long top = newId();
        long initiator = newId();
        long a = newId();
        long b = newId();
        organisation.unit(unit, null, top).person(top, unit, null).person(initiator, unit, top).person(a, unit, aHasSuperior ? top : null).person(b, unit, a);
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(newKey("escalation"), TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(TestAccounts.employee(a).toString(), TestAccounts.employee(b).toString()))))));
        return new Scene(instance, shareOf(instance, a), shareOf(instance, b), top, a, b);
    }

    private ApprovalTaskId shareOf(ApprovalInstanceId instance, long person) {
        return engine.findOpenTasksFor(TestAccounts.account(person)).stream().filter(task -> task.instanceId().equals(instance)).findFirst().orElseThrow()
                .taskId();
    }

    private void aDecidesHerShare(Scene s) {
        actAs(s.a());
        claimTask.execute(s.aShare(), TestAccounts.account(s.a()), OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(s.a()), List.of(new TaskDecision(s.aShare(), DecisionOutcome.APPROVE, null)), OptionalLong.empty());
    }

    private void bClaimsAndTheEscalationTimeComes(Scene s) {
        actAs(s.b());
        claimTask.execute(s.bShare(), TestAccounts.account(s.b()), OptionalLong.empty());
        Instant escalateAt = jdbc.queryForObject("select escalate_at from platform_approval.approval_tasks where task_id = ?",
                java.time.OffsetDateTime.class, s.bShare().value()).toInstant();
        clock.set(escalateAt.plusSeconds(1));
        enforceDeadlines.execute();
    }

    private List<String> candidatesOf(ApprovalTaskId task) {
        return taskService.getIdentityLinksForTask(task.value().toString()).stream().filter(l -> "candidate".equals(l.getType())).map(l -> l.getUserId())
                .toList();
    }

    private String holderOf(ApprovalTaskId task) {
        return taskService.createTaskQuery().taskId(task.value().toString()).singleResult().getAssignee();
    }

    private long escalationsOf(ApprovalTaskId task) {
        return events.escalated().stream().filter(e -> e.taskId().equals(task)).count();
    }

    @Test
    @DisplayName("#1546 a supervisor who already decided another share is skipped: the share goes to the next supervisor up")
    void aSupervisorWhoDecidedAnotherShareIsSkipped() {
        Scene s = scene(true);
        aDecidesHerShare(s);

        bClaimsAndTheEscalationTimeComes(s);

        assertThat(candidatesOf(s.bShare())).as("the next supervisor up, not the decided one").containsExactlyInAnyOrder(
                TestAccounts.account(s.b()).value().toString(), TestAccounts.account(s.top()).value().toString());
        ApprovalTaskEscalated escalated = events.escalated().stream().filter(e -> e.taskId().equals(s.bShare())).findFirst().orElseThrow();
        assertThat(escalated.escalatedTo()).containsExactly(TestAccounts.account(s.top()));
    }

    @Test
    @DisplayName("#1546 a supervisor who holds another open share is skipped too")
    void aSupervisorWhoHoldsAnotherShareIsSkipped() {
        Scene s = scene(true);
        actAs(s.a());
        claimTask.execute(s.aShare(), TestAccounts.account(s.a()), OptionalLong.empty());
        jdbc.update("update platform_approval.approval_tasks set remind_at = ?, escalate_at = ? where task_id = ?", java.sql.Timestamp.from(Instant.parse("2030-01-01T00:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2030-01-02T00:00:00Z")), s.aShare().value());

        bClaimsAndTheEscalationTimeComes(s);

        assertThat(candidatesOf(s.bShare())).doesNotContain(TestAccounts.account(s.a()).value().toString()).contains(TestAccounts.account(s.top()).value().toString());
    }

    @Test
    @DisplayName("#1546 with nobody left up the line the share stays overdue with its holder, is not handed to an administrator, and raises the alert")
    void withNobodyLeftTheShareStaysOverdueWithItsHolder() {
        Scene s = scene(false);
        aDecidesHerShare(s);
        double unresolved = meters.counter("forcitis.platform.approval.task.escalation.unresolved").count();
        double administrator = meters.counter("forcitis.platform.approval.task.escalation.administrator").count();

        bClaimsAndTheEscalationTimeComes(s);

        assertThat(holderOf(s.bShare())).as("still claimed by its holder").isEqualTo(TestAccounts.account(s.b()).value().toString());
        assertThat(candidatesOf(s.bShare())).doesNotContain(TestAccounts.account(ADMINISTRATOR).value().toString(), TestAccounts.account(s.a()).value().toString());
        assertThat(escalationsOf(s.bShare())).isZero();
        assertThat(jdbc.queryForObject("select overdue_at is not null from platform_approval.approval_tasks where task_id = ?", Boolean.class,
                s.bShare().value())).as("it is overdue").isTrue();
        assertThat(meters.counter("forcitis.platform.approval.task.escalation.unresolved").count()).isEqualTo(unresolved + 1);
        assertThat(meters.counter("forcitis.platform.approval.task.escalation.administrator").count()).isEqualTo(administrator);
    }

    @Test
    @DisplayName("#1546 somebody who already decided another share cannot claim this one, even if made a candidate of it")
    void aPersonWithAnotherShareCannotClaim() {
        Scene s = scene(true);
        aDecidesHerShare(s);
        engine.addCandidates(s.bShare(), List.of(TestAccounts.account(s.a())));
        actAs(s.a());

        assertThatThrownBy(() -> claimTask.execute(s.bShare(), TestAccounts.account(s.a()), OptionalLong.empty())).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NODE_TASK_DUPLICATE));
        assertThat(holderOf(s.bShare())).isNull();
    }

    @Test
    @DisplayName("#1546 an unclaimed share is not handed to an administrator who holds another share of the round")
    void anAdministratorWithAnotherShareDoesNotTakeAnUnclaimedShare() {
        Scene s = scene(true);
        engine.addCandidates(s.aShare(), List.of(TestAccounts.account(ADMINISTRATOR)));
        actAs(ADMINISTRATOR);
        claimTask.execute(s.aShare(), TestAccounts.account(ADMINISTRATOR), OptionalLong.empty());
        jdbc.update("update platform_approval.approval_tasks set remind_at = ?, escalate_at = ? where task_id = ?", java.sql.Timestamp.from(Instant.parse("2030-01-01T00:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2030-01-02T00:00:00Z")), s.aShare().value());
        double unresolved = meters.counter("forcitis.platform.approval.task.escalation.unresolved").count();
        Instant escalateAt = jdbc.queryForObject("select escalate_at from platform_approval.approval_tasks where task_id = ?",
                java.time.OffsetDateTime.class, s.bShare().value()).toInstant();

        clock.set(escalateAt.plusSeconds(1));
        enforceDeadlines.execute();

        assertThat(candidatesOf(s.bShare())).doesNotContain(TestAccounts.account(ADMINISTRATOR).value().toString());
        assertThat(escalationsOf(s.bShare())).isZero();
        assertThat(meters.counter("forcitis.platform.approval.task.escalation.unresolved").count()).isEqualTo(unresolved + 1);
    }
}
