package com.forcitis.one.organization.leave.api;

import com.forcitis.one.kernel.EmployeeId;
import java.time.Instant;
import java.util.Objects;

/** One source fact, identified for replay by its source system and external key. */
public record AttendanceRecordInput(String sourceSystem, String externalRecordKey,
                                    EmployeeId employeeId, AttendanceRecordKind recordKind,
                                    Instant occurredAt) {
    public AttendanceRecordInput {
        Objects.requireNonNull(sourceSystem, "sourceSystem");
        Objects.requireNonNull(externalRecordKey, "externalRecordKey");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(recordKind, "recordKind");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
