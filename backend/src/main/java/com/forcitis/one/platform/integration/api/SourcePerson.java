package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.Objects;

public record SourcePerson(OwnObjectId personId, String employeeCode, String displayName, OwnObjectId unitId,
                           OwnObjectId positionId, OwnObjectId supervisorId, String loginName, String email,
                           SourceGender gender, WecomUserId wecomUserId, boolean feeEarner, boolean internalFlagMissing) {

    public SourcePerson {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(employeeCode, "employeeCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(unitId, "unitId");
    }

    public SourcePerson(OwnObjectId personId, String employeeCode, String displayName, OwnObjectId unitId, OwnObjectId positionId,
                        OwnObjectId supervisorId, String loginName, String email, SourceGender gender, WecomUserId wecomUserId,
                        boolean feeEarner) {
        this(personId, employeeCode, displayName, unitId, positionId, supervisorId, loginName, email, gender, wecomUserId, feeEarner, false);
    }
}
