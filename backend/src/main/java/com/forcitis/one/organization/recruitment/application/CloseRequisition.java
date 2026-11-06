package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An approved requisition is cancelled or filled by hand (PG-08: no trigger exists), by its requester or by whoever's scope covers its org unit.
 * Closing a closed requisition changes nothing and answers it as it is.
 */
@Service
public class CloseRequisition {

    private final RequisitionRepository repository;
    private final RequisitionScope scope;
    private final CurrentUser currentUser;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    CloseRequisition(RequisitionRepository repository, RequisitionScope scope, CurrentUser currentUser, DomainEvents events, Clock clock, BusinessZone zone) {
        this.repository = repository;
        this.scope = scope;
        this.currentUser = currentUser;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public RequisitionView execute(RequisitionId id, CloseReasonKind reason, Optional<ExpectedVersion> expected) {
        RecruitmentRequisition requisition = repository.findForUpdate(id)
                .orElseThrow(() -> RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition"));
        boolean requester = requisition.requestedBy().equals(currentUser.employeeId());
        if (!requester && !scope.covers(RecruitmentPermissions.UPDATE, requisition.orgUnitId())) {
            scope.denied(requisition.orgUnitId());
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition");
        }
        if (requisition.isClosed()) {
            return RequisitionView.of(requisition);
        }
        expected.ifPresent(version -> version.check(requisition.version()));
        requisition.closeApproved(reason, clock.instant(), zone.today(clock));
        repository.save(requisition);
        events.publish(requisition);
        return RequisitionView.of(requisition);
    }
}
