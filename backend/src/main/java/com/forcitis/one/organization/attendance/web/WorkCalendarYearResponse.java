package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.organization.attendance.application.WorkCalendarYearView;
import com.forcitis.one.organization.attendance.api.DayKind;
import java.time.LocalDate;
import java.util.List;

record WorkCalendarYearResponse(int calendarYear, List<CalendarDayResponse> days) {
    static WorkCalendarYearResponse of(WorkCalendarYearView view) {
        return new WorkCalendarYearResponse(view.calendarYear(),
                view.days().stream().map(d -> new CalendarDayResponse(d.calendarDate(), DayKind.valueOf(d.dayKind()))).toList());
    }

    record CalendarDayResponse(LocalDate calendarDate, DayKind dayKind) {
    }
}
