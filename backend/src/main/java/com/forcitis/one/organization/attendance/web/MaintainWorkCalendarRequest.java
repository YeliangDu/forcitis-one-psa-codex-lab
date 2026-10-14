package com.forcitis.one.organization.attendance.web;

import java.time.LocalDate;
import java.util.List;

record MaintainWorkCalendarRequest(List<CalendarDayRequest> days) {
    MaintainWorkCalendarRequest {
        days = days == null ? List.of() : List.copyOf(days);
    }

    record CalendarDayRequest(LocalDate calendarDate, String dayKind) {
    }
}
