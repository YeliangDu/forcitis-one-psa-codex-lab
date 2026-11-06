package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SubmitRequisitionCommand(RequisitionId requisitionId, PositionId positionId, OrgUnitId orgUnitId, int requestedCount,
                                       @Nullable LocalDate expectedStartDate, @Nullable String description) {
}
