package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Objects;

/** orgUnitPath lists the row's org unit first, then all its ancestors. */
public record AccessTarget(LegalEntityId legalEntityId, List<OrgUnitId> orgUnitPath, ProjectRole projectRole, UserId ownerId) {

    public AccessTarget {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        orgUnitPath = List.copyOf(orgUnitPath);
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
