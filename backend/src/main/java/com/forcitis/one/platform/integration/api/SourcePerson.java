package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.Objects;

public record SourcePerson(SourcePersonId personId, String employeeCode, String displayName, SourceUnitId unitId,
                           SourcePositionId positionId, SourcePersonId supervisorId, String loginName, String email,
                           SourceGender gender, WecomUserId wecomUserId, boolean feeEarner) {

    public SourcePerson {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(employeeCode, "employeeCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(unitId, "unitId");
    }
}
