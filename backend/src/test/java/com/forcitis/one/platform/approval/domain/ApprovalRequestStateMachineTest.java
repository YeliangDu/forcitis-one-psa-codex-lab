package com.forcitis.one.platform.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestEventEnvelopes;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ReturnSource;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.ApprovalRoundStarted;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ApprovalRequestStateMachineTest {

    static final EmployeeId INITIATOR = TestAccounts.employee(10);
    static final EmployeeId STRANGER = TestAccounts.employee(11);
    static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    static final Moment NOW = new Moment(Instant.parse("2026-10-14T02:00:00Z"), TODAY);
    static final DocumentReference DOCUMENT = new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("7"));
    static final ApprovalSummary SUMMARY = new ApprovalSummary("TS-1 2026-10-14 8h");
    static final TestEventEnvelopes ENVELOPES = new TestEventEnvelopes();

    static ApprovalRequest in(ApprovalStatus status) {
        return ApprovalRequest.restore(com.forcitis.one.TestApprovalIds.requestId(), com.forcitis.one.TestApprovalIds.generator(), TimesheetApprovalProcess.KEY, DOCUMENT, new LegalEntityId(TestIds.uuid(1)), INITIATOR, 2,
                com.forcitis.one.TestApprovalIds.instance("i-2"), status, null, SUMMARY, List.of(), 5, 3);
    }

    static ApprovalRequest begun() {
        return ApprovalRequest.begin(com.forcitis.one.TestApprovalIds.generator(), TimesheetApprovalProcess.KEY, DOCUMENT, new LegalEntityId(TestIds.uuid(1)), INITIATOR,
                com.forcitis.one.TestApprovalIds.instance("i-1"), SUMMARY, UUID.randomUUID(), NOW);
    }

    static void restart(ApprovalRequest request) {
        request.restart(INITIATOR, com.forcitis.one.TestApprovalIds.instance("i-3"), SUMMARY, UUID.randomUUID(), NOW);
    }

    @Test
    @DisplayName("RULE-289 D-1a-78 D-1a-89 every operation is tried from every status: only the table's pairs move, every other pair is refused with a conflict and changes nothing")
    void everyOperationInEveryStatus() {
        List<String> tried = new TransitionTable<ApprovalStatus, ApprovalRequest>(ApprovalStatus.class, ApprovalRequestStateMachineTest::in,
                ApprovalRequest::status)
                .operation("restart", ApprovalRequestStateMachineTest::restart, ApprovalStatus.RETURNED, ApprovalStatus.RUNNING,
                        ApprovalStatus.REJECTED, ApprovalStatus.RUNNING, ApprovalStatus.CANCELLED, ApprovalStatus.RUNNING)
                .operation("approve", r -> r.approve(NOW), ApprovalStatus.RUNNING, ApprovalStatus.APPROVED)
                .operation("reject", r -> r.reject(new Reason("no"), NOW), ApprovalStatus.RUNNING, ApprovalStatus.REJECTED)
                .operation("return", r -> r.returnRound(new Reason("fix it"), NOW), ApprovalStatus.RUNNING, ApprovalStatus.RETURNED)
                .operation("withdraw", r -> r.withdraw(INITIATOR, false, new Reason("wrong amount"), NOW), ApprovalStatus.RUNNING,
                        ApprovalStatus.RETURNED)
                .operation("cancel", r -> r.cancel(INITIATOR, false, new Reason("not needed"), NOW), ApprovalStatus.RUNNING,
                        ApprovalStatus.CANCELLED)
                .verify();
        assertThat(tried).hasSize(6 * ApprovalStatus.values().length);
    }

    @Test
    @DisplayName("M-5 a running approval cannot be restarted: the domain refuses it, the running instance is never orphaned")
    void runningCannotRestart() {
        ApprovalRequest request = in(ApprovalStatus.RUNNING);
        assertThatThrownBy(() -> restart(request)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_TRANSITION_ILLEGAL));
        assertThat(request.instanceId()).isEqualTo(com.forcitis.one.TestApprovalIds.instance("i-2"));
        assertThat(request.round()).isEqualTo(2);
    }

    @Test
    @DisplayName("D-1a-78 an approved request is final: it cannot be started again")
    void approvedIsFinal() {
        assertThat(ApprovalRequest.MACHINE.successors(ApprovalStatus.APPROVED)).isEmpty();
    }

    static List<Object[]> endings() {
        return List.of(new Object[] {"approve", ApprovalOutcome.APPROVED, null, null},
                new Object[] {"reject", ApprovalOutcome.REJECTED, "no", null},
                new Object[] {"return", ApprovalOutcome.RETURNED, "fix it", ReturnSource.APPROVER},
                new Object[] {"withdraw", ApprovalOutcome.RETURNED, "wrong amount", ReturnSource.INITIATOR},
                new Object[] {"cancel", ApprovalOutcome.CANCELLED, "not needed", null});
    }

    static void end(ApprovalRequest request, String how) {
        switch (how) {
            case "approve" -> request.approve(NOW);
            case "reject" -> request.reject(new Reason("no"), NOW);
            case "return" -> request.returnRound(new Reason("fix it"), NOW);
            case "withdraw" -> request.withdraw(INITIATOR, false, new Reason("wrong amount"), NOW);
            default -> request.cancel(INITIATOR, false, new Reason("not needed"), NOW);
        }
    }

    @Test
    @DisplayName("RULE-279/289 D-1a-72 ending a round records exactly one ApprovalCompleted with the typed outcome, the round, the initiator and who returned it")
    void endingRecordsOneTypedEvent() {
        for (Object[] ending : endings()) {
            ApprovalRequest request = in(ApprovalStatus.RUNNING);
            end(request, (String) ending[0]);

            List<DomainEvent> events = request.drainEvents(ENVELOPES);

            assertThat(events).as(ending[0].toString()).hasSize(1);
            ApprovalCompleted completed = (ApprovalCompleted) events.getFirst();
            assertThat(completed.outcome()).isEqualTo(ending[1]);
            assertThat(completed.reason()).isEqualTo(Reason.ofNullable((String) ending[2]));
            assertThat(completed.returnSource()).isEqualTo(ending[3]);
            assertThat(completed.round()).isEqualTo(new RoundNumber(2));
            assertThat(completed.initiator()).isEqualTo(INITIATOR);
            assertThat(completed.document()).isEqualTo(DOCUMENT);
            assertThat(completed.envelope().eventType()).isEqualTo("platform.approval.ApprovalCompleted");
            assertThat(completed.envelope().aggregateType()).isEqualTo("ApprovalRequest");
            assertThat(completed.envelope().aggregateId()).isEqualTo(request.id().value());
            assertThat(completed.envelope().aggregateVersion()).as("the event sequence continues after the 3 events recorded before").isEqualTo(4);
            assertThat(completed.envelope().legalEntityId()).isEqualTo(new LegalEntityId(TestIds.uuid(1)));
            assertThat(completed.envelope().businessDate()).isEqualTo(TODAY);
            assertThat(request.eventSequence()).isEqualTo(4);
            assertThat(request.version()).as("the lock version moves once per save, whatever the number of events").isEqualTo(6);
        }
    }

    @Test
    @DisplayName("D-1a-78 starting a request records the round it opens; a restart opens the next round on a new instance with no leftover reason")
    void startsAndRestartsRecordTheRound() {
        ApprovalRequest request = begun();
        assertThat(request.round()).isEqualTo(1);
        List<DomainEvent> first = request.drainEvents(ENVELOPES);
        assertThat(first).singleElement().isInstanceOfSatisfying(ApprovalRoundStarted.class, started -> {
            assertThat(started.round()).isEqualTo(new RoundNumber(1));
            assertThat(started.instanceId()).isEqualTo(com.forcitis.one.TestApprovalIds.instance("i-1"));
            assertThat(started.initiator()).isEqualTo(INITIATOR);
            assertThat(started.envelope().aggregateVersion()).isEqualTo(1);
        });
        request.returnRound(new Reason("fix it"), NOW);
        request.drainEvents(ENVELOPES);

        restart(request);

        assertThat(request.round()).isEqualTo(2);
        assertThat(request.status()).isEqualTo(ApprovalStatus.RUNNING);
        assertThat(request.instanceId()).isEqualTo(com.forcitis.one.TestApprovalIds.instance("i-3"));
        assertThat(request.resultReason()).isEmpty();
        assertThat(request.startsRound()).isTrue();
        List<DomainEvent> second = request.drainEvents(ENVELOPES);
        assertThat(second).singleElement().isInstanceOfSatisfying(ApprovalRoundStarted.class, started -> {
            assertThat(started.round()).isEqualTo(new RoundNumber(2));
            assertThat(started.envelope().aggregateVersion()).as("1 start, 2 completed, 3 start").isEqualTo(3);
        });
    }

    @Test
    @DisplayName("RULE-287/I-5 only the initiator of the previous round opens the next one")
    void restartIsTheInitiatorsAlone() {
        ApprovalRequest request = in(ApprovalStatus.RETURNED);
        assertThatThrownBy(() -> request.restart(STRANGER, com.forcitis.one.TestApprovalIds.instance("i-3"), SUMMARY, UUID.randomUUID(), NOW))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NOT_INITIATOR));
        assertThat(request.round()).isEqualTo(2);
        assertThat(request.hasPendingEvents()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = WithdrawCheck.class, names = {"ALLOWED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("RULE-268/269/309 withdraw and cancel are refused for a stranger, when nothing runs and past a cut-off, exactly as the shown check says")
    void withdrawAndCancelFollowTheCheck(WithdrawCheck refused) {
        ApprovalStatus status = refused == WithdrawCheck.NOT_RUNNING ? ApprovalStatus.APPROVED : ApprovalStatus.RUNNING;
        EmployeeId actor = refused == WithdrawCheck.NOT_INITIATOR ? STRANGER : INITIATOR;
        boolean cutoff = refused == WithdrawCheck.CUTOFF_PASSED;
        assertThat(in(status).checkWithdraw(actor, cutoff)).isEqualTo(refused);
        assertThatThrownBy(() -> in(status).withdraw(actor, cutoff, new Reason("reason"), NOW)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> in(status).cancel(actor, cutoff, new Reason("reason"), NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("RULE-268/269 only with a reason; the displayed check and the executed action agree for every status, actor and cut-off")
    void displayAndExecutionShareTheCheck() {
        for (ApprovalStatus status : ApprovalStatus.values()) {
            for (EmployeeId actor : List.of(INITIATOR, STRANGER)) {
                for (boolean cutoff : new boolean[] {false, true}) {
                    WithdrawCheck shown = in(status).checkWithdraw(actor, cutoff);
                    boolean executed;
                    try {
                        in(status).withdraw(actor, cutoff, new Reason("reason"), NOW);
                        executed = true;
                    } catch (DomainException refused) {
                        executed = false;
                    }
                    assertThat(executed).as("%s by %s cutoff=%s shows %s", status, actor.value(), cutoff, shown)
                            .isEqualTo(shown == WithdrawCheck.ALLOWED);
                }
            }
        }
        assertThatThrownBy(() -> in(ApprovalStatus.RUNNING).withdraw(INITIATOR, false, null, NOW)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
        assertThatThrownBy(() -> in(ApprovalStatus.RUNNING).cancel(INITIATOR, false, null, NOW)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01 a rejection, a return or a cancellation without a reason is refused and the request keeps running")
    void anEndingWithoutAReasonIsRefused() {
        for (java.util.function.Consumer<ApprovalRequest> ending : List.<java.util.function.Consumer<ApprovalRequest>>of(
                r -> r.reject(null, NOW), r -> r.returnRound(null, NOW), r -> r.cancel(INITIATOR, false, null, NOW))) {
            ApprovalRequest request = in(ApprovalStatus.RUNNING);
            assertThatThrownBy(() -> ending.accept(request)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_REASON_REQUIRED));
            assertThat(request.status()).isEqualTo(ApprovalStatus.RUNNING);
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

    @Test
    @DisplayName("DDD-3 over any sequence of operations the event numbers are 1..n without a gap, a save without events does not advance them, and the version moves once per save")
    void eventNumbersHaveNoGaps() {
        for (long seed = 0; seed < 200; seed++) {
            java.util.Random random = new java.util.Random(seed);
            ApprovalRequest request = begun();
            java.util.List<Long> numbers = new java.util.ArrayList<>();
            long saves = 0;
            for (int step = 0; step < 25; step++) {
                try {
                    switch (random.nextInt(7)) {
                        case 0 -> restart(request);
                        case 1 -> request.approve(NOW);
                        case 2 -> request.reject(new Reason("no"), NOW);
                        case 3 -> request.returnRound(new Reason("fix"), NOW);
                        case 4 -> request.withdraw(INITIATOR, false, new Reason("wrong"), NOW);
                        case 5 -> request.cancel(INITIATOR, false, new Reason("not needed"), NOW);
                        default -> {
                        }
                    }
                } catch (DomainException refused) {
                    // an illegal move records nothing
                }
                long before = request.eventSequence();
                boolean hadEvents = request.hasPendingEvents();
                List<DomainEvent> published = request.drainEvents(ENVELOPES);
                saves++;
                published.forEach(event -> numbers.add(event.envelope().aggregateVersion()));
                assertThat(hadEvents).isEqualTo(!published.isEmpty());
                assertThat(request.eventSequence()).as("seed %s step %s", seed, step).isEqualTo(before + published.size());
                assertThat(request.version()).as("seed %s step %s", seed, step).isEqualTo(saves);
            }
            for (int i = 0; i < numbers.size(); i++) {
                assertThat(numbers.get(i)).as("seed %s", seed).isEqualTo(i + 1L);
            }
        }
    }
}
