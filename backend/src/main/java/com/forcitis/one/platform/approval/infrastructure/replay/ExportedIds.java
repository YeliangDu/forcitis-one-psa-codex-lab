package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.UUID;

/**
 * The export names people and departments by the old system's numbers; the replay maps each number to a UUID of its own in memory, one
 * to one and back, without source references and without writing anything (RULING-P3B-D6).
 */
final class ExportedIds {

    private static final long PERSON = 0x0000_0000_0000_7001L;
    private static final long UNIT = 0x0000_0000_0000_7002L;

    private ExportedIds() {
    }

    static EmployeeId employee(long person) {
        return new EmployeeId(new UUID(PERSON, person));
    }

    static long person(EmployeeId employee) {
        return number(employee.value(), PERSON, "employee");
    }

    static OrgUnitId orgUnit(long unit) {
        return new OrgUnitId(new UUID(UNIT, unit));
    }

    static long unit(OrgUnitId orgUnit) {
        return number(orgUnit.value(), UNIT, "org unit");
    }

    /**
     * A form field value as the replay's process reads it: a number of the export becomes the UUID of that person or department, and 0,
     * the export's "nobody", becomes blank.
     */
    static String employeeText(String raw) {
        Long number = numberOrNull(raw);
        return number == null ? raw : number == 0 ? "" : employee(number).toString();
    }

    static String orgUnitText(String raw) {
        Long number = numberOrNull(raw);
        return number == null ? raw : number == 0 ? "" : orgUnit(number).toString();
    }

    private static Long numberOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static long number(UUID id, long kind, String what) {
        if (id.getMostSignificantBits() != kind) {
            throw new IllegalArgumentException(what + " " + id + " was not made by the replay");
        }
        return id.getLeastSignificantBits();
    }
}
