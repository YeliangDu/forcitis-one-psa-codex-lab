package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionQueries;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Newest first. The scope is applied before any row is read: an empty scope lists nothing, a scope that cannot be worked out throws. */
@Service
public class ListRequisitions {

    private static final String LIST = "recruitment-requisitions";

    private final RequisitionQueries repository;
    private final RequisitionScope scope;

    ListRequisitions(RequisitionQueries repository, RequisitionScope scope) {
        this.repository = repository;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public CursorPage<RequisitionView> execute(PageRequest page) {
        Set<UUID> units = scope.covered(RecruitmentPermissions.READ, repository.orgUnitsWithRequisitions());
        UUID cursor = page.cursorUuid();
        List<RecruitmentRequisition> rows = repository.list(units, cursor == null ? null : new RequisitionId(cursor), page.limit() + 1);
        List<RequisitionView> items = rows.stream().limit(page.limit()).map(RequisitionView::of).toList();
        return CursorPage.of(items, rows.size() > items.size() ? PageRequest.encode(LIST, items.getLast().requisitionId().value().toString()) : null);
    }
}
