package com.forcitis.one.organization.recruitment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestEventEnvelopes;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.api.RequisitionStatus;
import com.forcitis.one.organization.recruitment.events.RequisitionApproved;
import com.forcitis.one.organization.recruitment.events.RequisitionClosed;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecruitmentRequisitionTest {

    static final EmployeeId REQUESTER = TestAccounts.employee(30);
    static final EmployeeId STRANGER = TestAccounts.employee(31);
    static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    static final TestEventEnvelopes ENVELOPES = new TestEventEnvelopes();

    static RecruitmentRequisition in(RequisitionStatus status) {
        boolean approved = status == RequisitionStatus.APPROVED;
        boolean closed = status == RequisitionStatus.CLOSED;
        return RecruitmentRequisition.restore(new RequisitionId(TestIds.uuid(7)), new DocumentNumber("RR-ABC-202611-0007"), TestOrgIds.position(3),
                TestOrgIds.orgUnit(5), new LegalEntityId(TestIds.uuid(1)), REQUESTER, 3, LocalDate.of(2026, 12, 1), "A backend engineer", status,
                closed ? CloseReasonKind.CANCELLED : null, new ApprovalInstanceId(TestIds.uuid(9)), NOW.minusSeconds(60), approved || closed ? NOW.minusSeconds(30) : null,
                closed ? NOW : null, NOW.minusSeconds(120), 1);
    }

    static RecruitmentRequisition submitted(int count, String description) {
        return RecruitmentRequisition.submit(new RequisitionId(TestIds.uuid(7)), new DocumentNumber("RR-ABC-202611-0007"), TestOrgIds.position(3),
                TestOrgIds.orgUnit(5), new LegalEntityId(TestIds.uuid(1)), REQUESTER, count, null, description, new ApprovalInstanceId(TestIds.uuid(9)), NOW);
    }

    @Test
    @DisplayName("#1451 every requisition operation is tried from every status: only the table's pairs move, every other pair is refused and changes nothing")
    void everyOperationInEveryStatus() {
        List<String> tried = new TransitionTable<RequisitionStatus, RecruitmentRequisition>(RequisitionStatus.class, RecruitmentRequisitionTest::in,
                RecruitmentRequisition::status)
                .operation("approve", r -> r.approve(NOW, TODAY), RequisitionStatus.SUBMITTED, RequisitionStatus.APPROVED)
                .operation("reject", r -> r.closeFromApproval(CloseReasonKind.REJECTED, NOW, TODAY), RequisitionStatus.SUBMITTED, RequisitionStatus.CLOSED)
                .operation("withdraw", r -> r.withdraw(REQUESTER, NOW, TODAY), RequisitionStatus.SUBMITTED, RequisitionStatus.CLOSED)
                .operation("cancel", r -> r.closeApproved(CloseReasonKind.CANCELLED, NOW, TODAY), RequisitionStatus.APPROVED, RequisitionStatus.CLOSED)
                .operation("fill", r -> r.closeApproved(CloseReasonKind.FILLED, NOW, TODAY), RequisitionStatus.APPROVED, RequisitionStatus.CLOSED)
                .verify();
        assertThat(tried).hasSize(5 * RequisitionStatus.values().length);
    }

    @Test
    @DisplayName("#1451 a submitted requisition keeps what the requester stated; the headcount is checked against nothing but 1..999 (Q26)")
    void submitKeepsWhatWasStated() {
        RecruitmentRequisition requisition = submitted(999, "Needs Java");

        assertThat(requisition.status()).isEqualTo(RequisitionStatus.SUBMITTED);
        assertThat(requisition.requestedCount()).isEqualTo(999);
        assertThat(requisition.submittedAt()).contains(NOW);
        assertThat(requisition.approvedAt()).isEmpty();
        assertThat(requisition.isNew()).isTrue();
        for (int refused : new int[] {0, -1, 1000}) {
            assertThatThrownBy(() -> submitted(refused, null)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(RecruitmentError.ORGANIZATION_RECRUITMENT_COUNT_INVALID));
        }
        assertThatThrownBy(() -> submitted(1, " ")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#1451 approving raises RequisitionApproved with ids only; rejecting, withdrawing and cancelling raise RequisitionClosed with their reason kind")
    void eventsCarryIdsAndTheReasonKind() {
        RecruitmentRequisition approved = in(RequisitionStatus.SUBMITTED);
        approved.approve(NOW, TODAY);
        List<DomainEvent> approvedEvents = approved.drainEvents(ENVELOPES);
        assertThat(approvedEvents).singleElement().isInstanceOfSatisfying(RequisitionApproved.class, e -> {
            assertThat(e.requisitionId()).isEqualTo(approved.requisitionId());
            assertThat(e.orgUnitId()).isEqualTo(approved.orgUnitId());
            assertThat(e.positionId()).isEqualTo(approved.positionId());
            assertThat(e.approvedAt()).isEqualTo(NOW);
            assertThat(e.envelope().businessDate()).isEqualTo(TODAY);
        });

        for (CloseReasonKind kind : new CloseReasonKind[] {CloseReasonKind.REJECTED, CloseReasonKind.WITHDRAWN}) {
            RecruitmentRequisition closed = in(RequisitionStatus.SUBMITTED);
            closed.closeFromApproval(kind, NOW, TODAY);
            assertThat(closed.drainEvents(ENVELOPES)).singleElement().isInstanceOfSatisfying(RequisitionClosed.class,
                    e -> assertThat(e.closeReasonKind()).isEqualTo(kind));
        }
        RecruitmentRequisition cancelled = in(RequisitionStatus.APPROVED);
        cancelled.closeApproved(CloseReasonKind.FILLED, NOW, TODAY);
        assertThat(cancelled.drainEvents(ENVELOPES)).singleElement().isInstanceOfSatisfying(RequisitionClosed.class,
                e -> assertThat(e.closeReasonKind()).isEqualTo(CloseReasonKind.FILLED));
        assertThat(cancelled.closedAt()).contains(NOW);
        assertThat(cancelled.approvedAt()).as("closing an approved requisition keeps its approval time").isPresent();
    }

    @Test
    @DisplayName("#1451 only the requester withdraws, and a stranger learns nothing: the answer is the one for a requisition that does not exist")
    void onlyTheRequesterWithdraws() {
        RecruitmentRequisition requisition = in(RequisitionStatus.SUBMITTED);

        assertThatThrownBy(() -> requisition.withdraw(STRANGER, NOW, TODAY)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND));
        assertThat(requisition.status()).isEqualTo(RequisitionStatus.SUBMITTED);
        assertThat(requisition.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("#1451 the reason kinds are fixed by the edge: an approval ends a requisition as REJECTED or WITHDRAWN, a hand close is CANCELLED or FILLED")
    void reasonKindsFollowTheEdge() {
        assertThatThrownBy(() -> in(RequisitionStatus.SUBMITTED).closeFromApproval(CloseReasonKind.FILLED, NOW, TODAY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(RecruitmentError.ORGANIZATION_RECRUITMENT_CLOSE_REASON_INVALID));
        assertThatThrownBy(() -> in(RequisitionStatus.APPROVED).closeApproved(CloseReasonKind.REJECTED, NOW, TODAY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(RecruitmentError.ORGANIZATION_RECRUITMENT_CLOSE_REASON_INVALID));
        assertThatThrownBy(() -> in(RequisitionStatus.SUBMITTED).closeApproved(CloseReasonKind.CANCELLED, NOW, TODAY))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_TRANSITION_ILLEGAL));
    }

    @Test
    @DisplayName("#1451 a restored requisition resumes the event numbering from its state: approved one event, closed after approval two, closed without approval one")
    void restoredSequenceFollowsTheState() {
        assertThat(in(RequisitionStatus.SUBMITTED).eventSequence()).isZero();
        assertThat(in(RequisitionStatus.APPROVED).eventSequence()).isEqualTo(1);
        assertThat(in(RequisitionStatus.CLOSED).eventSequence()).isEqualTo(2);
    }
}
