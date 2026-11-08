package com.forcitis.one.organization.leave.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.leave.api.AttendanceRecordInput;
import com.forcitis.one.organization.leave.domain.AttendanceRecordRepository;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the entire input before appending any facts, in one transaction. */
@Service
public class ImportAttendanceRecords implements com.forcitis.one.organization.leave.api.ImportAttendanceRecords {
    private final OrgDirectory directory;
    private final AttendanceRecordRepository records;
    private final Clock clock;

    ImportAttendanceRecords(OrgDirectory directory, AttendanceRecordRepository records, Clock clock) {
        this.directory = directory;
        this.records = records;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(List<AttendanceRecordInput> inputs) {
        List<AttendanceRecordInput> batch = List.copyOf(inputs);
        // Resolve duplicates too: a known key must never bypass employee validation.
        for (AttendanceRecordInput input : batch) {
            if (directory.findEmployee(input.employeeId()).isEmpty()) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_NOT_FOUND,
                        ErrorKind.NOT_FOUND, "attendance record employee cannot be resolved");
            }
        }
        Instant createdAt = clock.instant();
        // Sequential inserts preserve the first supplied record for a repeated batch key.
        for (AttendanceRecordInput input : batch) {
            records.insertIfAbsent(input, createdAt);
        }
    }
}
