package com.forcitis.one.organization.recruitment.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What the read use cases ask; they never see the write repository. */
public interface RequisitionQueries {

    Optional<RecruitmentRequisition> find(RequisitionId id);

    /** The org units that hold at least one requisition: what the data scope is applied to before any row is read. */
    Set<UUID> orgUnitsWithRequisitions();

    /** Newest first, the ones after the given requisition; an empty set answers nothing. */
    List<RecruitmentRequisition> list(Set<UUID> orgUnitIds, @Nullable RequisitionId after, int limit);

    /** Newest first, the ones the employee submitted; scope does not apply to one's own. */
    List<RecruitmentRequisition> listRequestedBy(EmployeeId requester, @Nullable RequisitionId after, int limit);

    /** Newest first, of exactly these requisitions; an empty set answers nothing. */
    List<RecruitmentRequisition> listByIds(Set<UUID> ids, @Nullable RequisitionId after, int limit);
}
