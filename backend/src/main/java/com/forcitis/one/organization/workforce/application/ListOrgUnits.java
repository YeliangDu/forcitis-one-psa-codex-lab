package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgHierarchy;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Flat list, parents first, only the units the caller may see; the client builds the tree from parentOrgUnitId. */
@Service
public class ListOrgUnits {
    private final OrgSnapshotQueries snapshots;
    private final OrgUnitAccess access;

    ListOrgUnits(OrgSnapshotQueries snapshots, OrgUnitAccess access) {
        this.snapshots = snapshots;
        this.access = access;
    }

    public List<OrgUnitView> execute(boolean includeRevoked) {
        List<OrgUnit> all = snapshots.findAllOrgUnits();
        Set<OrgUnitId> allowed = OrgUnitVisibility.idsIn(all, access.visibleOrgUnits(OrgUnitVisibility.ORG_UNIT_READ));
        return OrgHierarchy.parentsFirst(all.stream()
                .filter(unit -> allowed.contains(unit.orgUnitId()))
                .filter(unit -> includeRevoked || unit.status() != OrgUnitStatus.REVOKED)
                .toList()).stream()
                .map(OrgUnitView::of)
                .toList();
    }
}
