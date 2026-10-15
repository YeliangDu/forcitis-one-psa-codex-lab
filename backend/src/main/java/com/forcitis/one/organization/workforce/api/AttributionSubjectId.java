package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** The own id of the department or employee a legal entity attribution is about; its kind travels beside it (RULING-P3B-D5). */
@ValueObject
public record AttributionSubjectId(UUID value) {

    public AttributionSubjectId {
        value = Uuids.requireKey(value, "AttributionSubjectId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
