package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.Visible;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class EmployeeVisibility {
    private static final String EMPLOYEE_READ = "organization.workforce:employee:read";
    private final OrgUnitAccess access;
    private final OrgSnapshotQueries snapshots;

    EmployeeVisibility(OrgUnitAccess access, OrgSnapshotQueries snapshots) {
        this.access = access;
        this.snapshots = snapshots;
    }

    Visible forList() {
        OrgUnitAccess.Visible visible = access.visibleOrgUnits(EMPLOYEE_READ);
        return new Visible(visible.everything(), visible.everything() ? Set.of() : OrgUnitVisibility.idsIn(snapshots.findAllOrgUnits(), visible));
    }

    Visible forDetail() {
        try {
            return forList();
        } catch (DomainException unavailable) {
            if (unavailable.kind() != ErrorKind.FORBIDDEN) {
                throw unavailable;
            }
            return new Visible(false, Set.of());
        }
    }
}
