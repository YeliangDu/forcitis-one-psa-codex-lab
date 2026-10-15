package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;

@ValueObject
public record AttributionSubject(AttributionKind kind, long subjectKey) {
    public AttributionSubject {
        Objects.requireNonNull(kind, "kind");
        if (subjectKey == 0) {
            throw new IllegalArgumentException("subjectKey must not be 0");
        }
    }

    public static AttributionSubject of(EmployeeId employee) {
        return new AttributionSubject(AttributionKind.EMPLOYEE, employee.value());
    }

    public static AttributionSubject of(OrgUnitId orgUnit) {
        return new AttributionSubject(AttributionKind.ORG_UNIT, orgUnit.value());
    }

    public EmployeeId employeeId() {
        requireKind(AttributionKind.EMPLOYEE);
        return new EmployeeId(subjectKey);
    }

    public OrgUnitId orgUnitId() {
        requireKind(AttributionKind.ORG_UNIT);
        return new OrgUnitId(subjectKey);
    }

    private void requireKind(AttributionKind expected) {
        if (kind != expected) {
            throw new IllegalStateException("the subject is a " + kind + ", not a " + expected);
        }
    }
}
