package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgHierarchy;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** One org unit with its ancestors and its unit-and-below scope (RULE-360: revoked units excluded). */
@Service
public class GetOrgUnit {

    private final OrgSnapshotRepository snapshots;

    GetOrgUnit(OrgSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    public Optional<OrgUnitDetailView> execute(long orgUnitId) {
        if (orgUnitId == 0) {
            return Optional.empty();
        }
        OrgUnitId id = new OrgUnitId(orgUnitId);
        var all = snapshots.findAllOrgUnits();
        Map<OrgUnitId, OrgUnit> byId = new java.util.LinkedHashMap<>();
        all.forEach(u -> byId.put(u.orgUnitId(), u));
        OrgUnit unit = byId.get(id);
        if (unit == null) {
            return Optional.empty();
        }
        return Optional.of(new OrgUnitDetailView(OrgUnitView.of(unit),
                OrgHierarchy.ancestors(byId, id).stream().map(OrgUnitId::value).toList(),
                OrgHierarchy.unitAndBelow(all, id).stream().map(OrgUnitId::value).toList()));
    }
}
