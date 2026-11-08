package com.forcitis.one.organization.leave.api;

import java.util.List;

/** Imports source attendance facts without interpreting their calendar or employment state. */
public interface ImportAttendanceRecords {
    /**
     * Resolves every employee, including employees on duplicate keys, and imports the batch atomically.
     * Known source/key pairs remain unchanged; duplicates within the batch keep the first record.
     * An unresolvable employee refuses the whole batch.
     */
    void execute(List<AttendanceRecordInput> records);
}
