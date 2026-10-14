package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.EscalationTarget;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskOverdue;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskResulted;
import com.forcitis.one.platform.approval.events.TaskTransferred;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApprovalTaskStateMachineTest {

    enum Move {
        CLAIM,
        COMPLETE,
        COMPLETE_AUTOMATICALLY,
        CLOSE,
        TRANSFER,
        MARK_OVERDUE,
        ESCALATE
    }

    static final UserId HOLDER = new UserId(20);
    static final UserId OTHER = new UserId(21);
    static final Instant DUE = Instant.parse("2026-10-15T16:00:00Z");
    static final Instant ESCALATE_AT = Instant.parse("2026-10-16T16:00:00Z");
    static final Instant AFTER_BOTH = Instant.parse("2026-10-17T00:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    /** Written down independently of the aggregate: for each move the statuses from which it is legal (the deadline moves also need the right time). */
    static final Map<Move, List<ApprovalTaskStatus>> LEGAL = new EnumMap<>(Map.of(
            Move.CLAIM, List.of(ApprovalTaskStatus.PENDING),
            Move.COMPLETE, List.of(ApprovalTaskStatus.CLAIMED),
            Move.COMPLETE_AUTOMATICALLY, List.of(ApprovalTaskStatus.PENDING),
            Move.CLOSE, List.of(ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLAIMED),
            Move.TRANSFER, List.of(ApprovalTaskStatus.CLAIMED),
            Move.MARK_OVERDUE, List.of(ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLAIMED),
            Move.ESCALATE, List.of()));

    static ApprovalTask in(ApprovalTaskStatus status, Instant overdueAt) {
        return ApprovalTask.restore(new ApprovalTaskId("t-1"), new ApprovalInstanceId("i-1"), ProcessKey.COST_TIMESHEET_APPROVAL,
                new DocumentReference(DocumentType.TIMESHEET, new DocumentId("7")), new LegalEntityId(1), "projectManagerReview", status,
                status == ApprovalTaskStatus.CLAIMED || status == ApprovalTaskStatus.COMPLETED ? HOLDER : null, DUE, ESCALATE_AT,
                overdueAt, null, null, 2, 0);
    }

    static void perform(ApprovalTask task, Move move) {
        switch (move) {
            case CLAIM -> task.claim(HOLDER, TODAY);
            case COMPLETE -> task.complete(HOLDER, TaskOutcome.APPROVED, false, null, "digest", TODAY);
            case COMPLETE_AUTOMATICALLY -> task.completeAutomatically();
            case CLOSE -> task.close();
            case TRANSFER -> task.transfer(HOLDER, OTHER, TODAY);
            case MARK_OVERDUE -> task.markOverdue(List.of(HOLDER), AFTER_BOTH, TODAY);
            case ESCALATE -> task.escalate(List.of(OTHER), false, AFTER_BOTH, TODAY);
        }
    }

    static Stream<Arguments> everyMoveInEveryStatus() {
        return Stream.of(Move.values()).flatMap(move -> Stream.of(ApprovalTaskStatus.values())
                .map(status -> Arguments.of(move, status, LEGAL.get(move).contains(status))));
    }

    @ParameterizedTest(name = "{0} while {1} is legal: {2}")
    @MethodSource("everyMoveInEveryStatus")
    @DisplayName("RULE-276/289 every task move is legal only from the statuses of the table, and an illegal one changes nothing")
    void everyPairBehavesAsTheTableSays(Move move, ApprovalTaskStatus from, boolean legal) {
        ApprovalTask task = in(from, null);
        if (legal) {
            perform(task, move);
        } else {
            assertThatThrownBy(() -> perform(task, move)).isInstanceOf(ApprovalTransitionException.class);
            assertThat(task.status()).isEqualTo(from);
            assertThat(task.pullEvents()).isEmpty();
        }
    }

    @ParameterizedTest(name = "escalate while {0}")
    @MethodSource("openStatuses")
    @DisplayName("platform.approval deadline: a task escalates only after its overdue was recorded, and a claimed one goes back to the pool")
    void escalationFollowsTheReminder(ApprovalTaskStatus open) {
        ApprovalTask notReminded = in(open, null);
        assertThatThrownBy(() -> perform(notReminded, Move.ESCALATE)).isInstanceOf(ApprovalTransitionException.class);

        ApprovalTask reminded = in(open, DUE);
        perform(reminded, Move.ESCALATE);
        assertThat(reminded.status()).isEqualTo(ApprovalTaskStatus.PENDING);
        assertThat(reminded.assignee()).isEmpty();
        assertThat(reminded.escalatedAt()).contains(AFTER_BOTH);
        assertThatThrownBy(() -> perform(reminded, Move.ESCALATE)).as("only once").isInstanceOf(ApprovalTransitionException.class);
    }

    static Stream<ApprovalTaskStatus> openStatuses() {
        return Stream.of(ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLAIMED);
    }

    @Test
    @DisplayName("platform.approval deadline: overdue is recorded once, and not before the due time")
    void overdueOnceAndNotEarly() {
        ApprovalTask task = in(ApprovalTaskStatus.PENDING, null);
        assertThatThrownBy(() -> task.markOverdue(List.of(HOLDER), DUE.minusSeconds(1), TODAY)).isInstanceOf(ApprovalTransitionException.class);
        task.markOverdue(List.of(HOLDER), DUE, TODAY);
        assertThat(task.overdueAt()).contains(DUE);
        assertThatThrownBy(() -> task.markOverdue(List.of(HOLDER), AFTER_BOTH, TODAY)).isInstanceOf(ApprovalTransitionException.class);
    }

    @Test
    @DisplayName("platform.approval deadline: a task without a deadline is never overdue")
    void noDeadlineNoOverdue() {
        ApprovalTask task = ApprovalTask.restore(new ApprovalTaskId("t-2"), new ApprovalInstanceId("i-1"), ProcessKey.COST_TIMESHEET_APPROVAL,
                new DocumentReference(DocumentType.TIMESHEET, new DocumentId("7")), new LegalEntityId(1), "node", ApprovalTaskStatus.PENDING,
                null, null, null, null, null, null, 0, 0);
        assertThatThrownBy(() -> task.markOverdue(List.of(HOLDER), AFTER_BOTH, TODAY)).isInstanceOf(ApprovalTransitionException.class);
    }

    @Test
    @DisplayName("RULE-285 only the current handler hands a task over, never to themself")
    void transferIsTheHandlersAndToSomebodyElse() {
        ApprovalTask task = in(ApprovalTaskStatus.CLAIMED, null);
        assertThatThrownBy(() -> task.transfer(OTHER, new UserId(22), TODAY)).isInstanceOf(ApprovalTransitionException.class);
        assertThatThrownBy(() -> task.transfer(HOLDER, HOLDER, TODAY)).isInstanceOf(ApprovalTransitionException.class);
        task.transfer(HOLDER, OTHER, TODAY);
        assertThat(task.assignee()).contains(OTHER);
    }

    @Test
    @DisplayName("RULE-276 only the person who claimed the task completes it")
    void onlyTheAssigneeCompletes() {
        ApprovalTask task = in(ApprovalTaskStatus.CLAIMED, null);
        assertThatThrownBy(() -> task.complete(OTHER, TaskOutcome.APPROVED, false, null, "d", TODAY)).isInstanceOf(ApprovalTransitionException.class);
        assertThat(task.status()).isEqualTo(ApprovalTaskStatus.CLAIMED);
    }

    @Test
    @DisplayName("RULE-302 the digest of what was approved is kept for a formal approval only; an advisory rejection and a system pass keep none")
    void digestOnlyForFormalApprovals() {
        ApprovalTask approved = in(ApprovalTaskStatus.CLAIMED, null);
        approved.complete(HOLDER, TaskOutcome.APPROVED, false, null, "d1", TODAY);
        assertThat(approved.approvedDigest()).contains("d1");

        ApprovalTask advisory = in(ApprovalTaskStatus.CLAIMED, null);
        advisory.complete(HOLDER, TaskOutcome.REJECTED, true, "too high", "d1", TODAY);
        assertThat(advisory.approvedDigest()).isEmpty();

        ApprovalTask automatic = ApprovalTask.assignAutomatically(new ApprovalTaskId("t-3"), new ApprovalInstanceId("i-1"),
                ProcessKey.COST_TIMESHEET_APPROVAL, new DocumentReference(DocumentType.TIMESHEET, new DocumentId("7")), new LegalEntityId(1), "node");
        assertThat(automatic.status()).isEqualTo(ApprovalTaskStatus.COMPLETED);
        assertThat(automatic.approvedDigest()).isEmpty();
        assertThat(automatic.pullEvents()).as("no card, no result for a system pass").isEmpty();
    }

    @Test
    @DisplayName("RULE-295 each task event carries the task's own running event number and typed payload")
    void eventsCarryTheTaskSequence() {
        ApprovalTask task = ApprovalTask.assign(new ApprovalTaskId("t-4"), new ApprovalInstanceId("i-1"), ProcessKey.COST_TIMESHEET_APPROVAL,
                "工时审批", new DocumentReference(DocumentType.TIMESHEET, new DocumentId("7")), new LegalEntityId(1), "projectManagerReview",
                "项目经理审批", new UserId(5), List.of(HOLDER, OTHER), DUE, ESCALATE_AT, TODAY);
        task.claim(HOLDER, TODAY);
        task.markOverdue(List.of(HOLDER), AFTER_BOTH, TODAY);
        task.escalate(List.of(OTHER), false, AFTER_BOTH, TODAY);
        task.claim(OTHER, TODAY);
        task.transfer(OTHER, new UserId(22), TODAY);
        task.complete(new UserId(22), TaskOutcome.APPROVED, false, null, "d", TODAY);

        List<PendingEvent> events = task.pullEvents();
        assertThat(events).extracting(e -> e.draft().eventType()).containsExactly("platform.approval.TaskAssigned",
                "platform.approval.TaskClaimed", "platform.approval.ApprovalTaskOverdue", "platform.approval.ApprovalTaskEscalated",
                "platform.approval.TaskClaimed", "platform.approval.TaskTransferred", "platform.approval.TaskResulted");
        assertThat(events).extracting(e -> e.draft().aggregateVersion()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(events).extracting(e -> e.draft().aggregateType()).containsOnly("ApprovalTask");
        assertThat(events).extracting(e -> e.draft().aggregateId()).containsOnly("t-4");

        assertThat(events.get(0).assemble().apply(envelope(events.get(0)))).isInstanceOf(TaskAssigned.class);
        assertThat(events.get(1).assemble().apply(envelope(events.get(1)))).isInstanceOf(TaskClaimed.class);
        ApprovalTaskOverdue overdue = (ApprovalTaskOverdue) events.get(2).assemble().apply(envelope(events.get(2)));
        assertThat(overdue.dueAt()).isEqualTo(DUE);
        assertThat(overdue.holders()).containsExactly(HOLDER);
        ApprovalTaskEscalated escalated = (ApprovalTaskEscalated) events.get(3).assemble().apply(envelope(events.get(3)));
        assertThat(escalated.escalatedTo()).containsExactly(OTHER);
        assertThat(escalated.target()).isEqualTo(EscalationTarget.SUPERVISOR);
        assertThat(events.get(5).assemble().apply(envelope(events.get(5)))).isInstanceOf(TaskTransferred.class);
        TaskResulted resulted = (TaskResulted) events.get(6).assemble().apply(envelope(events.get(6)));
        assertThat(resulted.outcome()).isEqualTo(TaskOutcome.APPROVED);
        assertThat(resulted.effect()).isEqualTo(TaskEffect.BINDING);
    }

    private static EventEnvelope envelope(PendingEvent event) {
        return new EventEnvelope(UUID.randomUUID(), event.draft().eventType(), 1, Instant.EPOCH, event.draft().aggregateType(),
                event.draft().aggregateId(), event.draft().aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, "job",
                event.draft().legalEntityId(), event.draft().businessDate(), event.draft().source(), null, List.of());
    }
}
