package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkdayDeadlines;
import java.time.LocalDate;

/**
 * Business days in Asia/Shanghai. Fails (never assumes "all workdays") when a year that the answer depends on has not been
 * maintained by an administrator.
 */
public interface WorkCalendar extends WorkdayDeadlines {

    boolean isWorkday(LocalDate date, LegalEntityId legalEntityId);
}
