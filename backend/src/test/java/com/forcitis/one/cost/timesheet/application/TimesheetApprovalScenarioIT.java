package com.forcitis.one.cost.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetCancelled;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
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
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskResulted;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.modulith.test.Scenario;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Submit, claim, decide, the timesheet follows through events; the approval owns the process state, the timesheet its own. */
@EnableScenarios
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.modulith.test.PublishedEventsExtension.class)
class TimesheetApprovalScenarioIT extends ApprovalIntegrationSupport {

    static final AtomicLong TIMESHEETS = new AtomicLong(50_000);
    static final long WORK_PACKAGE = 7_001;

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
    io.micrometer.core.instrument.MeterRegistry meters;

    long employee;
    long manager;

    @BeforeEach
    void people() {
        jdbc.update("insert into delivery_execution.work_packages (id, planned_hours, actual_hours, status) values (?, 400, 10, 'IN_PROGRESS')"
                + " on conflict (id) do nothing", WORK_PACKAGE);
        employee = newId();
        manager = newId();
        organisation.person(employee, 1, null).person(manager, 1, null);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    void signInAs(long user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(user), "x", "ROLE_TEST"));
    }

    static TimesheetId newTimesheet() {
        return new TimesheetId(TIMESHEETS.incrementAndGet());
    }

    SubmissionResult submit(TimesheetId id, String hours) {
        signInAs(employee);
        return submitTimesheet.execute(new SubmitTimesheetCommand(id, new WorkPackageId(WORK_PACKAGE), Hours.of(hours), new UserId(manager)));
    }

    TimesheetView view(TimesheetId id) {
        signInAs(employee);
        return getTimesheet.execute(id);
    }

    ApprovalTaskId taskOf(ApprovalInstanceId instance) {
        return openTaskId(instance);
    }

    @Test
    @DisplayName("RULE-279 submit, claim, decide: the timesheet turns SUBMITTED then APPROVED and publishes its own events, after the approval's")
    void submittedClaimedDecidedApproved(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));

        ApprovalInstanceId[] instance = new ApprovalInstanceId[1];
        scenario.stimulate(() -> {
            SubmissionResult result = submit(id, "8");
            instance[0] = ((SubmissionResult.Submitted) result).approvalInstanceId();
            return result;
        }).andWaitForEventOfType(TaskAssigned.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
            assertThat(e.candidates()).containsExactly(new UserId(manager));
            assertThat(e.envelope().eventType()).isEqualTo("platform.approval.TaskAssigned");
            assertThat(e.envelope().legalEntityId()).isEqualTo(new LegalEntityId(1));
        });
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(view(id).canWithdraw()).isTrue();
        assertThat(published.ofType(TimesheetSubmitted.class).matching(e -> e.document().equals(document))).hasSize(1);
        TimesheetSubmitted submitted = published.ofType(TimesheetSubmitted.class).matching(e -> e.document().equals(document)).iterator().next();
        assertThat(submitted.round()).isEqualTo(1);
        assertThat(submitted.approvalInstanceId()).isEqualTo(instance[0]);
        assertThat(submitted.submittedHours()).isEqualTo(Hours.of("8"));
        assertThat(submitted.envelope().aggregateType()).isEqualTo("Timesheet");
        assertThat(submitted.envelope().aggregateId()).isEqualTo(String.valueOf(id.value()));
        assertThat(submitted.envelope().aggregateVersion()).isEqualTo(1);
        assertThat(submitted.envelope().businessDate()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")));

        ApprovalTaskId task = taskOf(instance[0]);
        scenario.stimulate(() -> claimTask.execute(task, new UserId(manager))).andWaitForEventOfType(TaskClaimed.class)
                .matching(e -> e.taskId().equals(task)).toArriveAndVerify(e -> assertThat(e.claimedBy()).isEqualTo(new UserId(manager)));

        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null))))
                .andWaitForEventOfType(TimesheetApproved.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.round()).isEqualTo(1);
                    assertThat(e.employee()).isEqualTo(new UserId(employee));
                    assertThat(e.submittedHours()).isEqualTo(Hours.of("8"));
                    assertThat(e.envelope().aggregateVersion()).as("submitted = 1, approved = 2").isEqualTo(2);
                    assertThat(e.envelope().eventType()).isEqualTo("cost.timesheet.TimesheetApproved");
                });

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(view(id).canWithdraw()).isFalse();
        assertThat(published.ofType(TaskResulted.class).matching(e -> e.document().equals(document))).hasSize(1);
        ApprovalCompleted completed = published.ofType(ApprovalCompleted.class).matching(e -> e.document().equals(document)).iterator().next();
        assertThat(completed.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
        assertThat(completed.envelope().eventId()).isNotNull();
    }

    @Test
    @DisplayName("RULE-303 the consumer is idempotent per eventId: delivering the same result again changes nothing and publishes nothing")
    void redeliveryChangesNothing(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));
        ApprovalInstanceId instance = ((SubmissionResult.Submitted) submit(id, "6")).approvalInstanceId();
        ApprovalTaskId task = taskOf(instance);

        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null))))
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
    void staleRoundsAreIgnored(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));
        ApprovalInstanceId instance = ((SubmissionResult.Submitted) submit(id, "5")).approvalInstanceId();
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "platform.approval.ApprovalCompleted", 1, Instant.now(), "ApprovalRequest",
                "TIMESHEET:" + id.value(), 9, UUID.randomUUID(), null, ActorType.SYSTEM, "test", new LegalEntityId(1), LocalDate.now(),
                new EventSource(SourceSystem.ONE, null), null, List.of());

        tx.executeWithoutResult(s -> applyApprovalResult.apply(new ApprovalCompleted(envelope, document, ProcessKey.COST_TIMESHEET_APPROVAL, instance, 7,
                ApprovalOutcome.APPROVED, null)));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(published.ofType(TimesheetApproved.class).matching(e -> e.document().equals(document))).isEmpty();
    }

    @Test
    @DisplayName("G-1 an approval result that names no timesheet of ours is counted and logged, never thrown into the retry loop")
    void resultsForUnknownTimesheetsAreCounted() {
        double before = meters.counter("cost.timesheet.approval.result.unknown").count();
        for (String documentId : List.of("not-a-number", "999999999")) {
            EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "platform.approval.ApprovalCompleted", 1, Instant.now(), "ApprovalRequest",
                    "TIMESHEET:" + documentId, 1, UUID.randomUUID(), null, ActorType.SYSTEM, "test", new LegalEntityId(1), LocalDate.now(),
                    new EventSource(SourceSystem.ONE, null), null, List.of());
            tx.executeWithoutResult(s -> applyApprovalResult.apply(new ApprovalCompleted(envelope,
                    new DocumentReference(DocumentType.TIMESHEET, new DocumentId(documentId)), ProcessKey.COST_TIMESHEET_APPROVAL,
                    new ApprovalInstanceId("i"), 1, ApprovalOutcome.APPROVED, null)));
        }
        assertThat(meters.counter("cost.timesheet.approval.result.unknown").count()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("RULE-267/293 a rejection sends the timesheet back to its owner: revise and submit the next round, which is approved")
    void returnedThenRevisedAndApproved(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));
        ApprovalInstanceId first = ((SubmissionResult.Submitted) submit(id, "9")).approvalInstanceId();
        ApprovalTaskId task = taskOf(first);

        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(task, DecisionOutcome.REJECT, "too many hours"))))
                .andWaitForEventOfType(TimesheetReturned.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.reason()).isEqualTo("too many hours");
                    assertThat(e.round()).isEqualTo(1);
                });
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.RETURNED);
        assertThat(published.ofType(TimesheetRejected.class).matching(e -> e.document().equals(document))).isEmpty();

        ApprovalInstanceId second = ((SubmissionResult.Submitted) submit(id, "7.5")).approvalInstanceId();
        assertThat(second).isNotEqualTo(first);
        assertThat(view(id).round()).isEqualTo(2);
        ApprovalTaskId secondTask = taskOf(second);
        scenario.stimulate(() -> decideTasks.execute(new UserId(manager), List.of(new TaskDecision(secondTask, DecisionOutcome.APPROVE, "ok"))))
                .andWaitForEventOfType(TimesheetApproved.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.round()).isEqualTo(2);
                    assertThat(e.submittedHours()).isEqualTo(Hours.of("7.5"));
                });
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
    }

    @Test
    @DisplayName("RULE-269 the employee withdraws a submitted timesheet: it comes back RETURNED and can be submitted again")
    void withdrawnTimesheetIsEditableAgain(Scenario scenario) {
        TimesheetId id = newTimesheet();
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));
        submit(id, "4");

        scenario.stimulate(() -> {
            signInAs(employee);
            withdrawTimesheet.execute(id, "wrong work package");
        }).andWaitForEventOfType(TimesheetReturned.class).matching(e -> e.document().equals(document))
                .toArriveAndVerify(e -> assertThat(e.reason()).isEqualTo("wrong work package"));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.RETURNED);
        assertThat(submit(id, "4.5")).isInstanceOf(SubmissionResult.Submitted.class);
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(view(id).round()).isEqualTo(2);
    }

    @Test
    @DisplayName("RULE-279 cancelling returns the timesheet to a draft that is not a refusal; no TimesheetRejected is ever published")
    void cancelledTimesheetIsADraft(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = new DocumentReference(DocumentType.TIMESHEET, new DocumentId(String.valueOf(id.value())));
        submit(id, "3");

        scenario.stimulate(() -> {
            signInAs(employee);
            cancelTimesheet.execute(id, "duplicate");
        }).andWaitForEventOfType(TimesheetCancelled.class).matching(e -> e.document().equals(document))
                .toArriveAndVerify(e -> assertThat(e.reason()).isEqualTo("duplicate"));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(published.ofType(TimesheetRejected.class).matching(e -> e.document().equals(document))).isEmpty();
        assertThat(published.ofType(TimesheetReturned.class).matching(e -> e.document().equals(document))).isEmpty();
        assertThat(submit(id, "3")).isInstanceOf(SubmissionResult.Submitted.class);
    }

    @Test
    @DisplayName("I-5 only the employee who owns a timesheet may submit, withdraw, cancel or read it")
    void strangersCannotTouchSomeoneElsesTimesheet() {
        TimesheetId id = newTimesheet();
        submit(id, "2");
        long stranger = newId();
        organisation.person(stranger, 1, null);
        signInAs(stranger);

        assertThatThrownBy(() -> submitTimesheet.execute(new SubmitTimesheetCommand(id, new WorkPackageId(WORK_PACKAGE), Hours.of("1"), new UserId(manager))))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> withdrawTimesheet.execute(id, "mine")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> cancelTimesheet.execute(id, "mine")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> getTimesheet.execute(id)).isInstanceOf(AccessDeniedException.class);
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    @Test
    @DisplayName("RULE-266 submitting a timesheet that is already in approval returns the running instance and starts nothing new")
    void submittingTwiceReturnsTheRunningInstance() {
        TimesheetId id = newTimesheet();
        ApprovalInstanceId first = ((SubmissionResult.Submitted) submit(id, "2")).approvalInstanceId();
        ApprovalInstanceId again = ((SubmissionResult.Submitted) submit(id, "2")).approvalInstanceId();

        assertThat(again).isEqualTo(first);
        assertThat(view(id).round()).isEqualTo(1);
    }

    @Test
    @DisplayName("G-1 the submitting legal entity is stored with the timesheet and with its approval; version counts every change")
    void legalEntityAndVersionAreStored() {
        TimesheetId id = newTimesheet();
        submit(id, "1");

        assertThat(jdbc.queryForObject("select legal_entity_id from cost_timesheet.timesheets where id = ?", Long.class, id.value())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select legal_entity_id from platform_approval.approval_requests where document_key = ?", Long.class,
                String.valueOf(id.value()))).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select version from cost_timesheet.timesheets where id = ?", Long.class, id.value())).isEqualTo(0L);
        assertThat(jdbc.queryForObject("select event_count from cost_timesheet.timesheets where id = ?", Long.class, id.value())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select hours from (select submitted_hours as hours from cost_timesheet.timesheets where id = ?) t",
                BigDecimal.class, id.value())).isEqualByComparingTo("1");
    }
}
