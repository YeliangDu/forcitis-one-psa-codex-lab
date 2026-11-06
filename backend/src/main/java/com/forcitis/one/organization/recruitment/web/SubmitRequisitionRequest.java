package com.forcitis.one.organization.recruitment.web;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.organization.recruitment.api.RequisitionId;
import com.forcitis.one.organization.recruitment.application.SubmitRequisitionCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The client names the requisition id (a time-ordered UUID), so a repeated submission is recognised. */
record SubmitRequisitionRequest(@Nullable UUID requisitionId, @Nullable UUID positionId, @Nullable UUID orgUnitId, @Nullable Integer requestedCount,
                                @Nullable LocalDate expectedStartDate, @Nullable String description) {

    SubmitRequisitionCommand toCommand() {
        if (requisitionId == null || positionId == null || orgUnitId == null || requestedCount == null) {
            throw new InvalidRequestException("requisitionId, positionId, orgUnitId and requestedCount are required");
        }
        return new SubmitRequisitionCommand(new RequisitionId(requisitionId), new PositionId(positionId), new OrgUnitId(orgUnitId), requestedCount,
                expectedStartDate, description);
    }
}
