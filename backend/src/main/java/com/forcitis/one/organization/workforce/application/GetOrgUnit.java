package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgHierarchy;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** A unit outside the caller's scope answers like one that does not exist, and the attempt is audited (ADR-0026 section 1 item 7). */
@Service
public class GetOrgUnit {
    private final OrgSnapshotQueries snapshots;
    private final AuditLog audit;
    private final OrgUnitAccess access;
    private final CurrentUser currentUser;

    GetOrgUnit(OrgSnapshotQueries snapshots, AuditLog audit, OrgUnitAccess access, CurrentUser currentUser) {
        this.snapshots = snapshots;
        this.audit = audit;
        this.access = access;
        this.currentUser = currentUser;
    }

    public Optional<OrgUnitDetailView> execute(UUID orgUnitId) {
        OrgUnitId id = new OrgUnitId(orgUnitId);
        var all = snapshots.findAllOrgUnits();
        Map<OrgUnitId, OrgUnit> byId = new LinkedHashMap<>();
        all.forEach(u -> byId.put(u.orgUnitId(), u));
        OrgUnit unit = byId.get(id);
        if (unit == null) {
            return Optional.empty();
        }
        Set<OrgUnitId> allowed = OrgUnitVisibility.idsIn(all, access.visibleOrgUnits(OrgUnitVisibility.ORG_UNIT_READ));
        if (!allowed.contains(id)) {
            audit.recordIndependently(new AuditEntry("organization.workforce.org-unit.denied", currentUser.userId(), null, AuditSubject.of(AuditSubjectKind.ORG_UNIT, orgUnitId),
                    "outside the scope of the caller"));
            return Optional.empty();
        }
        return Optional.of(new OrgUnitDetailView(OrgUnitView.of(unit),
                OrgHierarchy.ancestors(byId, id).stream().filter(allowed::contains).map(OrgUnitId::value).toList(),
                OrgHierarchy.unitAndBelow(all, id).stream().filter(allowed::contains).map(OrgUnitId::value).toList()));
    }
}
