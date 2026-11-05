package com.forcitis.one.organization.recruitment.application;

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
        if (!scope.covers(RecruitmentPermissions.READ, found.get().orgUnitId())) {
            scope.denied(found.get().orgUnitId());
            return Optional.empty();
        }
        return found.map(RequisitionView::of);
    }
}
