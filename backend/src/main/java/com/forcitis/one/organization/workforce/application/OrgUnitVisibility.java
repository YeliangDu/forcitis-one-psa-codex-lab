package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgHierarchy;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Turns what the caller may see into the ids of the units that exist: a unit outside it is not there for the caller. */
final class OrgUnitVisibility {
    /** The permission whose org unit scope decides what can be seen (a copy of platform.identity Permission.Codes, checked by WorkforceConfigurationTest). */
    static final String ORG_UNIT_READ = WorkforcePermissions.ORG_UNIT_READ;

    private OrgUnitVisibility() {
    }

    /**
     * For a detail: a scope that cannot be worked out shows nothing, so the id answers like a missing one; a refusal here would tell the caller the id exists
     * (security-invariants I-10, #730 comment 6606893624). Lists keep the refusal, they carry no id.
     */
    static Set<OrgUnitId> idsInForDetail(Collection<OrgUnit> units, OrgUnitAccess access) {
        try {
            return idsIn(units, access.visibleOrgUnits(ORG_UNIT_READ));
        } catch (DomainException unavailable) {
            if (unavailable.kind() != ErrorKind.FORBIDDEN) {
                throw unavailable;
            }
            return Set.of();
        }
    }

    static Set<OrgUnitId> idsIn(Collection<OrgUnit> units, OrgUnitAccess.Visible visible) {
        Set<OrgUnitId> ids = new HashSet<>();
        if (visible.everything()) {
            units.forEach(unit -> ids.add(unit.orgUnitId()));
            return ids;
        }
        visible.orgUnits().forEach(id -> ids.add(new OrgUnitId(id)));
        visible.orgUnitSubtrees().forEach(root -> ids.addAll(OrgHierarchy.unitAndBelow(units, new OrgUnitId(root))));
        return ids;
    }
}
