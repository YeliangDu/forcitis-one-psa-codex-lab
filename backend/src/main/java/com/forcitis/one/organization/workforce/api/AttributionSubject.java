package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A department or an employee, each under its own typed id (DA-5, ADR-0033 section 7 item 4): exactly the one the kind names is set. */
@ValueObject
public record AttributionSubject(AttributionKind kind, @Nullable OrgUnitId orgUnit, @Nullable EmployeeId employee) {
    public AttributionSubject {
        Objects.requireNonNull(kind, "kind");
        if ((kind == AttributionKind.ORG_UNIT) != (orgUnit != null) || (kind == AttributionKind.EMPLOYEE) != (employee != null)) {
            throw new IllegalArgumentException("an attribution subject of kind " + kind + " names exactly that one id");
        }
    }

    public static AttributionSubject of(EmployeeId employee) {
        return new AttributionSubject(AttributionKind.EMPLOYEE, null, Objects.requireNonNull(employee, "employee"));
    }

    public static AttributionSubject of(OrgUnitId orgUnit) {
        return new AttributionSubject(AttributionKind.ORG_UNIT, Objects.requireNonNull(orgUnit, "orgUnit"), null);
    }

    public EmployeeId employeeId() {
        requireKind(AttributionKind.EMPLOYEE);
        return employee;
    }

    public OrgUnitId orgUnitId() {
        requireKind(AttributionKind.ORG_UNIT);
        return orgUnit;
    }

    /** The id of whichever it is, for keys and texts that name the subject together with its kind. */
    public UUID subjectId() {
        return kind == AttributionKind.ORG_UNIT ? Objects.requireNonNull(orgUnit).value() : Objects.requireNonNull(employee).value();
    }

    private void requireKind(AttributionKind expected) {
        if (kind != expected) {
            throw new IllegalStateException("the subject is a " + kind + ", not a " + expected);
        }
    }
}
