package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.organization.attendance.api.DayKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** A maintained year as it is stored, for reading; changes go through WorkCalendarYear. */
public record WorkCalendarYearRecord(WorkCalendarKey key, List<CalendarDay> days, long version) {
    public WorkCalendarYearRecord {
        days = List.copyOf(days);
    }

    public WorkCalendarYearRecord(WorkCalendarKey key, List<CalendarDay> days) {
        this(key, days, 0);
    }

    public boolean isWorkday(LocalDate date) {
        if (date.getYear() != key.calendarYear()) {
            throw new IllegalArgumentException(date + " is not in calendar year " + key.calendarYear());
        }
        Map<LocalDate, DayKind> exceptions = days.stream().collect(Collectors.toMap(CalendarDay::calendarDate, CalendarDay::dayKind));
        return WorkdayRules.isWorkday(date, exceptions);
    }

    public boolean isMakeUpWorkday(LocalDate date) {
        return days.stream().anyMatch(day -> day.calendarDate().equals(date) && day.dayKind() == DayKind.MAKE_UP_WORKDAY);
    }
}
