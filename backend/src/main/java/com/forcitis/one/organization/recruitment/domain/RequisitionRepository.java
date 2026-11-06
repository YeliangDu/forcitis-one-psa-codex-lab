package com.forcitis.one.organization.recruitment.domain;

import com.forcitis.one.organization.recruitment.api.RequisitionId;
import java.util.Optional;

public interface RequisitionRepository {

    /** The row stays locked until the transaction ends. */
    Optional<RecruitmentRequisition> findForUpdate(RequisitionId id);

    /** Serialises the first submissions of one requisition until the transaction ends. */
    void lockForCreate(RequisitionId id);

    /** Writes only what the aggregate holds, and only if the stored version and status are still the ones it was loaded with. */
    void save(RecruitmentRequisition requisition);
}
