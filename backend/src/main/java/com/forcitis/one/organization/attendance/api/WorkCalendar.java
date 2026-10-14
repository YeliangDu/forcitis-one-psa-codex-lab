package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Business days in Asia/Shanghai. Fails (never assumes "all workdays") when a year that the answer depends on has not been
 * maintained by an administrator.
 */
public interface WorkCalendar {

    /** End (24:00 business zone) of the n-th workday after startDate; startDate itself never counts. n >= 1. */
    Instant endOfWorkdayAfter(LocalDate startDate, int n, LegalEntityId legalEntityId);

    boolean isWorkday(LocalDate date, LegalEntityId legalEntityId);
}
