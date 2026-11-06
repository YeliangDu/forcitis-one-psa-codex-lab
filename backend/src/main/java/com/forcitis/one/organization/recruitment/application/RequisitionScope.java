package com.forcitis.one.organization.recruitment.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The org-unit scope of a permission, fail-closed (I-10): a unit is covered when the caller sees it as such or sees a unit above it with
 * everything below. A scope that cannot be worked out throws and nothing is shown. Reading outside the scope is audited by the caller of {@link #denied}.
 */
@Component
class RequisitionScope {

    private final OrgUnitAccess access;
    private final OrgDirectory org;
    private final AuditLog audit;
    private final CurrentUser currentUser;

    RequisitionScope(OrgUnitAccess access, OrgDirectory org, AuditLog audit, CurrentUser currentUser) {
        this.access = access;
        this.org = org;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    boolean covers(String permission, OrgUnitId unit) {
        return covers(access.visibleOrgUnits(permission), unit);
    }

    /** Of the candidate units, the ones the caller's scope for the permission covers; none when the scope is empty. */
    Set<UUID> covered(String permission, Set<UUID> candidates) {
        OrgUnitAccess.Visible visible = access.visibleOrgUnits(permission);
        Set<UUID> covered = new LinkedHashSet<>();
        for (UUID candidate : candidates) {
            if (covers(visible, new OrgUnitId(candidate))) {
                covered.add(candidate);
            }
        }
        return covered;
    }

    void denied(OrgUnitId unit) {
        audit.recordIndependently(new AuditEntry("organization.recruitment.requisition.denied", currentUser.userId(), null, AuditSubject.of(AuditSubjectKind.ORG_UNIT, unit.value()),
                "outside the scope of the caller"));
    }

    private boolean covers(OrgUnitAccess.Visible visible, OrgUnitId unit) {
        if (visible.everything() || visible.orgUnits().contains(unit.value()) || visible.orgUnitSubtrees().contains(unit.value())) {
            return true;
        }
        if (visible.orgUnitSubtrees().isEmpty()) {
            return false;
        }
        Set<OrgUnitId> seen = new LinkedHashSet<>();
        Optional<OrgUnitId> above = parentOf(unit);
        while (above.isPresent() && seen.add(above.get())) {
            if (visible.orgUnitSubtrees().contains(above.get().value())) {
                return true;
            }
            above = parentOf(above.get());
        }
        return false;
    }

    private Optional<OrgUnitId> parentOf(OrgUnitId unit) {
        return org.findOrgUnit(unit).map(OrgUnitProfile::parentOrgUnitId);
    }
}
