package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionQueries;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the caller submitted, newest first. Everybody sees their own, whatever their org-unit scope. */
@Service
public class ListMyRequisitions {

    static final String LIST = "recruitment-requisitions-mine";

    private final RequisitionQueries repository;
    private final CurrentUser currentUser;

    ListMyRequisitions(RequisitionQueries repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CursorPage<RequisitionView> execute(PageRequest page) {
        UUID cursor = page.cursorUuid();
        List<RecruitmentRequisition> rows = repository.listRequestedBy(currentUser.employeeId(), cursor == null ? null : new RequisitionId(cursor), page.limit() + 1);
        List<RequisitionView> items = rows.stream().limit(page.limit()).map(RequisitionView::of).toList();
        return CursorPage.of(items, rows.size() > items.size() ? PageRequest.encode(LIST, items.getLast().requisitionId().value().toString()) : null);
    }
}
