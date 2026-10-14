package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Objects;

/** projectRole null matches any row, with or without one. */
public record AccessGrant(LegalEntityId legalEntityId, OrgUnitId orgUnitId, boolean withDescendants, ProjectRole projectRole) {

    public AccessGrant {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
    }

    public boolean covers(AccessTarget target) {
        boolean unitMatches = withDescendants
                ? target.orgUnitPath().contains(orgUnitId)
                : !target.orgUnitPath().isEmpty() && target.orgUnitPath().getFirst().equals(orgUnitId);
        return legalEntityId.equals(target.legalEntityId()) && unitMatches
                && (projectRole == null || projectRole.equals(target.projectRole()));
    }
}
