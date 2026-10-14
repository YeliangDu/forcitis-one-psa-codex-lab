package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

/** Bulk sync and single-person lookup use the same mapper; loginName is trimmed, blank means none. */
public record SourceMember(UserId userId, String employeeCode, String displayName, OrgUnitId orgUnitId,
                           PositionId positionId, UserId supervisorId, String loginName, String email, Gender gender,
                           WecomUserId wecomUserId, boolean feeEarner) {
    public SourceMember {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(employeeCode, "employeeCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
    }
}
