package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Scope;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class PersonnelCommandReadScope {
    static final String PERMISSION = PersonnelCommandService.PERMISSION;
    private final OrgUnitAccess access;
    private final OrgSnapshotQueries snapshots;

    PersonnelCommandReadScope(OrgUnitAccess access, OrgSnapshotQueries snapshots) {
        this.access = access;
        this.snapshots = snapshots;
    }

    Scope resolve() {
        OrgUnitAccess.Visible visible = access.visibleOrgUnits(PERMISSION);
        return new Scope(visible.everything(), visible.everything() ? Set.of() : OrgUnitVisibility.idsIn(snapshots.findAllOrgUnits(), visible));
    }
}
