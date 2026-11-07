package com.forcitis.one.organization.leave.domain;

import com.forcitis.one.organization.leave.api.AttendanceRecordInput;
import java.time.Instant;

/** Append-only storage of source attendance facts. */
public interface AttendanceRecordRepository {
    /** Inserts once per source/key pair; replay and concurrent duplicate inserts leave the old row untouched. */
    void insertIfAbsent(AttendanceRecordInput record, Instant createdAt);
}
