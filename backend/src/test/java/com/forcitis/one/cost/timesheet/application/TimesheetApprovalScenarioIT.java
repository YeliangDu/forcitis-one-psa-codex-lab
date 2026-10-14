package com.forcitis.one.cost.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetCancelled;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.Scenario;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Submit changes the timesheet only and publishes the fact; the approval starts from that event, the timesheet follows the result
 * through events. The approval owns the process state, the timesheet its own. Other modules' data comes from doubles (work packages,
 * numbering, organisation); what is asserted is what the timesheet module did and published.
 */
@EnableScenarios
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.modulith.test.PublishedEventsExtension.class)
class TimesheetApprovalScenarioIT extends ApprovalIntegrationSupport {

    static final AtomicLong TIMESHEETS = new AtomicLong(50_000);
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    SubmitTimesheet submitTimesheet;

    @Autowired
    WithdrawTimesheet withdrawTimesheet;

    @Autowired
    CancelTimesheet cancelTimesheet;

    @Autowired
    GetTimesheet getTimesheet;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ApplyApprovalResult applyApprovalResult;

    @Autowired
    StartApprovalOnSubmission startApprovalOnSubmission;

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meters;

    long employee;
    long manager;

    @BeforeEach
    void people() {
        employee = newId();
        manager = newId();
        organisation.person(employee, 1, null).person(manager, 1, null);
    }

    @AfterEach
    void signOutAgain() {
        SecurityContextHolder.clearContext();
    }

