package com.forcitis.one.cost.timesheet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.cost.timesheet.api.TimesheetError;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetApprovalWithdrawn;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.fixture.doubles.TestEventEnvelopes;
import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.api.RoutingFlag;
import com.forcitis.one.platform.approval.events.DocumentSubmittedForApproval;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;

class TimesheetStateMachineTest {

    static final EmployeeId OWNER = TestAccounts.employee(30);
    static final EmployeeId STRANGER = TestAccounts.employee(31);
    static final LocalDate WORK_DATE = LocalDate.of(2026, 10, 13);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    static final TestEventEnvelopes ENVELOPES = new TestEventEnvelopes();

    static Timesheet in(TimesheetStatus status) {
        return Timesheet.restore(new TimesheetId(TestIds.uuid(7)), new DocumentNumber("TS-ABC-202610-0007"), new NumberingRuleVersionId(TestIds.uuid(12)), OWNER, new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5),
                WORK_DATE, Hours.of("8"), TestAccounts.employee(40), status, 2, TODAY.minusDays(1), 6, 4);
    }

    static Timesheet drafted() {
        return Timesheet.draft(new TimesheetId(TestIds.uuid(7)), new DocumentNumber("TS-ABC-202610-0007"), new AggregateReference("NumberingCounter", TestIds.uuid(11)),
                new NumberingRuleVersionId(TestIds.uuid(12)), OWNER, new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5), WORK_DATE, Hours.of("8"), TestAccounts.employee(40), TODAY);
    }

    @Test
    @DisplayName("RULE-279/289 every timesheet operation is tried from every status: only the table's pairs move, every other pair is refused with a conflict and changes nothing")
    void everyOperationInEveryStatus() {
        List<String> tried = new TransitionTable<TimesheetStatus, Timesheet>(TimesheetStatus.class, TimesheetStateMachineTest::in, Timesheet::status)
                .operation("revise", t -> t.revise(OWNER, WORK_DATE, Hours.of("6"), TestAccounts.employee(41)),
                        TimesheetStatus.DRAFT, TimesheetStatus.DRAFT, TimesheetStatus.RETURNED, TimesheetStatus.RETURNED,
                        TimesheetStatus.REJECTED, TimesheetStatus.REJECTED)
                .operation("submit", t -> t.submit(OWNER, TODAY), TimesheetStatus.DRAFT, TimesheetStatus.SUBMITTED, TimesheetStatus.RETURNED,
                        TimesheetStatus.SUBMITTED, TimesheetStatus.REJECTED, TimesheetStatus.SUBMITTED)
                .operation("approve", Timesheet::approve, TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED)
                .operation("reject", t -> t.reject(new Reason("no")), TimesheetStatus.SUBMITTED, TimesheetStatus.REJECTED)
                .operation("return", t -> t.returnForRevision(new Reason("fix")), TimesheetStatus.SUBMITTED, TimesheetStatus.RETURNED)
                .operation("cancel", t -> t.cancelSubmission(new Reason("changed my mind")), TimesheetStatus.SUBMITTED, TimesheetStatus.DRAFT)
                .verify();
        assertThat(tried).hasSize(6 * TimesheetStatus.values().length);
    }

    @Test
    @DisplayName("I-5 only the employee revises or submits the timesheet")
    void onlyTheOwnerChangesAndSubmits() {
        assertThatThrownBy(() -> in(TimesheetStatus.DRAFT).submit(STRANGER, TODAY)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(TimesheetError.COST_TIMESHEET_NOT_OWNER));
        assertThatThrownBy(() -> in(TimesheetStatus.RETURNED).revise(STRANGER, WORK_DATE, Hours.of("1"), TestAccounts.employee(41)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(TimesheetError.COST_TIMESHEET_NOT_OWNER));
    }

    @Test
    @DisplayName("H-6 D-1a-126 the work date is what the employee entered: it cannot be after today, and a resubmission cannot rewrite it")
    void theWorkDateIsEnteredAndKept() {
        assertThatThrownBy(() -> Timesheet.draft(new TimesheetId(TestIds.uuid(7)), new DocumentNumber("TS-1"), new AggregateReference("NumberingCounter", TestIds.uuid(11)),
                new NumberingRuleVersionId(TestIds.uuid(12)), OWNER, new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5), TODAY.plusDays(1), Hours.of("8"), TestAccounts.employee(40), TODAY))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(TimesheetError.COST_TIMESHEET_WORK_DATE_IN_FUTURE));
        Timesheet today = Timesheet.draft(new TimesheetId(TestIds.uuid(8)), new DocumentNumber("TS-2"), new AggregateReference("NumberingCounter", TestIds.uuid(11)),
                new NumberingRuleVersionId(TestIds.uuid(12)), OWNER, new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5), TODAY, Hours.of("8"), TestAccounts.employee(40), TODAY);
        assertThat(today.workDate()).isEqualTo(TODAY);

        Timesheet returned = in(TimesheetStatus.RETURNED);
        assertThatThrownBy(() -> returned.revise(OWNER, WORK_DATE.plusDays(1), Hours.of("8"), TestAccounts.employee(40)))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(TimesheetError.COST_TIMESHEET_WORK_DATE_FIXED));
        returned.revise(OWNER, WORK_DATE, Hours.of("7.5"), TestAccounts.employee(40));
        returned.submit(OWNER, TODAY.plusDays(3));
        assertThat(returned.workDate()).as("submitting days later does not move the hours to another day").isEqualTo(WORK_DATE);
        assertThat(returned.submittedOn()).as("the day of submission is a fact of its own").contains(TODAY.plusDays(3));
        assertThat(returned.periodOfWork()).isEqualTo(AccountingPeriod.of(2026, 10));
    }

    @Test
    @DisplayName("RULE-267 a returned timesheet is revised and submitted again as the next round; the content hash follows the content")
    void returnedThenRevisedThenSubmittedAgain() {
        Timesheet timesheet = in(TimesheetStatus.RETURNED);
        var before = timesheet.contentHash();
        timesheet.revise(OWNER, WORK_DATE, Hours.of("7.5"), TestAccounts.employee(40));
        assertThat(timesheet.contentHash()).isNotEqualTo(before);
        timesheet.submit(OWNER, TODAY);
        assertThat(timesheet.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(timesheet.round()).isEqualTo(3);
        assertThat(timesheet.isAwaitingResultOfRound(3)).isTrue();
        assertThat(timesheet.isAwaitingResultOfRound(2)).as("a result of an older round is not awaited").isFalse();
    }

    @Test
    @DisplayName("RULE-279 cancelling returns the timesheet to a draft that is not counted as refused")
    void cancelIsNotARefusal() {
        Timesheet timesheet = in(TimesheetStatus.SUBMITTED);
        timesheet.cancelSubmission(new Reason("wrong hours"));
        assertThat(timesheet.status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(timesheet.status().isEditable()).isTrue();
        assertThat(timesheet.drainEvents(ENVELOPES)).extracting(e -> e.envelope().eventType()).containsExactly("cost.timesheet.TimesheetApprovalWithdrawn");
    }

    @Test
    @DisplayName("H-1 RULE-295 submitting records the fact TimesheetSubmitted and, separately, DocumentSubmittedForApproval with everything the approval needs; the numbering aggregate is named as touched together")
    void submittingPublishesTheFactAndTheRequest() {
        Timesheet timesheet = drafted();
        timesheet.submit(OWNER, TODAY);

        List<DomainEvent> events = timesheet.drainEvents(ENVELOPES);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOfSatisfying(TimesheetSubmitted.class, submitted -> {
            assertThat(submitted.document().documentId().value()).isEqualTo(TestIds.uuid(7));
            assertThat(submitted.number()).isEqualTo(new DocumentNumber("TS-ABC-202610-0007"));
            assertThat(submitted.employee()).isEqualTo(OWNER);
            assertThat(submitted.projectManager()).isEqualTo(TestAccounts.employee(40));
            assertThat(submitted.round()).isEqualTo(new RoundNumber(1));
            assertThat(submitted.submittedOn()).isEqualTo(TODAY);
            assertThat(submitted.envelope().businessDate()).as("the business date of the fact is the work date").isEqualTo(WORK_DATE);
            assertThat(submitted.envelope().legalEntityId()).isEqualTo(new LegalEntityId(TestIds.uuid(1)));
            assertThat(submitted.envelope().aggregateVersion()).isEqualTo(1);
            assertThat(submitted.envelope().relatedAggregates()).as("DDD-1 the counter that took the number in the same transaction")
                    .containsExactly(new AggregateReference("NumberingCounter", TestIds.uuid(11)));
        });
        assertThat(events.get(1)).isInstanceOfSatisfying(DocumentSubmittedForApproval.class, requested -> {
            assertThat(requested.envelope().eventType()).isEqualTo("platform.approval.DocumentSubmittedForApproval");
            assertThat(requested.envelope().aggregateType()).isEqualTo("Timesheet");
            assertThat(requested.envelope().aggregateVersion()).isEqualTo(2);
            assertThat(requested.processKey()).isEqualTo(TimesheetApprovalProcess.KEY);
            assertThat(requested.document()).isEqualTo(timesheet.document());
            assertThat(requested.initiator()).isEqualTo(OWNER);
            assertThat(requested.round()).isEqualTo(new RoundNumber(1));
            assertThat(requested.routingFlags()).containsExactly(new RoutingFlag(TimesheetRoutingFlag.PROJECT_HOURS.flagName()));
            assertThat(requested.formFields()).containsExactly(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(40).toString())));
            assertThat(requested.contentHash()).isEqualTo(timesheet.contentHash());
            assertThat(requested.summary().value()).contains("TS-ABC-202610-0007").contains("2026-10-13");
            assertThat(requested.envelope().legalEntityId()).isEqualTo(new LegalEntityId(TestIds.uuid(1)));
        });
    }

    @Test
    @DisplayName("RULE-295 each timesheet event carries the work date as business date, the legal entity and the aggregate's own running event number")
    void eventsCarryTheDraftFields() {
        Timesheet timesheet = in(TimesheetStatus.DRAFT);
        timesheet.submit(OWNER, TODAY);
        timesheet.returnForRevision(new Reason("fix"));
        timesheet.submit(OWNER, TODAY);
        timesheet.approve();

        List<DomainEvent> events = timesheet.drainEvents(ENVELOPES);

        assertThat(events).extracting(e -> e.envelope().eventType()).containsExactly("cost.timesheet.TimesheetSubmitted",
                "platform.approval.DocumentSubmittedForApproval", "cost.timesheet.TimesheetReturned", "cost.timesheet.TimesheetSubmitted",
                "platform.approval.DocumentSubmittedForApproval", "cost.timesheet.TimesheetApproved");
        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(events).extracting(e -> e.envelope().businessDate()).containsOnly(WORK_DATE);
        assertThat(events).extracting(e -> e.envelope().legalEntityId()).containsOnly(new LegalEntityId(TestIds.uuid(1)));
        assertThat(events).extracting(e -> e.envelope().aggregateId()).containsOnly(TestIds.uuid(7));
        assertThat(((TimesheetSubmitted) events.get(0)).round()).isEqualTo(new RoundNumber(3));
        assertThat(((DocumentSubmittedForApproval) events.get(1)).round()).isEqualTo(new RoundNumber(3));
        assertThat(events.get(2)).isInstanceOf(TimesheetReturned.class);
        assertThat(((TimesheetApproved) events.get(5)).submittedHours()).isEqualTo(Hours.of("8"));
        assertThat(timesheet.version()).as("one save, one version step, however many events").isEqualTo(7);
        assertThat(timesheet.eventSequence()).isEqualTo(10);
    }

    @Test
    @DisplayName("RULE-279 a refusal and a cancel carry their reason")
    void reasonsTravelWithTheEvents() {
        Timesheet refused = in(TimesheetStatus.SUBMITTED);
        refused.reject(new Reason("over budget"));
        assertThat(((TimesheetRejected) refused.drainEvents(ENVELOPES).getFirst()).reason()).isEqualTo(new Reason("over budget"));

        Timesheet cancelled = in(TimesheetStatus.SUBMITTED);
        cancelled.cancelSubmission(new Reason("duplicate"));
        assertThat(((TimesheetApprovalWithdrawn) cancelled.drainEvents(ENVELOPES).getFirst()).reason()).isEqualTo(new Reason("duplicate"));
    }

    @Test
    @DisplayName("DDD-3 over any sequence of operations the event numbers are 1..n without a gap, a save without events does not advance them, and the version moves once per save")
    void eventNumbersHaveNoGaps() {
        for (long seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            Timesheet timesheet = drafted();
            List<Long> numbers = new ArrayList<>();
            long saves = 0;
            for (int step = 0; step < 25; step++) {
                try {
                    switch (random.nextInt(7)) {
                        case 0 -> timesheet.revise(OWNER, WORK_DATE, Hours.of("8"), TestAccounts.employee(40));
                        case 1 -> timesheet.submit(OWNER, TODAY);
                        case 2 -> timesheet.approve();
                        case 3 -> timesheet.reject(new Reason("no"));
                        case 4 -> timesheet.returnForRevision(new Reason("fix"));
                        case 5 -> timesheet.cancelSubmission(new Reason("changed"));
                        default -> {
                        }
                    }
                } catch (DomainException refused) {
                    // an illegal move records nothing
                }
                long sequenceBefore = timesheet.eventSequence();
                boolean hadEvents = timesheet.hasPendingEvents();
                List<DomainEvent> published = timesheet.drainEvents(ENVELOPES);
                saves++;
                published.forEach(event -> numbers.add(event.envelope().aggregateVersion()));
                assertThat(hadEvents).isEqualTo(!published.isEmpty());
                assertThat(timesheet.eventSequence()).as("seed %s step %s", seed, step).isEqualTo(sequenceBefore + published.size());
                assertThat(timesheet.version()).as("seed %s step %s", seed, step).isEqualTo(saves);
            }
            for (int i = 0; i < numbers.size(); i++) {
                assertThat(numbers.get(i)).as("seed %s", seed).isEqualTo(i + 1L);
            }
        }
    }
}
