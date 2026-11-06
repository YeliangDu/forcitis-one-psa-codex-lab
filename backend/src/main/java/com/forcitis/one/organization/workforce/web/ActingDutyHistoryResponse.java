package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.ActingDutyView;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Duty facts newest first, with inclusive display dates and null names for covered people outside the reader's scope. */
record ActingDutyHistoryResponse(List<ActingDutyResponse> items) {
    static ActingDutyHistoryResponse of(List<ActingDutyView> duties) {
        return new ActingDutyHistoryResponse(duties.stream().map(duty -> new ActingDutyResponse(duty.actingDutyId().value(), duty.employeeId().value(),
                duty.coveredOrgUnitId().value(), duty.coveredPositionId() == null ? null : duty.coveredPositionId().value(),
                duty.coveredEmployeeId() == null ? null : duty.coveredEmployeeId().value(), duty.coveredEmployeeName(), duty.firstDay(), duty.lastDay(),
                duty.state(), duty.endCause(), duty.reason())).toList());
    }

    record ActingDutyResponse(UUID actingDutyId, UUID employeeId, UUID coveredOrgUnitId, @Nullable UUID coveredPositionId,
                               @Nullable UUID coveredEmployeeId, @Nullable String coveredEmployeeName,
                               LocalDate firstDay, @Nullable LocalDate lastDay, ActingDutyView.State state,
                               @Nullable ActingDutyEndCause endCause, String reason) {
    }
}
