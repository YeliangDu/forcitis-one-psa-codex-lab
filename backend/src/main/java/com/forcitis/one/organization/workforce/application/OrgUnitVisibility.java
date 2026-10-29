package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgHierarchy;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Turns what the caller may see into the ids of the units that exist: a unit outside it is not there for the caller. */
final class OrgUnitVisibility {
    /** The permission whose org unit scope decides what can be seen (a copy of platform.identity Permission.Codes, checked by WorkforceConfigurationTest). */
    static final String ORG_UNIT_READ = "organization.workforce:org-unit:read";

    private OrgUnitVisibility() {
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