    void signInAs(long user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(user), "x", "ROLE_TEST"));
    }

    static TimesheetId newTimesheet() {
        return new TimesheetId(TIMESHEETS.incrementAndGet());
    }

    static LocalDate workDay() {
        return LocalDate.now(ZONE).minusDays(1);
    }

    static DocumentReference documentOf(TimesheetId id) {
        return new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));
    }

    TimesheetView submit(TimesheetId id, String hours) {
        return submit(id, workDay(), hours);
    }

    TimesheetView submit(TimesheetId id, LocalDate workDate, String hours) {
        signInAs(employee);
        return submitTimesheet.execute(new SubmitTimesheetCommand(id, workDate, Hours.of(hours), new UserId(manager)));
    }

    /** Submits and waits until the approval has started from the event: its first task has been assigned. */
    TimesheetView submitAndAwaitApproval(Scenario scenario, TimesheetId id, LocalDate workDate, String hours) {
        java.util.concurrent.atomic.AtomicReference<TimesheetView> answer = new java.util.concurrent.atomic.AtomicReference<>();
        DocumentReference document = documentOf(id);
        scenario.stimulate(() -> answer.updateAndGet(previous -> submit(id, workDate, hours)))
                .andWaitForEventOfType(TaskAssigned.class).matching(e -> e.document().equals(document)).toArrive();
        return answer.get();
    }

    TimesheetView submitAndAwaitApproval(Scenario scenario, TimesheetId id, String hours) {
        return submitAndAwaitApproval(scenario, id, workDay(), hours);
    }

    TimesheetView view(TimesheetId id) {
        signInAs(employee);
        return getTimesheet.execute(id);
    }

    ApprovalTaskId taskOf(TimesheetId id) {
        return new ApprovalTaskId(taskService.createTaskQuery().processInstanceBusinessKey("TIMESHEET:" + id.value()).singleResult().getId());
    }

    static void domainError(Throwable thrown, TimesheetError expected) {
        assertThat(thrown).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("H-1 RULE-279 submit only changes the timesheet; the approval starts from its event; claim, decide: the timesheet turns SUBMITTED then APPROVED and publishes its own events")
    void submittedClaimedDecidedApproved(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);

        TimesheetView submitted = submitAndAwaitApproval(scenario, id, "8");
        assertThat(submitted.status()).as("the answer to the submit is the timesheet, not an approval instance").isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(submitted.round()).isEqualTo(1);
        assertThat(submitted.number().value()).startsWith("TS-T-");
        assertThat(events.of(TaskAssigned.class)).as("recorded on the listener's thread, not here").isEmpty();
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(view(id).canWithdraw()).isTrue();
        TimesheetSubmitted event = published.ofType(TimesheetSubmitted.class).matching(e -> e.document().equals(document)).iterator().next();
        assertThat(event.round()).isEqualTo(new RoundNumber(1));
        assertThat(event.submittedHours()).isEqualTo(Hours.of("8"));
        assertThat(event.envelope().aggregateType()).isEqualTo("Timesheet");
        assertThat(event.envelope().aggregateId()).isEqualTo(String.valueOf(id.value()));
        assertThat(event.envelope().aggregateVersion()).isEqualTo(1);
        assertThat(event.envelope().businessDate()).as("the business date of the fact is the work date, not the day of submission").isEqualTo(workDay());
        assertThat(event.submittedOn()).isEqualTo(LocalDate.now(ZONE));

        ApprovalTaskId task = taskOf(id);
        scenario.stimulate(() -> claimTask.execute(task, new UserId(manager), java.util.OptionalLong.empty())).andWaitForEventOfType(TaskClaimed.class)
                .matching(e -> e.taskId().equals(task)).toArriveAndVerify(e -> assertThat(e.claimedBy()).isEqualTo(new UserId(manager)));

        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty()))
                .andWaitForEventOfType(TimesheetApproved.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.round()).isEqualTo(new RoundNumber(1));
                    assertThat(e.employee()).isEqualTo(new UserId(employee));
                    assertThat(e.submittedHours()).isEqualTo(Hours.of("8"));
                    assertThat(e.envelope().aggregateVersion()).as("submitted = 1, approved = 2").isEqualTo(2);
                    assertThat(e.envelope().eventType()).isEqualTo("cost.timesheet.TimesheetApproved");
                    assertThat(e.envelope().actorType()).as("the result is applied by the listener, not by a person").isEqualTo(ActorType.SYSTEM);
                });

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(view(id).canWithdraw()).isFalse();
        ApprovalCompleted completed = published.ofType(ApprovalCompleted.class).matching(e -> e.document().equals(document)).iterator().next();
        assertThat(completed.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
    }

    @Test
    @DisplayName("D-1a-78 the approval starts once per (document, round): the submitted event delivered again starts nothing new")
    void redeliveredSubmissionStartsNothingNew(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "3");
        TimesheetSubmitted event = published.ofType(TimesheetSubmitted.class).matching(e -> e.document().equals(document)).iterator().next();
        String instance = jdbc.queryForObject("select current_instance_id from platform_approval.approval_requests where document_key = ?",
                String.class, String.valueOf(id.value()));

        startApprovalOnSubmission.on(event);
        startApprovalOnSubmission.on(event);

        assertThat(jdbc.queryForObject("select current_instance_id from platform_approval.approval_requests where document_key = ?", String.class,
                String.valueOf(id.value()))).isEqualTo(instance);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where document_key = ?", Integer.class,
                String.valueOf(id.value()))).isEqualTo(1);
        assertThat(taskService.createTaskQuery().processInstanceId(instance).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-303 the consumer is idempotent per eventId: delivering the same result again changes nothing and publishes nothing")
    void redeliveryChangesNothing(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "6");
        ApprovalTaskId task = taskOf(id);

        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty()))
                .andWaitForEventOfType(TimesheetApproved.class).matching(e -> e.document().equals(document)).toArrive();
        ApprovalCompleted original = published.ofType(ApprovalCompleted.class).matching(e -> e.document().equals(document)).iterator().next();

        tx.executeWithoutResult(s -> applyApprovalResult.apply(original));
        tx.executeWithoutResult(s -> applyApprovalResult.apply(original));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(published.ofType(TimesheetApproved.class).matching(e -> e.document().equals(document))).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.consumed_events where event_id = ?", Integer.class,
                original.envelope().eventId())).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-267/279 a result of a round the timesheet is not waiting for (older, or already applied) changes nothing even with a new event id")
    void staleRoundsAreIgnored(PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submit(id, "5");
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "platform.approval.ApprovalCompleted", 1, Instant.now(), "ApprovalRequest",
                "TIMESHEET:" + id.value(), 9, UUID.randomUUID(), null, ActorType.SYSTEM, "test", LEGAL_ENTITY, LocalDate.now(),
                new EventSource(SourceSystem.ONE, null), null, List.of());

        tx.executeWithoutResult(s -> applyApprovalResult.apply(new ApprovalCompleted(envelope, document, ProcessKey.COST_TIMESHEET_APPROVAL,
                new ApprovalInstanceId("i"), new RoundNumber(7), new UserId(employee), ApprovalOutcome.APPROVED, null, null)));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(published.ofType(TimesheetApproved.class).matching(e -> e.document().equals(document))).isEmpty();
    }

    @Test
    @DisplayName("G-1 an approval result that names no timesheet of ours is counted and logged, never thrown into the retry loop")
    void resultsForUnknownTimesheetsAreCounted() {
        double before = meters.counter("forcitis.cost.timesheet.approval.result.unknown").count();
        for (String documentId : List.of("not-a-number", "999999999")) {
            EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "platform.approval.ApprovalCompleted", 1, Instant.now(), "ApprovalRequest",
                    "TIMESHEET:" + documentId, 1, UUID.randomUUID(), null, ActorType.SYSTEM, "test", LEGAL_ENTITY, LocalDate.now(),
                    new EventSource(SourceSystem.ONE, null), null, List.of());
            tx.executeWithoutResult(s -> applyApprovalResult.apply(new ApprovalCompleted(envelope,
                    new DocumentReference(DocumentType.TIMESHEET, new DocumentId(documentId)), ProcessKey.COST_TIMESHEET_APPROVAL,
                    new ApprovalInstanceId("i"), new RoundNumber(1), new UserId(employee), ApprovalOutcome.APPROVED, null, null)));
        }
        assertThat(meters.counter("forcitis.cost.timesheet.approval.result.unknown").count()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("RULE-267/293 D-1a-126 a rejection sends the timesheet back to its owner: revise and submit the next round, which is approved; the work date stays")
    void returnedThenRevisedAndApproved(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        LocalDate workDate = workDay();
        submitAndAwaitApproval(scenario, id, workDate, "9");
        ApprovalTaskId task = taskOf(id);

        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.REJECT, "too many hours")), java.util.OptionalLong.empty()))
                .andWaitForEventOfType(TimesheetReturned.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.reason()).isEqualTo(new Reason("too many hours"));
                    assertThat(e.round()).isEqualTo(new RoundNumber(1));
                });
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.RETURNED);
        assertThat(published.ofType(TimesheetRejected.class).matching(e -> e.document().equals(document))).isEmpty();

        assertThatThrownBy(() -> submit(id, workDate.minusDays(1), "7.5")).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_WORK_DATE_FIXED));
        scenario.stimulate(() -> submit(id, "7.5")).andWaitForEventOfType(TaskAssigned.class)
                .matching(e -> e.document().equals(document) && !e.taskId().equals(task)).toArrive();
        assertThat(view(id).round()).isEqualTo(2);
        assertThat(view(id).workDate()).as("submitting again does not move the hours to another day").isEqualTo(workDate);
        ApprovalTaskId secondTask = taskOf(id);
        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(secondTask, DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty()))
                .andWaitForEventOfType(TimesheetApproved.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.round()).isEqualTo(new RoundNumber(2));
                    assertThat(e.submittedHours()).isEqualTo(Hours.of("7.5"));
                });
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("RULE-269 D-1a-72 the employee withdraws a submitted timesheet: it comes back RETURNED and can be submitted again")
    void withdrawnTimesheetIsEditableAgain(Scenario scenario) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "4");

        scenario.stimulate(() -> {
            signInAs(employee);
            withdrawTimesheet.execute(id, "wrong work package", java.util.OptionalLong.empty());
        }).andWaitForEventOfType(TimesheetReturned.class).matching(e -> e.document().equals(document))
                .toArriveAndVerify(e -> assertThat(e.reason()).isEqualTo(new Reason("wrong work package")));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.RETURNED);
        assertThat(submit(id, "4.5").status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(view(id).round()).isEqualTo(2);
    }

    @Test
    @DisplayName("M-2 a round that ended in the approval but is not applied to the timesheet yet cannot be submitted again; the answer says so (409)")
    void resubmittingBeforeTheResultArrivedIsRefused(Scenario scenario) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "4");

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> tx.executeWithoutResult(s -> {
            signInAs(employee);
            withdrawTimesheet.execute(id, "wrong hours", java.util.OptionalLong.empty());
            submit(id, "4");
        }));

        domainError(thrown, TimesheetError.COST_TIMESHEET_RESULT_PENDING);
    }

    @Test
    @DisplayName("RULE-279 cancelling returns the timesheet to a draft that is not a refusal; no TimesheetRejected is ever published")
    void cancelledTimesheetIsADraft(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "3");

        scenario.stimulate(() -> {
            signInAs(employee);
            cancelTimesheet.execute(id, "duplicate", java.util.OptionalLong.empty());
        }).andWaitForEventOfType(TimesheetCancelled.class).matching(e -> e.document().equals(document))
                .toArriveAndVerify(e -> assertThat(e.reason()).isEqualTo(new Reason("duplicate")));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(published.ofType(TimesheetRejected.class).matching(e -> e.document().equals(document))).isEmpty();
        assertThat(published.ofType(TimesheetReturned.class).matching(e -> e.document().equals(document))).isEmpty();
        assertThat(submit(id, "3").status()).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("I-5 only the employee who owns a timesheet may submit, withdraw, cancel or read it")
    void strangersCannotTouchSomeoneElsesTimesheet() {
        TimesheetId id = newTimesheet();
        submit(id, "2");
        long stranger = newId();
        organisation.person(stranger, 1, null);
        signInAs(stranger);

        assertThatThrownBy(() -> submitTimesheet.execute(new SubmitTimesheetCommand(id, workDay(), Hours.of("1"),
                new UserId(manager)))).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_OWNER));
        assertThatThrownBy(() -> withdrawTimesheet.execute(id, "mine", java.util.OptionalLong.empty())).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_OWNER));
        assertThatThrownBy(() -> cancelTimesheet.execute(id, "mine", java.util.OptionalLong.empty())).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_OWNER));
        assertThatThrownBy(() -> getTimesheet.execute(id)).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_OWNER));
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("RULE-266 submitting a timesheet that is already in approval answers with it as it is and records nothing new")
    void submittingTwiceChangesNothing(PublishedEvents published) {
        TimesheetId id = newTimesheet();
        TimesheetView first = submit(id, "2");
        TimesheetView again = submit(id, "2");

        assertThat(again.number()).isEqualTo(first.number());
        assertThat(again.round()).isEqualTo(1);
        assertThat(published.ofType(TimesheetSubmitted.class).matching(e -> e.document().equals(documentOf(id)))).hasSize(1);
    }

    @Test
    @DisplayName("H-6 the work date cannot be after today; K1/K2 the timesheet keeps its number and the department of the employee")
    void workDateAndTheNumberAndDepartmentAreStored() {
        TimesheetId future = newTimesheet();
        assertThatThrownBy(() -> submit(future, LocalDate.now(ZONE).plusDays(1), "1"))
                .satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_WORK_DATE_IN_FUTURE));
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where id = ?", Integer.class, future.value())).isZero();

        TimesheetId id = newTimesheet();
        TimesheetView view = submit(id, "1");

        assertThat(jdbc.queryForMap("select timesheet_number, org_unit_id, work_date, submitted_date, legal_entity_id from cost_timesheet.timesheets"
                + " where id = ?", id.value())).containsEntry("timesheet_number", view.number().value()).containsEntry("org_unit_id", 1L)
                .containsEntry("work_date", java.sql.Date.valueOf(workDay())).containsEntry("submitted_date", java.sql.Date.valueOf(LocalDate.now(ZONE)))
                .containsEntry("legal_entity_id", LEGAL_ENTITY.value());
        assertThat(view.period()).isEqualTo(com.forcitis.one.kernel.AccountingPeriod.containing(workDay()));
    }

    @Test
    @DisplayName("DDD-3 the lock version and the event sequence are stored apart: a submit is one save and one event")
    void versionAndEventSequenceAreStored() {
        TimesheetId id = newTimesheet();
        submit(id, "1");

        assertThat(jdbc.queryForMap("select version, event_count from cost_timesheet.timesheets where id = ?", id.value()))
                .containsEntry("version", 1L).containsEntry("event_count", 1L);
        assertThat(jdbc.queryForObject("select submitted_hours from cost_timesheet.timesheets where id = ?", BigDecimal.class, id.value()))
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("two first submissions of the same timesheet at once create one timesheet, one number and one TimesheetSubmitted")
    void concurrentFirstSubmissionsOfOneTimesheetCreateOne() throws Exception {
        TimesheetId id = newTimesheet();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> results = List.of(pool.submit(() -> submitOnThread(id, go)), pool.submit(() -> submitOnThread(id, go)));
            go.countDown();
            for (Future<Object> result : results) {
                assertThat(result.get()).isInstanceOf(TimesheetView.class);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where id = ?", Integer.class, id.value())).isEqualTo(1);
        assertThat(jdbc.queryForMap("select version, event_count from cost_timesheet.timesheets where id = ?", id.value()))
                .containsEntry("version", 1L).containsEntry("event_count", 1L);
    }

    Object submitOnThread(TimesheetId id, CountDownLatch go) throws InterruptedException {
        go.await();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(employee), "x", "ROLE_TEST"));
        try {
            return submitTimesheet.execute(new SubmitTimesheetCommand(id, workDay(), Hours.of("6"), new UserId(manager)));
        } catch (RuntimeException refused) {
            return refused;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
