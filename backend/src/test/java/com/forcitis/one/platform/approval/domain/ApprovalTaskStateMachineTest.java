package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestEventEnvelopes;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.EscalationTarget;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.ApprovalRoundStarted;
import com.forcitis.one.platform.approval.events.ApprovalTaskEscalated;
import com.forcitis.one.platform.approval.events.ApprovalTaskBecameOverdue;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.approval.events.TaskClaimed;
import com.forcitis.one.platform.approval.events.TaskClosed;
import com.forcitis.one.platform.approval.events.TaskReassigned;
import com.forcitis.one.platform.approval.events.TaskReleased;
import com.forcitis.one.platform.approval.events.TaskHandled;
import com.forcitis.one.platform.approval.events.TaskTransferred;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApprovalTaskStateMachineTest {

    static final UserId HOLDER = TestAccounts.account(20);
    static final UserId OTHER = TestAccounts.account(21);
    static final EmployeeId INITIATOR = TestAccounts.employee(10);
    static final Instant DUE = Instant.parse("2026-10-19T15:59:00Z");
    static final Instant REMIND_AT = Instant.parse("2026-10-20T01:00:00Z");
    static final Instant ESCALATE_AT = Instant.parse("2026-10-21T01:00:00Z");
    static final Instant AFTER_BOTH = Instant.parse("2026-10-22T00:00:00Z");
    static final Moment NOW = new Moment(AFTER_BOTH, LocalDate.of(2026, 10, 22));
    static final ApprovalTaskId TASK = com.forcitis.one.TestApprovalIds.task("t-1");
    static final ApprovalInstanceId INSTANCE = com.forcitis.one.TestApprovalIds.instance("i-1");
    static final NodeKey NODE = new NodeKey("projectManagerReview");
    static final com.forcitis.one.platform.approval.api.NodeName NODE_NAME = new com.forcitis.one.platform.approval.api.NodeName("项目经理审批");
    static final UserId ADMIN = TestAccounts.account(1);
    static final DocumentReference DOCUMENT = new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("7"));
    static final DeadlinePlan PLAN = new DeadlinePlan(DUE, REMIND_AT, ESCALATE_AT);
    static final TestEventEnvelopes ENVELOPES = new TestEventEnvelopes();

    static ApprovalTask taskIn(ApprovalTaskStatus status, Instant overdueAt) {
        boolean held = status == ApprovalTaskStatus.CLAIMED || status == ApprovalTaskStatus.COMPLETED;
        return ApprovalTask.restore(TASK, INSTANCE, NODE, NODE_NAME, status, held ? HOLDER : null, Instant.parse("2026-10-16T02:00:00Z"), PLAN,
                status == ApprovalTaskStatus.CLAIMED ? Instant.parse("2026-10-16T03:00:00Z") : null,
                status == ApprovalTaskStatus.COMPLETED ? Instant.parse("2026-10-16T04:00:00Z") : null, overdueAt, null,
                status == ApprovalTaskStatus.COMPLETED ? TaskOutcome.APPROVED : null,
                status == ApprovalTaskStatus.COMPLETED ? TaskEffect.BINDING : null, null, null);
    }

    static ApprovalRequest requestWith(ApprovalTask task) {
        return ApprovalRequest.restore(com.forcitis.one.TestApprovalIds.requestId(), com.forcitis.one.TestApprovalIds.generator(), ProcessKey.COST_TIMESHEET_APPROVAL, DOCUMENT, new LegalEntityId(TestIds.uuid(1)), INITIATOR, 1, INSTANCE,
                ApprovalStatus.RUNNING, null, new ApprovalSummary("summary"), List.of(task), 0, 0);
    }

    static ApprovalRequest in(ApprovalTaskStatus status) {
        return requestWith(taskIn(status, null));
    }

    static ApprovalTaskStatus statusOf(ApprovalRequest request) {
        return request.task(TASK).status();
    }

    @Test
    @DisplayName("RULE-276/289 every task move is tried from every task status: only the table's pairs move, every other pair is refused with a conflict and changes nothing")
    void everyMoveInEveryStatus() {
        List<String> tried = new TransitionTable<ApprovalTaskStatus, ApprovalRequest>(ApprovalTaskStatus.class,
                ApprovalTaskStateMachineTest::in, ApprovalTaskStateMachineTest::statusOf)
                .operation("claim", r -> r.claimTask(TASK, HOLDER, NOW), ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLAIMED)
                .operation("release", r -> r.releaseTask(TASK, HOLDER, NOW), ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.PENDING)
                .operation("complete", r -> r.completeTask(TASK, HOLDER, TaskOutcome.APPROVED, false, null, new ContentHash("h"), NOW),
                        ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.COMPLETED)
                .operation("close", r -> r.closeTask(TASK, NOW), ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLOSED,
                        ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.CLOSED)
                .operation("transfer", r -> r.transferTask(TASK, HOLDER, OTHER, new Reason("on leave"), NOW), ApprovalTaskStatus.CLAIMED,
                        ApprovalTaskStatus.CLAIMED)
                .operation("reassign", r -> r.reassignTask(TASK, ADMIN, OTHER, new Reason("covering"), NOW), ApprovalTaskStatus.PENDING,
                        ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.CLAIMED, ApprovalTaskStatus.CLAIMED)
                .verify();
        assertThat(tried).hasSize(6 * ApprovalTaskStatus.values().length);
    }

    @Test
    @DisplayName("platform.approval deadline: a task escalates only after its overdue was recorded and not before the escalation time; a claimed one goes back to the pool; only once")
    void escalationFollowsTheReminder() {
        for (ApprovalTaskStatus open : List.of(ApprovalTaskStatus.PENDING, ApprovalTaskStatus.CLAIMED)) {
            ApprovalRequest notReminded = requestWith(taskIn(open, null));
            assertThatThrownBy(() -> notReminded.escalateTask(TASK, List.of(OTHER), false, NOW)).isInstanceOf(DomainException.class);

            ApprovalRequest reminded = requestWith(taskIn(open, REMIND_AT));
            assertThatThrownBy(() -> reminded.escalateTask(TASK, List.of(OTHER), false,
                    new Moment(ESCALATE_AT.minusSeconds(1), NOW.date()))).isInstanceOf(DomainException.class);
            reminded.escalateTask(TASK, List.of(OTHER), false, NOW);
            assertThat(reminded.task(TASK).status()).isEqualTo(ApprovalTaskStatus.PENDING);
            assertThat(reminded.task(TASK).assignee()).isEmpty();
            assertThat(reminded.task(TASK).claimedAt()).isEmpty();
            assertThat(reminded.task(TASK).escalatedAt()).contains(AFTER_BOTH);
            assertThatThrownBy(() -> reminded.escalateTask(TASK, List.of(OTHER), false, NOW)).as("only once").isInstanceOf(DomainException.class);
        }
    }

    @Test
    @DisplayName("platform.approval deadline: the overdue is recorded once and not before the reminder time")
    void overdueOnceAndNotEarly() {
        ApprovalRequest request = in(ApprovalTaskStatus.PENDING);
        assertThatThrownBy(() -> request.markTaskOverdue(TASK, List.of(HOLDER), new Moment(REMIND_AT.minusSeconds(1), NOW.date())))
                .isInstanceOf(DomainException.class);
        request.markTaskOverdue(TASK, List.of(HOLDER), new Moment(REMIND_AT, NOW.date()));
        assertThat(request.task(TASK).overdueAt()).contains(REMIND_AT);
        assertThatThrownBy(() -> request.markTaskOverdue(TASK, List.of(HOLDER), NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("platform.approval deadline: a task without a deadline is never overdue")
    void noDeadlineNoOverdue() {
        ApprovalTask task = ApprovalTask.restore(TASK, INSTANCE, NODE, NODE_NAME, ApprovalTaskStatus.PENDING, null, Instant.EPOCH, null, null, null, null,
                null, null, null, null, null);
        assertThatThrownBy(() -> requestWith(task).markTaskOverdue(TASK, List.of(HOLDER), NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("RULE-285 D-1a-75 only the current handler hands a task over, never to themself, and says why")
    void transferIsTheHandlersAndToSomebodyElseWithAReason() {
        ApprovalRequest request = in(ApprovalTaskStatus.CLAIMED);
        assertThatThrownBy(() -> request.transferTask(TASK, OTHER, TestAccounts.account(22), new Reason("r"), NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> request.transferTask(TASK, HOLDER, HOLDER, new Reason("r"), NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> request.transferTask(TASK, HOLDER, OTHER, null, NOW)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
        request.transferTask(TASK, HOLDER, OTHER, new Reason("on leave"), NOW);
        assertThat(request.task(TASK).assignee()).contains(OTHER);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01 a rejection or a return without a reason is refused, for an advisor too; the task stays claimed")
    void aRejectionWithoutAReasonIsRefused() {
        for (boolean advisory : new boolean[] {false, true}) {
            for (TaskOutcome outcome : new TaskOutcome[] {TaskOutcome.REJECTED, TaskOutcome.RETURNED}) {
                ApprovalRequest request = in(ApprovalTaskStatus.CLAIMED);
                assertThatThrownBy(() -> request.completeTask(TASK, HOLDER, outcome, advisory, null, new ContentHash("d"), NOW))
                        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
                assertThat(statusOf(request)).isEqualTo(ApprovalTaskStatus.CLAIMED);
            }
        }
    }

    @Test
    @DisplayName("RULE-276 only the person who claimed the task completes it")
    void onlyTheAssigneeCompletes() {
        ApprovalRequest request = in(ApprovalTaskStatus.CLAIMED);
        assertThatThrownBy(() -> request.completeTask(TASK, OTHER, TaskOutcome.APPROVED, false, null, new ContentHash("d"), NOW))
                .isInstanceOf(DomainException.class);
        assertThat(statusOf(request)).isEqualTo(ApprovalTaskStatus.CLAIMED);
    }

    @Test
    @DisplayName("RULE-280 the opinion is kept as given: no reason stays no reason, a reason stays a reason; the time of the decision is recorded")
    void theOpinionIsKeptAsGiven() {
        ApprovalRequest silent = in(ApprovalTaskStatus.CLAIMED);
        silent.completeTask(TASK, HOLDER, TaskOutcome.APPROVED, false, null, new ContentHash("d"), NOW);
        assertThat(silent.task(TASK).resultReason()).isEmpty();
        assertThat(silent.task(TASK).completedAt()).contains(AFTER_BOTH);

        ApprovalRequest worded = in(ApprovalTaskStatus.CLAIMED);
        worded.completeTask(TASK, HOLDER, TaskOutcome.APPROVED, false, new Reason("looks right"), new ContentHash("d"), NOW);
        assertThat(worded.task(TASK).resultReason()).contains(new Reason("looks right"));
        List<DomainEvent> events = worded.drainEvents(ENVELOPES);
        assertThat(((TaskHandled) events.getFirst()).reason()).isEqualTo(new Reason("looks right"));
        List<DomainEvent> silentEvents = silent.drainEvents(ENVELOPES);
        assertThat(((TaskHandled) silentEvents.getFirst()).reason()).isNull();
    }

    @Test
    @DisplayName("RULE-302 D-1a-66 the hash of what was approved is kept for a formal approval only; an advisory rejection keeps none; a system pass says AUTOMATIC and is a result event")
    void hashOnlyForFormalApprovals() {
        ApprovalRequest approved = in(ApprovalTaskStatus.CLAIMED);
        approved.completeTask(TASK, HOLDER, TaskOutcome.APPROVED, false, null, new ContentHash("h1"), NOW);
        assertThat(approved.task(TASK).approvedHash()).contains(new ContentHash("h1"));

        ApprovalRequest advisory = in(ApprovalTaskStatus.CLAIMED);
        advisory.completeTask(TASK, HOLDER, TaskOutcome.REJECTED, true, new Reason("too high"), new ContentHash("h1"), NOW);
        assertThat(advisory.task(TASK).approvedHash()).isEmpty();
        assertThat(((TaskHandled) advisory.drainEvents(ENVELOPES).getFirst()).effect()).isEqualTo(TaskEffect.ADVISORY);

        ApprovalRequest request = ApprovalRequest.begin(com.forcitis.one.TestApprovalIds.generator(), ProcessKey.COST_TIMESHEET_APPROVAL, DOCUMENT, new LegalEntityId(TestIds.uuid(1)), INITIATOR, INSTANCE,
                new ApprovalSummary("summary"), UUID.randomUUID(), NOW);
        request.drainEvents(ENVELOPES);
        TaskAssignment assignment = new TaskAssignment(com.forcitis.one.TestApprovalIds.task("t-3"), INSTANCE, NODE, new NodeName("Review"),
                new ProcessName("Timesheet approval"), List.of(HOLDER), Optional.empty());
        request.passAutomatically(assignment, HOLDER, NOW);
        assertThat(request.task(com.forcitis.one.TestApprovalIds.task("t-3")).status()).isEqualTo(ApprovalTaskStatus.COMPLETED);
        assertThat(request.task(com.forcitis.one.TestApprovalIds.task("t-3")).approvedHash()).isEmpty();
        List<DomainEvent> events = request.drainEvents(ENVELOPES);
        assertThat(events).singleElement().isInstanceOfSatisfying(TaskHandled.class, result -> {
            assertThat(result.effect()).isEqualTo(TaskEffect.AUTOMATIC);
            assertThat(result.outcome()).isEqualTo(TaskOutcome.APPROVED);
            assertThat(result.assignee()).isEqualTo(HOLDER);
        });
    }

    @Test
    @DisplayName("RULE-295 M-4 every task event carries the request's running event number; opening a round and closing a task are events too")
    void eventsCarryTheRequestsSequence() {
        ApprovalRequest request = ApprovalRequest.begin(com.forcitis.one.TestApprovalIds.generator(), ProcessKey.COST_TIMESHEET_APPROVAL, DOCUMENT, new LegalEntityId(TestIds.uuid(1)), INITIATOR, INSTANCE,
                new ApprovalSummary("summary"), UUID.randomUUID(), NOW);
        TaskAssignment first = new TaskAssignment(TASK, INSTANCE, NODE, new NodeName("项目经理审批"), new ProcessName("工时审批"),
                List.of(HOLDER, OTHER), Optional.of(PLAN));
        TaskAssignment second = new TaskAssignment(com.forcitis.one.TestApprovalIds.task("t-2"), INSTANCE, new NodeKey("supervisorReview"), new NodeName("一级汇报人审批"),
                new ProcessName("工时审批"), List.of(HOLDER), Optional.empty());
        request.assignTask(first, NOW);
        request.assignTask(second, NOW);
        request.claimTask(TASK, HOLDER, NOW);
        request.markTaskOverdue(TASK, List.of(HOLDER), NOW);
        request.escalateTask(TASK, List.of(OTHER), false, NOW);
        request.claimTask(TASK, OTHER, NOW);
        request.transferTask(TASK, OTHER, TestAccounts.account(22), new Reason("on leave"), NOW);
        request.reassignTask(TASK, ADMIN, TestAccounts.account(23), new Reason("covering"), NOW);
        request.releaseTask(TASK, TestAccounts.account(23), NOW);
        request.claimTask(TASK, HOLDER, NOW);
        request.completeTask(TASK, HOLDER, TaskOutcome.APPROVED, false, null, new ContentHash("d"), NOW);
        request.reject(new Reason("no"), NOW);

        List<DomainEvent> events = request.drainEvents(ENVELOPES);

        assertThat(events).extracting(e -> e.envelope().eventType()).containsExactly("platform.approval.ApprovalRoundStarted",
                "platform.approval.TaskAssigned", "platform.approval.TaskAssigned", "platform.approval.TaskClaimed",
                "platform.approval.ApprovalTaskBecameOverdue", "platform.approval.ApprovalTaskEscalated", "platform.approval.TaskClaimed",
                "platform.approval.TaskTransferred", "platform.approval.TaskReassigned", "platform.approval.TaskReleased",
                "platform.approval.TaskClaimed", "platform.approval.TaskHandled", "platform.approval.TaskClosed",
                "platform.approval.ApprovalCompleted");
        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L,
                13L, 14L);
        assertThat(events).extracting(e -> e.envelope().aggregateType()).containsOnly("ApprovalRequest");
        assertThat(events).extracting(e -> e.envelope().aggregateId()).containsOnly(request.id().value());

        assertThat(events.get(0)).isInstanceOf(ApprovalRoundStarted.class);
        TaskAssigned assigned = (TaskAssigned) events.get(1);
        assertThat(assigned.candidates()).containsExactly(HOLDER, OTHER);
        assertThat(assigned.dueAt()).isEqualTo(DUE);
        assertThat(assigned.summary()).isEqualTo(new ApprovalSummary("summary"));
        assertThat(((TaskAssigned) events.get(2)).dueAt()).as("a node without a deadline has none").isNull();
        assertThat(events.get(3)).isInstanceOf(TaskClaimed.class);
        ApprovalTaskBecameOverdue overdue = (ApprovalTaskBecameOverdue) events.get(4);
        assertThat(overdue.dueAt()).isEqualTo(DUE);
        assertThat(overdue.holders()).containsExactly(HOLDER);
        ApprovalTaskEscalated escalated = (ApprovalTaskEscalated) events.get(5);
        assertThat(escalated.escalatedTo()).containsExactly(OTHER);
        assertThat(escalated.target()).isEqualTo(EscalationTarget.SUPERVISOR);
        TaskTransferred transferred = (TaskTransferred) events.get(7);
        assertThat(transferred.reason()).isEqualTo(new Reason("on leave"));
        TaskReassigned reassigned = (TaskReassigned) events.get(8);
        assertThat(reassigned.from()).isEqualTo(TestAccounts.account(22));
        assertThat(reassigned.to()).isEqualTo(TestAccounts.account(23));
        assertThat(events.get(9)).isInstanceOf(TaskReleased.class);
        assertThat(events.get(12)).isInstanceOf(TaskClosed.class);
        assertThat(((TaskClosed) events.get(12)).taskId()).isEqualTo(com.forcitis.one.TestApprovalIds.task("t-2"));
        assertThat(events.get(13)).isInstanceOf(ApprovalCompleted.class);
        assertThat(request.eventSequence()).isEqualTo(14);
    }

    @Test
    @DisplayName("M-6 a task given back by its holder can be claimed again; the process administrator may hand any open task to somebody, with a reason")
    void releaseAndReassign() {
        ApprovalRequest request = in(ApprovalTaskStatus.CLAIMED);
        request.releaseTask(TASK, HOLDER, NOW);
        assertThat(request.task(TASK).assignee()).isEmpty();
        request.claimTask(TASK, OTHER, NOW);
        assertThat(request.task(TASK).assignee()).contains(OTHER);

        ApprovalRequest pending = in(ApprovalTaskStatus.PENDING);
        assertThatThrownBy(() -> pending.reassignTask(TASK, ADMIN, OTHER, null, NOW)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
        pending.reassignTask(TASK, ADMIN, OTHER, new Reason("covering"), NOW);
        assertThat(pending.task(TASK).status()).isEqualTo(ApprovalTaskStatus.CLAIMED);
        assertThat(pending.task(TASK).assignee()).contains(OTHER);
    }

    @Test
    @DisplayName("M-7 a task of an old round that is still open can be closed on its own; the closing is an event")
    void anOldTaskIsClosedAlone() {
        ApprovalRequest request = in(ApprovalTaskStatus.PENDING);
        request.closeTask(TASK, NOW);
        assertThat(statusOf(request)).isEqualTo(ApprovalTaskStatus.CLOSED);
        assertThat(request.drainEvents(ENVELOPES)).singleElement().isInstanceOf(TaskClosed.class);
    }
}
