package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.PrimaryAssignmentView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Newest start first; {@code effectiveTo} is exclusive and null while the assignment is open. */
record PrimaryAssignmentHistoryResponse(List<PrimaryAssignmentResponse> items) {
    static PrimaryAssignmentHistoryResponse of(List<PrimaryAssignmentView> assignments) {
        return new PrimaryAssignmentHistoryResponse(assignments.stream().map(a -> new PrimaryAssignmentResponse(a.orgUnitId().value(), a.orgUnitName(),
                a.positionId() == null ? null : a.positionId().value(), a.positionName(), a.effectiveFrom(), a.effectiveTo(), a.current())).toList());
    }

    record PrimaryAssignmentResponse(UUID orgUnitId, String orgUnitName, @Nullable UUID positionId, @Nullable String positionName,
                                     LocalDate effectiveFrom, @Nullable LocalDate effectiveTo, boolean current) {
    }
}
