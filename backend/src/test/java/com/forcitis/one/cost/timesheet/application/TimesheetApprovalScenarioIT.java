package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetApprovalWithdrawn;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.events.DocumentSubmittedForApproval;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
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
    com.forcitis.one.platform.approval.application.ListInbox listInbox;

    @Autowired
    ApplyApprovalResult applyApprovalResult;

    @Autowired
    com.forcitis.one.platform.approval.api.ApprovalProcesses approvalProcesses;

    @Autowired
    TimesheetRepository timesheetRepository;

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
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(TestAccounts.account(user).value().toString(), "x", "ROLE_TEST"));
    }

    static TimesheetId newTimesheet() {
        return new TimesheetId(TestIds.uuid(TIMESHEETS.incrementAndGet()));
    }

    static LocalDate workDay() {
        return BusinessDay.today().minusDays(1);
    }

    static DocumentReference documentOf(TimesheetId id) {
        return new DocumentReference(new DocumentTypeCode("TIMESHEET"), new DocumentId(id.value()));
    }

    TimesheetView submit(TimesheetId id, String hours) {
        return submit(id, workDay(), hours);
    }

    TimesheetView submit(TimesheetId id, LocalDate workDate, String hours) {
        signInAs(employee);
        return submitTimesheet.execute(new SubmitTimesheetCommand(id, workDate, Hours.of(hours), TestAccounts.employee(manager)));
    }

    /** Submits and waits until the approval has started from the event: its first task has been assigned. */
    TimesheetView submitAndAwaitApproval(Scenario scenario, TimesheetId id, LocalDate workDate, String hours) {
        java.util.concurrent.atomic.AtomicReference<TimesheetView> answer = new java.util.concurrent.atomic.AtomicReference<>();
        DocumentReference document = documentOf(id);
        scenario.stimulate(() -> answer.updateAndGet(previous -> submit(id, workDate, hours)))
                .andWaitForEventOfType(TaskAssigned.class).matching(e -> e.document().equals(document)).toArrive();
        awaitApprovalCommitted(document, 1);
        return answer.get();
    }

    /** TaskAssigned is published inside the listener's transaction, so a second thread can still find no approval of that round when the event arrives. */
    void awaitApprovalCommitted(DocumentReference document, int round) {
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).pollInterval(java.time.Duration.ofMillis(50))
                .until(() -> approvalProcesses.progress(TimesheetApprovalProcess.KEY, document)
                        .filter(progress -> progress.round().equals(new RoundNumber(round))).isPresent());
    }

    TimesheetView submitAndAwaitApproval(Scenario scenario, TimesheetId id, String hours) {
        return submitAndAwaitApproval(scenario, id, workDay(), hours);
    }

    TimesheetView view(TimesheetId id) {
        signInAs(employee);
        return getTimesheet.execute(id);
    }

    ApprovalTaskId taskOf(TimesheetId id) {
        return new ApprovalTaskId(UUID.fromString(inboxOf(id).getFirst().taskId()));
    }

    List<com.forcitis.one.platform.approval.application.InboxItem> inboxOf(TimesheetId id) {
        return listInbox.execute(TestAccounts.account(manager), com.forcitis.one.platform.approval.application.ListPage.first()).items().stream()
                .filter(item -> item.documentId().filter(id.value()::equals).isPresent()).toList();
    }

    static void domainError(Throwable thrown, com.forcitis.one.kernel.ErrorCode expected) {
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
        assertThat(event.envelope().aggregateId()).isEqualTo(id.value());
        assertThat(event.envelope().aggregateVersion()).isEqualTo(1);
        assertThat(event.envelope().businessDate()).as("the business date of the fact is the work date, not the day of submission").isEqualTo(workDay());
        assertThat(event.submittedOn()).isEqualTo(BusinessDay.today());

        ApprovalTaskId task = taskOf(id);
        scenario.stimulate(() -> claimTask.execute(task, TestAccounts.account(manager), java.util.OptionalLong.empty())).andWaitForEventOfType(TaskClaimed.class)
                .matching(e -> e.taskId().equals(task)).toArriveAndVerify(e -> assertThat(e.claimedBy()).isEqualTo(TestAccounts.account(manager)));

        scenario.stimulate(() -> decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty()))
                .andWaitForEventOfType(TimesheetApproved.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.round()).isEqualTo(new RoundNumber(1));
                    assertThat(e.employee()).isEqualTo(TestAccounts.employee(employee));
                    assertThat(e.submittedHours()).isEqualTo(Hours.of("8"));
                    assertThat(e.envelope().aggregateVersion()).as("submitted = 1, approval requested = 2, approved = 3").isEqualTo(3);
                    assertThat(e.envelope().eventType()).isEqualTo("cost.timesheet.TimesheetApproved");
                    assertThat(e.envelope().actorType()).as("the result is applied by the listener, not by a person").isEqualTo(ActorType.SYSTEM);
                });

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.APPROVED);
        assertThat(view(id).canWithdraw()).isFalse();
        ApprovalCompleted completed = published.ofType(ApprovalCompleted.class).matching(e -> e.document().equals(document)).iterator().next();
        assertThat(completed.outcome()).isEqualTo(ApprovalOutcome.APPROVED);
    }

    @Test
    @DisplayName("D-1a-78 the approval starts once per (document, round): the request delivered again starts nothing new")
    void redeliveredSubmissionStartsNothingNew(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "3");
        DocumentSubmittedForApproval event = published.ofType(DocumentSubmittedForApproval.class).matching(e -> e.document().equals(document)).iterator().next();
        ApprovalStartRequest again = new ApprovalStartRequest(event.processKey(), event.document(), event.envelope().legalEntityId(), event.initiator(),
                event.round(), event.envelope().eventId(), java.util.Set.of("projectHours"), event.formFields(), event.contentHash(), event.summary());
        tx.executeWithoutResult(s -> approvalProcesses.start(again));
        tx.executeWithoutResult(s -> approvalProcesses.start(again));

        assertThat(inboxOf(id)).as("the first start opened one task and the repeats opened nothing").hasSize(1);
        assertThat(approvalProcesses.progress(TimesheetApprovalProcess.KEY, document)).hasValueSatisfying(
                progress -> assertThat(progress.round()).isEqualTo(new RoundNumber(1)));
    }

    @Test
    @DisplayName("RULE-303 the consumer is idempotent per eventId: delivering the same result again changes nothing and publishes nothing")
    void redeliveryChangesNothing(Scenario scenario, PublishedEvents published) {
        TimesheetId id = newTimesheet();
        DocumentReference document = documentOf(id);
        submitAndAwaitApproval(scenario, id, "6");
        ApprovalTaskId task = taskOf(id);

        scenario.stimulate(() -> decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty()))
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
        EventEnvelope envelope = envelope("platform.approval.ApprovalCompleted", "ApprovalRequest", UUID.randomUUID(), 9);

        tx.executeWithoutResult(s -> applyApprovalResult.apply(new ApprovalCompleted(envelope, document, TimesheetApprovalProcess.KEY,
                com.forcitis.one.TestApprovalIds.instance("i"), new RoundNumber(7), TestAccounts.employee(employee), ApprovalOutcome.APPROVED, null, null)));

        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(published.ofType(TimesheetApproved.class).matching(e -> e.document().equals(document))).isEmpty();
    }

    @Test
    @DisplayName("G-1 an approval result that names no timesheet of ours is counted and logged, never thrown into the retry loop")
    void resultsForUnknownTimesheetsAreCounted() {
        double before = meters.counter("forcitis.cost.timesheet.approval.result.unknown").count();
        for (UUID documentId : List.of(com.forcitis.one.TestIds.uuid(900001), com.forcitis.one.TestIds.uuid(900002))) {
            EventEnvelope envelope = envelope("platform.approval.ApprovalCompleted", "ApprovalRequest", UUID.randomUUID(), 1);
            tx.executeWithoutResult(s -> applyApprovalResult.apply(new ApprovalCompleted(envelope,
                    new DocumentReference(new DocumentTypeCode("TIMESHEET"), new DocumentId(documentId)), TimesheetApprovalProcess.KEY,
                    com.forcitis.one.TestApprovalIds.instance("i"), new RoundNumber(1), TestAccounts.employee(employee), ApprovalOutcome.APPROVED, null, null)));
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

        scenario.stimulate(() -> decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, DecisionOutcome.REJECT, "too many hours")), java.util.OptionalLong.empty()))
                .andWaitForEventOfType(TimesheetReturned.class).matching(e -> e.document().equals(document)).toArriveAndVerify(e -> {
                    assertThat(e.reason()).isEqualTo(new Reason("too many hours"));
                    assertThat(e.round()).isEqualTo(new RoundNumber(1));
                });
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.RETURNED);
        assertThat(published.ofType(TimesheetRejected.class).matching(e -> e.document().equals(document))).isEmpty();

        assertThatThrownBy(() -> submit(id, workDate.minusDays(1), "7.5")).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_WORK_DATE_FIXED));
        scenario.stimulate(() -> submit(id, "7.5")).andWaitForEventOfType(TaskAssigned.class)
                .matching(e -> e.document().equals(document) && !e.taskId().equals(task)).toArrive();
        awaitApprovalCommitted(document, 2);
        assertThat(view(id).round()).isEqualTo(2);
        assertThat(view(id).workDate()).as("submitting again does not move the hours to another day").isEqualTo(workDate);
        ApprovalTaskId secondTask = taskOf(id);
        scenario.stimulate(() -> decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(secondTask, DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty()))
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

    // RULE-478
    @Test
    @DisplayName("M-2 a round that ended in the approval but is not applied to the timesheet yet cannot be submitted again; the answer says so (409)")
    void resubmittingBeforeTheResultArrivedIsRefused(Scenario scenario) {
        TimesheetId id = newTimesheet();
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
        }).andWaitForEventOfType(TimesheetApprovalWithdrawn.class).matching(e -> e.document().equals(document))
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
                TestAccounts.employee(manager)))).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_FOUND));
        assertThatThrownBy(() -> withdrawTimesheet.execute(id, "mine", java.util.OptionalLong.empty())).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_FOUND));
        assertThatThrownBy(() -> cancelTimesheet.execute(id, "mine", java.util.OptionalLong.empty())).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_FOUND));
        assertThatThrownBy(() -> getTimesheet.execute(id)).satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_NOT_FOUND));
        assertThat(view(id).status()).isEqualTo(TimesheetStatus.SUBMITTED);
    }

    // RULE-479
    @Test
    @DisplayName("M-12 someone else's timesheet and a timesheet that does not exist are answered the same: same code, same text")
    void foreignAndMissingTimesheetsAreIndistinguishable() {
        TimesheetId id = newTimesheet();
        submit(id, "2");
        long stranger = newId();
        organisation.person(stranger, 1, null);
        signInAs(stranger);

        Throwable foreign = org.assertj.core.api.Assertions.catchThrowable(() -> getTimesheet.execute(id));
        Throwable missing = org.assertj.core.api.Assertions.catchThrowable(() -> getTimesheet.execute(newTimesheet()));
        Throwable foreignCancel = org.assertj.core.api.Assertions.catchThrowable(() -> cancelTimesheet.execute(id, "x", java.util.OptionalLong.empty()));
        Throwable missingCancel = org.assertj.core.api.Assertions.catchThrowable(() -> cancelTimesheet.execute(newTimesheet(), "x", java.util.OptionalLong.empty()));

        domainError(foreign, TimesheetError.COST_TIMESHEET_NOT_FOUND);
        domainError(missing, TimesheetError.COST_TIMESHEET_NOT_FOUND);
        assertThat(foreign.getMessage()).isEqualTo(missing.getMessage());
        assertThat(foreignCancel.getMessage()).isEqualTo(missingCancel.getMessage()).isEqualTo(missing.getMessage());
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
        assertThatThrownBy(() -> submit(future, BusinessDay.today().plusDays(1), "1"))
                .satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_WORK_DATE_IN_FUTURE));
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where id = ?", Integer.class, future.value())).isZero();

        TimesheetId id = newTimesheet();
        TimesheetView view = submit(id, "1");

        assertThat(jdbc.queryForMap("select timesheet_number, org_unit_id, work_date, submitted_date, legal_entity_id from cost_timesheet.timesheets"
                + " where id = ?", id.value())).containsEntry("timesheet_number", view.number().value()).containsEntry("org_unit_id", TestOrgIds.orgUnit(1).value())
                .containsEntry("work_date", java.sql.Date.valueOf(workDay())).containsEntry("submitted_date", java.sql.Date.valueOf(BusinessDay.today()))
                .containsEntry("legal_entity_id", LEGAL_ENTITY.value());
        assertThat(view.period()).isEqualTo(com.forcitis.one.kernel.AccountingPeriod.containing(workDay()));
    }

    @Test
    @DisplayName("DDD-3 the lock version and the event sequence are stored apart: a submit is one save and one event")
    void versionAndEventSequenceAreStored() {
        TimesheetId id = newTimesheet();
        submit(id, "1");

        assertThat(jdbc.queryForMap("select version, event_count from cost_timesheet.timesheets where id = ?", id.value()))
                .containsEntry("version", 1L).containsEntry("event_count", 2L);
        assertThat(jdbc.queryForObject("select submitted_hours from cost_timesheet.timesheets where id = ?", BigDecimal.class, id.value()))
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("an unknown or separated project manager is refused at submit with its own code, and nothing is stored")
    void unknownProjectManagerIsRefused() {
        TimesheetId id = newTimesheet();
        signInAs(employee);
        long nobody = newId();

        assertThatThrownBy(() -> submitTimesheet.execute(new SubmitTimesheetCommand(id, workDay(), Hours.of("1"), TestAccounts.employee(nobody))))
                .satisfies(thrown -> domainError(thrown, TimesheetError.COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN));
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where id = ?", Integer.class, id.value())).isZero();
    }

    // RULE-482
    @Test
    @DisplayName("an employee without a legal entity on the work date cannot submit: the refusal comes from workforce and nothing is stored")
    void missingLegalEntityIsRefused() {
        TimesheetId id = newTimesheet();
        org.mockito.Mockito.when(employeeLegalEntities.on(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenThrow(
                new DomainException(com.forcitis.one.organization.workforce.api.WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED,
                        com.forcitis.one.kernel.ErrorKind.UNPROCESSABLE, "none"));

        assertThatThrownBy(() -> submit(id, "1")).satisfies(thrown -> domainError(thrown,
                com.forcitis.one.organization.workforce.api.WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED));
        assertThat(jdbc.queryForObject("select count(*) from cost_timesheet.timesheets where id = ?", Integer.class, id.value())).isZero();
    }

    @Test
    @DisplayName("a stale copy of a timesheet cannot be saved over the newer one")
    void aStaleCopyIsNotSaved() {
        TimesheetId id = newTimesheet();
        submit(id, "2");
        com.forcitis.one.cost.timesheet.domain.Timesheet first = timesheetRepository.find(id).orElseThrow();
        com.forcitis.one.cost.timesheet.domain.Timesheet stale = timesheetRepository.find(id).orElseThrow();
        first.cancelSubmission(new Reason("first"));
        stale.cancelSubmission(new Reason("second"));

        tx.executeWithoutResult(s -> timesheetRepository.save(first));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> timesheetRepository.save(stale)))
                .satisfies(thrown -> domainError(thrown, KernelError.KERNEL_CONCURRENT_CHANGE));
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
                .containsEntry("version", 1L).containsEntry("event_count", 2L);
    }

    Object submitOnThread(TimesheetId id, CountDownLatch go) throws InterruptedException {
        go.await();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(TestAccounts.account(employee).value().toString(), "x", "ROLE_TEST"));
        try {
            return submitTimesheet.execute(new SubmitTimesheetCommand(id, workDay(), Hours.of("6"), TestAccounts.employee(manager)));
        } catch (RuntimeException refused) {
            return refused;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
