package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** The org tree as a flat list (parents first); the client builds the tree from parentOrgUnitId. */
@Service
public class ListOrgUnits {

    private final OrgSnapshotRepository snapshots;

    ListOrgUnits(OrgSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    public List<OrgUnitView> execute(boolean includeRevoked) {
        return snapshots.findAllOrgUnits().stream()
                .filter(unit -> includeRevoked || unit.status() != OrgUnitStatus.REVOKED)
                .sorted(Comparator.comparingInt((OrgUnit u) -> u.path().length())
                        .thenComparing(u -> u.sortOrder() == null ? Integer.MAX_VALUE : u.sortOrder())
                        .thenComparingLong(u -> u.orgUnitId().value()))
                .map(OrgUnitView::of)
                .toList();
    }
}
