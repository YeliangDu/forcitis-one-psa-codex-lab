package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import java.util.Objects;

/**
 * The coordinates of one business row, as a repository sees it. orgUnitPath lists the row's org unit first, then all its
 * ancestors; projectRole is null for rows without one; ownerId is the person the row belongs to.
 */
public record AccessTarget(LegalEntityId legalEntityId, List<OrgUnitId> orgUnitPath, ProjectRole projectRole, UserId ownerId) {

    public AccessTarget {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        orgUnitPath = List.copyOf(orgUnitPath);
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
