package com.forcitis.one.organization.recruitment.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.recruitment.api.RequisitionApprovalProcess;
import com.forcitis.one.organization.recruitment.api.RequisitionDocument;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionRepository;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The state checks of the listener, which the aggregate would otherwise answer with an exception nobody sees (the event is delivered at least once). */
class ApplyRequisitionApprovalResultTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);

    private final RequisitionRepository repository = mock(RequisitionRepository.class);
    private final DomainEvents events = mock(DomainEvents.class);
    private final RecruitmentRequisition requisition = mock(RecruitmentRequisition.class);
    private final ApprovalInstanceId instance = new ApprovalInstanceId(UUID.randomUUID());
    private final ApplyRequisitionApprovalResult listener = new ApplyRequisitionApprovalResult(repository, events, CLOCK, new BusinessZone(java.time.ZoneId.of("UTC")));

    private ApprovalCompleted result(ApprovalInstanceId of, ApprovalOutcome outcome) {
        ApprovalCompleted event = mock(ApprovalCompleted.class);
        when(event.processKey()).thenReturn(RequisitionApprovalProcess.KEY);
        when(event.document()).thenReturn(new DocumentReference(RequisitionDocument.TYPE, new DocumentId(UUID.randomUUID())));
        when(event.instanceId()).thenReturn(of);
        when(event.outcome()).thenReturn(outcome);
        when(repository.findForUpdate(any())).thenReturn(Optional.of(requisition));
        when(requisition.approvalInstanceId()).thenReturn(Optional.of(instance));
        return event;
    }

    @Test
    @DisplayName("#1451 a result for a requisition that is no longer in approval is ignored: nothing is thrown, saved or published")
    void aRequisitionOutOfApprovalIgnoresTheResult() {
        ApprovalCompleted event = result(instance, ApprovalOutcome.APPROVED);
        when(requisition.isAwaitingApproval()).thenReturn(false);

        assertThatCode(() -> listener.apply(event)).doesNotThrowAnyException();

        verify(requisition, never()).approve(any(), any());
        verify(repository, never()).save(any());
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("#1451 a result of another approval instance is ignored even while the requisition is in approval")
    void aResultOfAnotherInstanceIsIgnored() {
        ApprovalCompleted event = result(new ApprovalInstanceId(UUID.randomUUID()), ApprovalOutcome.APPROVED);
        when(requisition.isAwaitingApproval()).thenReturn(true);

        listener.apply(event);

        verify(requisition, never()).approve(any(), any());
        verify(repository, never()).save(any());
    }
}
