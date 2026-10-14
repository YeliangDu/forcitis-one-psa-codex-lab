package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import org.springframework.stereotype.Component;

@Component
class AttributionSubjects {
    private final OrgSnapshotQueries snapshot;

    AttributionSubjects(OrgSnapshotQueries snapshot) {
        this.snapshot = snapshot;
    }

    AttributionSubject require(AttributionKind kind, long subjectId) {
        boolean known = subjectId > 0 && switch (kind) {
            case ORG_UNIT -> snapshot.findOrgUnit(new OrgUnitId(subjectId)).isPresent();
            case EMPLOYEE -> snapshot.findStaff(new UserId(subjectId)).isPresent();
        };
        if (!known) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND, ErrorKind.NOT_FOUND,
                    "no such " + kind.name().toLowerCase(java.util.Locale.ROOT));
        }
        return new AttributionSubject(kind, subjectId);
    }
}
