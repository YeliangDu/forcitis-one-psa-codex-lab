package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionQueries;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * The legal entity of a unit is read only by someone who may see the unit, and that of an employee by someone who may see the unit
 * the employee works in. Anything else answers exactly like a subject that does not exist, and is audited. version is null while nothing
 * has ever been attributed to the subject: the first attribution is then written with If-Match: "0".
 */
@Service
public class GetLegalEntityAttribution {
    private final LegalEntityAttributionQueries attributions;
    private final AttributionSubjects subjects;
    private final OrgSnapshotQueries snapshot;
    private final AuditLog audit;
    private final OrgUnitAccess access;
    private final CurrentUser currentUser;

    GetLegalEntityAttribution(LegalEntityAttributionQueries attributions, AttributionSubjects subjects, OrgSnapshotQueries snapshot, AuditLog audit,
            OrgUnitAccess access, CurrentUser currentUser) {
        this.access = access;
        this.currentUser = currentUser;
        this.attributions = attributions;
        this.subjects = subjects;
        this.snapshot = snapshot;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public LegalEntityAttributionView execute(AttributionKind kind, UUID subjectId) {
        var subject = subjects.require(kind, subjectId);
        Set<OrgUnitId> allowed = OrgUnitVisibility.idsIn(snapshot.findAllOrgUnits(), access.visibleOrgUnits(OrgUnitVisibility.ORG_UNIT_READ));
        boolean permitted = switch (kind) {
            case ORG_UNIT -> allowed.contains(new OrgUnitId(subjectId));
            case EMPLOYEE -> snapshot.findStaff(new EmployeeId(subjectId)).map(staff -> allowed.contains(staff.assignment().orgUnitId())).orElse(false);
        };
        if (!permitted) {
            audit.recordIndependently(new AuditEntry("organization.workforce.legal-entity-attribution.denied", currentUser.userId(), null,
                    AuditSubject.of(AttributionAuditKinds.of(kind), subjectId), "outside the scope of the caller"));
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND, ErrorKind.NOT_FOUND,
                    "no such " + kind.name().toLowerCase(Locale.ROOT));
        }
        return LegalEntityAttributionView.of(subject, attributions.periodsOf(subject), attributions.versionOf(subject).orElse(null));
    }
}
