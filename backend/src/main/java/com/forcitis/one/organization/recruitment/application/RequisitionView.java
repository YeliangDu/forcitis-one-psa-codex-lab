package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.organization.recruitment.api.CloseReasonKind;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.api.RequisitionStatus;
import com.forcitis.one.organization.recruitment.domain.RecruitmentRequisition;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RequisitionView(RequisitionId requisitionId, @Nullable String number, UUID positionId, UUID orgUnitId, UUID requestedBy,
                              int requestedCount, @Nullable LocalDate expectedStartDate, @Nullable String description, RequisitionStatus status,
                              @Nullable CloseReasonKind closeReason, @Nullable Instant submittedAt, @Nullable Instant approvedAt,
                              @Nullable Instant closedAt, long version) {

    static RequisitionView of(RecruitmentRequisition r) {
        return new RequisitionView(r.requisitionId(), r.number().map(n -> n.value()).orElse(null), r.positionId().value(), r.orgUnitId().value(),
                r.requestedBy().value(), r.requestedCount(), r.expectedStartDate().orElse(null), r.description().orElse(null), r.status(),
                r.closeReason().orElse(null), r.submittedAt().orElse(null), r.approvedAt().orElse(null), r.closedAt().orElse(null), r.version());
    }
}
