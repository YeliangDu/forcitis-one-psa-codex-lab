package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgHierarchy;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import java.util.List;
import org.springframework.stereotype.Service;

/** Flat list, parents first; the client builds the tree from parentOrgUnitId. */
@Service
public class ListOrgUnits {
    private final OrgSnapshotQueries snapshots;

    ListOrgUnits(OrgSnapshotQueries snapshots) {
        this.snapshots = snapshots;
    }

    public List<OrgUnitView> execute(boolean includeRevoked) {
        return OrgHierarchy.parentsFirst(snapshots.findAllOrgUnits().stream()
                .filter(unit -> includeRevoked || unit.status() != OrgUnitStatus.REVOKED)
                .toList()).stream()
                .map(OrgUnitView::of)
                .toList();
    }
}
