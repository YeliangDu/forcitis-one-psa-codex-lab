package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionApprovalProcess;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionRepository;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The requester takes a requisition back while it is in approval. The approval round ends as CANCELLED (history kept, no new round: a closed
 * requisition never restarts) and the requisition is closed in the same transaction; the approval's own result event then finds it closed and changes nothing.
 */
@Service
public class WithdrawRequisition {

    private final RequisitionRepository repository;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    WithdrawRequisition(RequisitionRepository repository, ApprovalProcesses approvals, CurrentUser currentUser, DomainEvents events, Clock clock,
            BusinessZone zone) {
        this.repository = repository;
        this.approvals = approvals;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public RequisitionView execute(RequisitionId id, String reason, ExpectedVersion expected) {
        RecruitmentRequisition requisition = repository.findForUpdate(id)
                .filter(found -> found.requestedBy().equals(currentUser.employeeId()))
                .orElseThrow(() -> RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition"));
        expected.check(requisition.version());
        Retractions.require(approvals.cancel(RequisitionApprovalProcess.KEY, requisition.document(), currentUser.userId(), reason));
        requisition.withdraw(currentUser.employeeId(), clock.instant(), zone.today(clock));
        repository.save(requisition);
        events.publish(requisition);
        return RequisitionView.of(requisition);
    }
}
