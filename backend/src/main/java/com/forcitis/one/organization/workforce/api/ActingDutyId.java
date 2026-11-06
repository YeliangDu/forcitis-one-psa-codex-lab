package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One acting duty of an employee: a dated stretch of standing in for a unit or a post (#1525, the signed workforce-acting-duty data design record). */
@ValueObject
public record ActingDutyId(UUID value) {

    public ActingDutyId {
        value = Uuids.requireKey(value, "ActingDutyId");
    }

    /** Only the lower-case canonical form. */
    public static ActingDutyId parse(String text) {
        return new ActingDutyId(Uuids.parseCanonical(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
