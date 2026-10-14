package com.forcitis.one.cost.timesheet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.events.TimesheetApproved;
import com.forcitis.one.cost.timesheet.events.TimesheetCancelled;
import com.forcitis.one.cost.timesheet.events.TimesheetRejected;
import com.forcitis.one.cost.timesheet.events.TimesheetReturned;
import com.forcitis.one.cost.timesheet.events.TimesheetSubmitted;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TimesheetStateMachineTest {

    static final UserId OWNER = new UserId(30);
    static final UserId STRANGER = new UserId(31);
    static final LocalDate WORK_DATE = LocalDate.of(2026, 10, 13);

    /** Written down independently of the aggregate: action, from, to. REVISE keeps the status. */
    static final Map<Timesheet.Action, Map<TimesheetStatus, TimesheetStatus>> LEGAL = Map.of(
            Timesheet.Action.REVISE, Map.of(TimesheetStatus.DRAFT, TimesheetStatus.DRAFT, TimesheetStatus.RETURNED, TimesheetStatus.RETURNED),
            Timesheet.Action.SUBMIT, Map.of(TimesheetStatus.DRAFT, TimesheetStatus.SUBMITTED, TimesheetStatus.RETURNED, TimesheetStatus.SUBMITTED),
            Timesheet.Action.APPROVE, Map.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED),
            Timesheet.Action.REJECT, Map.of(TimesheetStatus.SUBMITTED, TimesheetStatus.REJECTED),
            Timesheet.Action.RETURN, Map.of(TimesheetStatus.SUBMITTED, TimesheetStatus.RETURNED),
            Timesheet.Action.CANCEL, Map.of(TimesheetStatus.SUBMITTED, TimesheetStatus.DRAFT));

    static Timesheet in(TimesheetStatus status) {
        return Timesheet.restore(new TimesheetId(7), OWNER, new LegalEntityId(1), new WorkPackageId(1), WORK_DATE, Hours.of("8"),
                new UserId(40), status, 2, new ApprovalInstanceId("i-2"), 4, 6);
    }

    static void perform(Timesheet timesheet, Timesheet.Action action) {
        switch (action) {
            case REVISE -> timesheet.revise(OWNER, new WorkPackageId(2), WORK_DATE, Hours.of("6"), new UserId(41));
            case SUBMIT -> timesheet.submit(OWNER, new ApprovalInstanceId("i-3"));
            case APPROVE -> timesheet.approve();
            case REJECT -> timesheet.reject("no");
            case RETURN -> timesheet.returnForRevision("fix");
            case CANCEL -> timesheet.cancelSubmission("changed my mind");
        }
    }

    static Stream<Arguments> everyActionInEveryStatus() {
        return Stream.of(Timesheet.Action.values()).flatMap(action -> Stream.of(TimesheetStatus.values())
                .map(status -> Arguments.of(action, status, LEGAL.get(action).get(status))));
    }

    @ParameterizedTest(name = "{0} while {1} gives {2}")
    @MethodSource("everyActionInEveryStatus")
    @DisplayName("RULE-279/289 every legal timesheet move follows the table and every other pair is refused without any change")
    void everyPairBehavesAsTheTableSays(Timesheet.Action action, TimesheetStatus from, TimesheetStatus to) {
        Timesheet timesheet = in(from);
        if (to != null) {
            perform(timesheet, action);
            assertThat(timesheet.status()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> perform(timesheet, action)).isInstanceOf(TimesheetTransitionException.class)
                    .extracting(e -> ((TimesheetTransitionException) e).violation())
                    .isEqualTo(TimesheetTransitionException.Violation.ILLEGAL_TRANSITION);
            assertThat(timesheet.status()).isEqualTo(from);
            assertThat(timesheet.round()).isEqualTo(2);
            assertThat(timesheet.workPackageId()).isEqualTo(new WorkPackageId(1));
            assertThat(timesheet.pullEvents()).isEmpty();
        }
    }

    @Test
    @DisplayName("RULE-279 the table has a row for every action, so a new action cannot be forgotten")
    void theTableCoversEveryAction() {
        assertThat(LEGAL).containsOnlyKeys(Timesheet.Action.values());
    }

    @Test
    @DisplayName("I-5 only the employee revises or submits the timesheet")
    void onlyTheOwnerChangesAndSubmits() {
        assertThatThrownBy(() -> in(TimesheetStatus.DRAFT).submit(STRANGER, new ApprovalInstanceId("i")))
                .extracting(e -> ((TimesheetTransitionException) e).violation()).isEqualTo(TimesheetTransitionException.Violation.NOT_OWNER);
        assertThatThrownBy(() -> in(TimesheetStatus.RETURNED).revise(STRANGER, new WorkPackageId(2), WORK_DATE, Hours.of("1"), new UserId(41)))
                .extracting(e -> ((TimesheetTransitionException) e).violation()).isEqualTo(TimesheetTransitionException.Violation.NOT_OWNER);
    }

    @Test
    @DisplayName("RULE-267 a returned timesheet is revised and submitted again as the next round; the content digest follows the content")
    void returnedThenRevisedThenSubmittedAgain() {
        Timesheet timesheet = in(TimesheetStatus.RETURNED);
        String before = timesheet.contentDigest();
        timesheet.revise(OWNER, new WorkPackageId(1), WORK_DATE, Hours.of("7.5"), new UserId(40));
        assertThat(timesheet.contentDigest()).isNotEqualTo(before);
        timesheet.submit(OWNER, new ApprovalInstanceId("i-3"));
        assertThat(timesheet.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(timesheet.round()).isEqualTo(3);
        assertThat(timesheet.approvalInstanceId()).contains(new ApprovalInstanceId("i-3"));
        assertThat(timesheet.isAwaitingResultOfRound(3)).isTrue();
        assertThat(timesheet.isAwaitingResultOfRound(2)).as("a result of an older round is not awaited").isFalse();
    }

    @Test
    @DisplayName("RULE-279 cancelling returns the timesheet to a draft that is not counted as refused")
    void cancelIsNotARefusal() {
        Timesheet timesheet = in(TimesheetStatus.SUBMITTED);
        timesheet.cancelSubmission("wrong hours");
        assertThat(timesheet.status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(timesheet.status().isEditable()).isTrue();
        assertThat(timesheet.pullEvents()).extracting(e -> e.draft().eventType()).containsExactly("cost.timesheet.TimesheetCancelled");
    }

    @Test
    @DisplayName("RULE-295 each timesheet event carries the work date as business date, the legal entity and the aggregate's own event number")
    void eventsCarryTheDraftFields() {
        Timesheet timesheet = in(TimesheetStatus.DRAFT);
        timesheet.submit(OWNER, new ApprovalInstanceId("i-3"));
        timesheet.returnForRevision("fix");
        timesheet.submit(OWNER, new ApprovalInstanceId("i-4"));
        timesheet.approve();

        List<PendingEvent> events = timesheet.pullEvents();
        assertThat(events).extracting(e -> e.draft().eventType()).containsExactly("cost.timesheet.TimesheetSubmitted",
                "cost.timesheet.TimesheetReturned", "cost.timesheet.TimesheetSubmitted", "cost.timesheet.TimesheetApproved");
        assertThat(events).extracting(e -> e.draft().aggregateVersion()).containsExactly(5L, 6L, 7L, 8L);
        assertThat(events).extracting(e -> e.draft().businessDate()).containsOnly(WORK_DATE);
        assertThat(events).extracting(e -> e.draft().legalEntityId()).containsOnly(new LegalEntityId(1));
        assertThat(events).extracting(e -> e.draft().aggregateId()).containsOnly("7");

        TimesheetSubmitted submitted = (TimesheetSubmitted) events.get(0).assemble().apply(envelope(events.get(0)));
        assertThat(submitted.approvalInstanceId()).isEqualTo(new ApprovalInstanceId("i-3"));
        assertThat(submitted.round()).isEqualTo(new RoundNumber(3));
        assertThat(submitted.document().documentId().value()).isEqualTo("7");
        assertThat(events.get(1).assemble().apply(envelope(events.get(1)))).isInstanceOf(TimesheetReturned.class);
        TimesheetApproved approved = (TimesheetApproved) events.get(3).assemble().apply(envelope(events.get(3)));
        assertThat(approved.submittedHours()).isEqualTo(Hours.of("8"));
    }

    @Test
    @DisplayName("RULE-279 a refusal and a cancel carry their reason")
    void reasonsTravelWithTheEvents() {
        Timesheet refused = in(TimesheetStatus.SUBMITTED);
        refused.reject("over budget");
        PendingEvent rejected = refused.pullEvents().getFirst();
        assertThat(((TimesheetRejected) rejected.assemble().apply(envelope(rejected))).reason()).isEqualTo(new Reason("over budget"));

        Timesheet cancelled = in(TimesheetStatus.SUBMITTED);
        cancelled.cancelSubmission("duplicate");
        PendingEvent cancel = cancelled.pullEvents().getFirst();
        assertThat(((TimesheetCancelled) cancel.assemble().apply(envelope(cancel))).reason()).isEqualTo(new Reason("duplicate"));
    }

    private static EventEnvelope envelope(PendingEvent event) {
        return new EventEnvelope(UUID.randomUUID(), event.draft().eventType(), 1, Instant.EPOCH, event.draft().aggregateType(),
                event.draft().aggregateId(), event.draft().aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, "x",
                event.draft().legalEntityId(), event.draft().businessDate(), event.draft().source(), null, List.of());
    }
}
