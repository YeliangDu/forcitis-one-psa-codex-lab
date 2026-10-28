package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.IdentityError;
import java.util.UUID;

/** One org unit of a CUSTOM_ORG_UNITS role: the unit alone, or with every unit below it. Both members are required. */
record CustomOrgUnitRequest(UUID orgUnitId, Boolean includeDescendants) {

    CustomOrgUnit toUnit() {
        if (orgUnitId == null || includeDescendants == null || (orgUnitId.getMostSignificantBits() == 0 && orgUnitId.getLeastSignificantBits() == 0)) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_CUSTOM_ORG_UNITS_INVALID.refusal("a custom org unit names orgUnitId (not the nil UUID) and includeDescendants");
        }
        return new CustomOrgUnit(new OrgUnitId(orgUnitId), includeDescendants);
    }
}
