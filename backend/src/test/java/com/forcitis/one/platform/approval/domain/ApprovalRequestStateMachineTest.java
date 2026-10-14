package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ApprovalRequestStateMachineTest {

    static final UserId INITIATOR = new UserId(10);
    static final UserId STRANGER = new UserId(11);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    static final DocumentReference DOCUMENT = new DocumentReference(DocumentType.TIMESHEET, new DocumentId("7"));

    /** The table the aggregate must implement, written down independently of it: action, from, to. */
    static final Map<ApprovalAction, Map<ApprovalStatus, ApprovalStatus>> LEGAL = Map.of(
            ApprovalAction.RESTART, Map.of(ApprovalStatus.RETURNED, ApprovalStatus.RUNNING, ApprovalStatus.CANCELLED, ApprovalStatus.RUNNING),
            ApprovalAction.APPROVE, Map.of(ApprovalStatus.RUNNING, ApprovalStatus.APPROVED),
            ApprovalAction.REJECT, Map.of(ApprovalStatus.RUNNING, ApprovalStatus.REJECTED),
            ApprovalAction.RETURN, Map.of(ApprovalStatus.RUNNING, ApprovalStatus.RETURNED),
            ApprovalAction.WITHDRAW, Map.of(ApprovalStatus.RUNNING, ApprovalStatus.RETURNED),
            ApprovalAction.CANCEL, Map.of(ApprovalStatus.RUNNING, ApprovalStatus.CANCELLED));

    static ApprovalRequest in(ApprovalStatus status) {
        return ApprovalRequest.restore(ProcessKey.COST_TIMESHEET_APPROVAL, DOCUMENT, new LegalEntityId(1), INITIATOR, 2,
                new ApprovalInstanceId("i-2"), status, null, 3, 5);
    }

    static void perform(ApprovalRequest request, ApprovalAction action) {
        switch (action) {
            case RESTART -> request.restart(INITIATOR, new ApprovalInstanceId("i-3"));
            case APPROVE -> request.approve(TODAY);
            case REJECT -> request.reject("no", TODAY);
            case RETURN -> request.returnRound("fix it", TODAY);
            case WITHDRAW -> request.withdraw(INITIATOR, false, "wrong amount", TODAY);
            case CANCEL -> request.cancel(INITIATOR, false, "not needed", TODAY);
        }
    }

    static Stream<Arguments> everyActionInEveryStatus() {
        return Stream.of(ApprovalAction.values()).flatMap(action -> Stream.of(ApprovalStatus.values())
                .map(status -> Arguments.of(action, status, LEGAL.get(action).get(status))));
    }

    @ParameterizedTest(name = "{0} while {1} gives {2}")
    @MethodSource("everyActionInEveryStatus")
    @DisplayName("RULE-289 every legal move changes the status as the table says and every other pair is refused without any change")
    void everyPairBehavesAsTheTableSays(ApprovalAction action, ApprovalStatus from, ApprovalStatus to) {
        ApprovalRequest request = in(from);
        if (to != null) {
            perform(request, action);
            assertThat(request.status()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> perform(request, action)).isInstanceOf(ApprovalTransitionException.class)
                    .extracting(e -> ((ApprovalTransitionException) e).violation())
                    .isEqualTo(ApprovalTransitionException.Violation.ILLEGAL_TRANSITION);
            assertThat(request.status()).isEqualTo(from);
            assertThat(request.round()).isEqualTo(2);
            assertThat(request.pullEvents()).isEmpty();
        }
    }

    @Test
    @DisplayName("RULE-289 the table has a row for every action, so a new action cannot be forgotten")
    void theTableCoversEveryAction() {
        assertThat(LEGAL).containsOnlyKeys(ApprovalAction.values());
    }

    static Stream<Arguments> endings() {
        return Stream.of(Arguments.of(ApprovalAction.APPROVE, ApprovalOutcome.APPROVED, null),
                Arguments.of(ApprovalAction.REJECT, ApprovalOutcome.REJECTED, "no"),
                Arguments.of(ApprovalAction.RETURN, ApprovalOutcome.RETURNED, "fix it"),
                Arguments.of(ApprovalAction.WITHDRAW, ApprovalOutcome.RETURNED, "wrong amount"),
                Arguments.of(ApprovalAction.CANCEL, ApprovalOutcome.CANCELLED, "not needed"));
    }

    @ParameterizedTest(name = "{0} publishes {1}")
    @MethodSource("endings")
    @DisplayName("RULE-279/289 ending a round records exactly one ApprovalCompleted with the typed outcome, the round and the next event number")
    void endingRecordsOneTypedEvent(ApprovalAction action, ApprovalOutcome outcome, String reason) {
        ApprovalRequest request = in(ApprovalStatus.RUNNING);
        perform(request, action);

        List<PendingEvent> pending = request.pullEvents();
        assertThat(pending).hasSize(1);
        assertThat(pending.getFirst().draft().eventType()).isEqualTo("platform.approval.ApprovalCompleted");
        assertThat(pending.getFirst().draft().aggregateType()).isEqualTo("ApprovalRequest");
        assertThat(pending.getFirst().draft().aggregateId()).isEqualTo("TIMESHEET:7");
        assertThat(pending.getFirst().draft().aggregateVersion()).isEqualTo(4);
        assertThat(pending.getFirst().draft().legalEntityId()).isEqualTo(new LegalEntityId(1));
        assertThat(pending.getFirst().draft().businessDate()).isEqualTo(TODAY);
        EventEnvelope envelope = new EventEnvelope(java.util.UUID.randomUUID(), pending.getFirst().draft().eventType(), 1,
                java.time.Instant.EPOCH, "ApprovalRequest", "TIMESHEET:7", 4, java.util.UUID.randomUUID(), null,
                com.forcitis.one.kernel.event.ActorType.USER, "10", new LegalEntityId(1), TODAY,
                pending.getFirst().draft().source(), null, List.of());
        ApprovalCompleted event = (ApprovalCompleted) pending.getFirst().assemble().apply(envelope);
        assertThat(event.outcome()).isEqualTo(outcome);
        assertThat(event.round()).isEqualTo(new RoundNumber(2));
        assertThat(event.document()).isEqualTo(DOCUMENT);
        assertThat(event.reason()).isEqualTo(Reason.ofNullable(reason));
        assertThat(request.eventSequence()).isEqualTo(4);
        assertThat(request.pullEvents()).as("pulled once").isEmpty();
    }

    @Test
    @DisplayName("RULE-287/I-5 only the initiator of the previous round opens the next one, which has a new instance and no leftover reason")
    void restartIsTheInitiatorsAndStartsAFreshRound() {
        ApprovalRequest request = in(ApprovalStatus.RETURNED);
        assertThatThrownBy(() -> request.restart(STRANGER, new ApprovalInstanceId("i-3"))).isInstanceOf(ApprovalTransitionException.class)
                .extracting(e -> ((ApprovalTransitionException) e).violation()).isEqualTo(ApprovalTransitionException.Violation.NOT_INITIATOR);
        assertThat(request.round()).isEqualTo(2);

        request.restart(INITIATOR, new ApprovalInstanceId("i-3"));
        assertThat(request.round()).isEqualTo(3);
        assertThat(request.instanceId()).isEqualTo(new ApprovalInstanceId("i-3"));
        assertThat(request.resultReason()).isNull();
        assertThat(request.pullEvents()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalAction.class, names = {"WITHDRAW", "CANCEL"})
    @DisplayName("RULE-268/269 only the initiator may withdraw or cancel, and only with a reason")
    void withdrawAndCancelNeedTheInitiatorAndAReason(ApprovalAction action) {
        UserId actor = STRANGER;
        assertThatThrownBy(() -> retract(in(ApprovalStatus.RUNNING), action, actor, false, "reason"))
                .extracting(e -> ((ApprovalTransitionException) e).violation()).isEqualTo(ApprovalTransitionException.Violation.NOT_INITIATOR);
        for (String blank : new String[] {null, "", "  "}) {
            assertThatThrownBy(() -> retract(in(ApprovalStatus.RUNNING), action, INITIATOR, false, blank))
                    .extracting(e -> ((ApprovalTransitionException) e).violation()).isEqualTo(ApprovalTransitionException.Violation.REASON_REQUIRED);
        }
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalAction.class, names = {"WITHDRAW", "CANCEL"})
    @DisplayName("RULE-309 once a cut-off node has a finished task, neither withdraw nor cancel is possible")
    void cutoffBlocksBoth(ApprovalAction action) {
        ApprovalRequest request = in(ApprovalStatus.RUNNING);
        assertThatThrownBy(() -> retract(request, action, INITIATOR, true, "reason"))
                .extracting(e -> ((ApprovalTransitionException) e).violation()).isEqualTo(ApprovalTransitionException.Violation.CUTOFF_PASSED);
        assertThat(request.status()).isEqualTo(ApprovalStatus.RUNNING);
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalAction.class, names = {"WITHDRAW", "CANCEL"})
    @DisplayName("RULE-269 the displayed answer and the executed action are the same check: whatever checkWithdraw allows, withdraw and cancel do")
    void displayAndExecutionShareTheCheck(ApprovalAction action) {
        for (ApprovalStatus status : ApprovalStatus.values()) {
            for (UserId actor : List.of(INITIATOR, STRANGER)) {
                for (boolean cutoff : new boolean[] {false, true}) {
                    WithdrawCheck shown = in(status).checkWithdraw(actor, cutoff);
                    boolean executed;
                    try {
                        retract(in(status), action, actor, cutoff, "reason");
                        executed = true;
                    } catch (ApprovalTransitionException refused) {
                        executed = false;
                    }
                    assertThat(executed).as("%s by %s cutoff=%s while %s shows %s", action, actor.value(), cutoff, status, shown)
                            .isEqualTo(shown == WithdrawCheck.ALLOWED);
                }
            }
        }
    }

    @Test
    @DisplayName("RULE-269 the check names the reason: not the initiator, not running, cut-off passed")
    void theCheckNamesTheReason() {
        assertThat(in(ApprovalStatus.RUNNING).checkWithdraw(INITIATOR, false)).isEqualTo(WithdrawCheck.ALLOWED);
        assertThat(in(ApprovalStatus.RUNNING).checkWithdraw(STRANGER, false)).isEqualTo(WithdrawCheck.NOT_INITIATOR);
        assertThat(in(ApprovalStatus.RUNNING).checkWithdraw(INITIATOR, true)).isEqualTo(WithdrawCheck.CUTOFF_PASSED);
        assertThat(in(ApprovalStatus.APPROVED).checkWithdraw(INITIATOR, false)).isEqualTo(WithdrawCheck.NOT_RUNNING);
    }

    private static void retract(ApprovalRequest request, ApprovalAction action, UserId actor, boolean cutoff, String reason) {
        if (action == ApprovalAction.WITHDRAW) {
            request.withdraw(actor, cutoff, reason, TODAY);
        } else {
            request.cancel(actor, cutoff, reason, TODAY);
        }
    }
}
