package com.forcitis.one.organization.recruitment.web;

import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RequisitionStatus;
import com.forcitis.one.organization.recruitment.application.RequisitionView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record RequisitionResponse(UUID requisitionId, @Nullable String requisitionNumber, UUID positionId, UUID orgUnitId, UUID requestedByEmployeeId,
                           int requestedCount, @Nullable LocalDate expectedStartDate, @Nullable String description, RequisitionStatus status,
                           @Nullable CloseReasonKind closeReasonKind, @Nullable Instant submittedAt, @Nullable Instant approvedAt,
                           @Nullable Instant closedAt) {

    static RequisitionResponse of(RequisitionView view) {
        return new RequisitionResponse(view.requisitionId().value(), view.number(), view.positionId(), view.orgUnitId(), view.requestedBy(),
                view.requestedCount(), view.expectedStartDate(), view.description(), view.status(), view.closeReason(), view.submittedAt(),
                view.approvedAt(), view.closedAt());
    }
}
