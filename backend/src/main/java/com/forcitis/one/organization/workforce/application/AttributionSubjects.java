package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
class AttributionSubjects {
    private final OrgSnapshotQueries snapshot;

    AttributionSubjects(OrgSnapshotQueries snapshot) {
        this.snapshot = snapshot;
    }

    AttributionSubject require(AttributionKind kind, UUID subjectId) {
        boolean known = switch (kind) {
            case ORG_UNIT -> snapshot.findOrgUnit(new OrgUnitId(subjectId)).isPresent();
            case EMPLOYEE -> snapshot.employeeExists(new EmployeeId(subjectId));
        };
        if (!known) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND, ErrorKind.NOT_FOUND,
                    "no such " + kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        return switch (kind) {
            case ORG_UNIT -> AttributionSubject.of(new OrgUnitId(subjectId));
            case EMPLOYEE -> AttributionSubject.of(new EmployeeId(subjectId));
        };
    }
}
