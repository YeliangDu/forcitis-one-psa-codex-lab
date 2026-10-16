package com.forcitis.one.kernel;

import java.util.UUID;

/** The own id of an employee (ADR-0033 section 1); not the id of the account the person signs in with, and not any source system's id. */
@ValueObject
public record EmployeeId(UUID value) {

    public EmployeeId {
        value = Uuids.requireKey(value, "EmployeeId");
    }

    /** Only the lower-case canonical form, as a stored text, a form field or a process variable carries it. */
    public static EmployeeId parse(String text) {
        return new EmployeeId(Uuids.parseCanonical(text));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
