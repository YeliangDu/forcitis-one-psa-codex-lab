package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RequisitionApprovalProcess;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionRepository;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The approval owns the process, the requisition owns its status, and this is the only place they meet. Delivery is at least once and the
 * state answers for idempotence: a requisition that is no longer in approval, or whose approval instance is another one, changes nothing.
 * Rejected and returned both close it as REJECTED (a closed requisition never restarts); cancelled closes it as WITHDRAWN.
 */
@SystemActorName("organization.recruitment.approval-result")
@Component
class ApplyRequisitionApprovalResult {

    private static final Logger log = LoggerFactory.getLogger(ApplyRequisitionApprovalResult.class);

    private final RequisitionRepository repository;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    ApplyRequisitionApprovalResult(RequisitionRepository repository, DomainEvents events, Clock clock, BusinessZone zone) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @ApplicationModuleListener
    void on(ApprovalCompleted event) {
        apply(event);
    }

    void apply(ApprovalCompleted event) {
        if (!RequisitionApprovalProcess.KEY.equals(event.processKey())) {
            return;
        }
        Optional<RecruitmentRequisition> found = repository.findForUpdate(new RequisitionId(event.document().documentId().value()));
        if (found.isEmpty()) {
            log.warn("The approval result {} names requisition {}, which does not exist", event.envelope().eventId(), event.document().documentId().value());
            return;
        }
        RecruitmentRequisition requisition = found.get();
        boolean ours = requisition.approvalInstanceId().filter(event.instanceId()::equals).isPresent();
        if (!ours || !requisition.isAwaitingApproval()) {
            log.info("Requisition {} is not waiting for this approval result: ignored", event.document().documentId().value());
            return;
        }
        var now = clock.instant();
        var today = zone.today(clock);
        switch (event.outcome()) {
            case APPROVED -> requisition.approve(now, today);
            case REJECTED, RETURNED -> requisition.closeFromApproval(CloseReasonKind.REJECTED, now, today);
            case CANCELLED -> requisition.closeFromApproval(CloseReasonKind.WITHDRAWN, now, today);
        }
        repository.save(requisition);
        events.publish(requisition);
    }
}
