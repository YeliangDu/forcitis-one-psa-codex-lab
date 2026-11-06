package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.recruitment.api.RecruitmentError;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionQueries;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A requisition outside the caller's scope answers like one that does not exist, and the attempt is audited (ADR-0026 section 1 item 7). */
@Service
public class GetRequisition {

    private final RequisitionQueries repository;
    private final RequisitionScope scope;

    GetRequisition(RequisitionQueries repository, RequisitionScope scope) {
        this.repository = repository;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public Optional<RequisitionView> execute(RequisitionId id) {
        Optional<RecruitmentRequisition> found = repository.find(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (!covered(found.get())) {
            scope.denied(found.get().orgUnitId());
            return Optional.empty();
        }
        return found.map(RequisitionView::of);
    }

    /**
     * A scope that cannot be worked out answers not-found, as every other scoped detail does (the list refuses instead). It throws rather than returns empty:
     * the failed scope lookup has already marked the transaction rollback-only.
     */
    private boolean covered(RecruitmentRequisition requisition) {
        try {
            return scope.covers(RecruitmentPermissions.READ, requisition.orgUnitId());
        } catch (DomainException unavailable) {
            if (unavailable.kind() != ErrorKind.FORBIDDEN) {
                throw unavailable;
            }
            throw RecruitmentError.ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND.refusal("No such requisition");
        }
    }
}
