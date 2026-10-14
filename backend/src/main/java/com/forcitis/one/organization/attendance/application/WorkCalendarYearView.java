package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import java.util.List;

public record WorkCalendarYearView(int calendarYear, List<DayEntry> days) {
    static WorkCalendarYearView of(WorkCalendarYear year) {
        return new WorkCalendarYearView(year.key().calendarYear(), year.days().stream().map(DayEntry::of).toList());
    }
}
