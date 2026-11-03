package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** A profile change request that waits for a second person (employee_profile_change_requests.employee_profile_change_request_id): a UUIDv7 the workforce application makes. */
@ValueObject
public record ProfileChangeRequestId(UUID value) {

    public ProfileChangeRequestId {
        value = Uuids.requireKey(value, "ProfileChangeRequestId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
