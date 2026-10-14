package com.forcitis.one.organization.attendance.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/** Monday to Friday work, the weekend rests, unless the calendar names the day as an exception. */
public final class WorkdayRules {
    private WorkdayRules() {
    }

    public static boolean isWorkday(LocalDate date, Map<LocalDate, DayKind> exceptions) {
        DayKind exception = exceptions.get(date);
        if (exception != null) {
            return exception == DayKind.MAKE_UP_WORKDAY;
        }
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
