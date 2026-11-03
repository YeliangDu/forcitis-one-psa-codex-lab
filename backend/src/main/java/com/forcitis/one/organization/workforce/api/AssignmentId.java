package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** One post of one person for life (the root of its assignment versions, #711): the primary post, or one concurrent post. */
@ValueObject
public record AssignmentId(UUID value) {

    public AssignmentId {
        value = Uuids.requireKey(value, "AssignmentId");
    }

    /** Only the lower-case canonical form. */
    public static AssignmentId parse(String text) {
        return new AssignmentId(Uuids.parseCanonical(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
