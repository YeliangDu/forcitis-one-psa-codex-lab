package com.forcitis.one.acceptance;

import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * #1371: the person-level workforce events carry the id of the employment relationship that triggered them (NOT NULL). A fixture that
 * builds such an event directly, for a consumer test, names the relationship of its employee here: the same employee always gets the same id,
 * so the events of one scenario agree with each other. No database row is read or written.
 */
public final class EmploymentRelationshipEventIds {

    private EmploymentRelationshipEventIds() {
    }

    public static EmploymentRelationshipId of(UUID employee) {
        return new EmploymentRelationshipId(UUID.nameUUIDFromBytes(("relationship-of:" + employee).getBytes(StandardCharsets.UTF_8)));
    }
}
