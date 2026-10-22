package com.forcitis.one.platform.audit.api;

import java.time.YearMonth;

public interface AuditPartitions {
    /** Read only: a month (UTC) without a partition cannot take an audit entry, and only the resident application creates partitions. */
    boolean exists(YearMonth month);
}
