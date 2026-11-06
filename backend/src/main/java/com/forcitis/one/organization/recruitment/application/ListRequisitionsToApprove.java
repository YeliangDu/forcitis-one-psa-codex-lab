package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.recruitment.api.RequisitionApprovalProcess;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import com.forcitis.one.organization.recruitment.domain.RequisitionQueries;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The requisitions the caller holds an open approval task for, newest first. The task is the grant: no org-unit scope applies. */
@Service
public class ListRequisitionsToApprove {

    static final String LIST = "recruitment-requisitions-to-approve";

    private final RequisitionQueries repository;
    private final ApprovalProcesses approvals;
    private final CurrentUser currentUser;

    ListRequisitionsToApprove(RequisitionQueries repository, ApprovalProcesses approvals, CurrentUser currentUser) {
        this.repository = repository;
        this.approvals = approvals;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public CursorPage<RequisitionView> execute(PageRequest page) {
        Set<UUID> ids = approvals.openDocumentsFor(RequisitionApprovalProcess.KEY, currentUser.userId()).stream()
                .map(document -> document.documentId().value())
                .collect(Collectors.toUnmodifiableSet());
        UUID cursor = page.cursorUuid();
        List<RecruitmentRequisition> rows = repository.listAmong(ids, cursor == null ? null : new RequisitionId(cursor), page.limit() + 1);
        List<RequisitionView> items = rows.stream().limit(page.limit()).map(RequisitionView::of).toList();
        return CursorPage.of(items, rows.size() > items.size() ? PageRequest.encode(LIST, items.getLast().requisitionId().value().toString()) : null);
    }
}
