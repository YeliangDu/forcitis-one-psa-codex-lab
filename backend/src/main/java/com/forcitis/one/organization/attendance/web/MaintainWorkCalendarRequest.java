package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.organization.attendance.api.DayKind;
import java.time.LocalDate;
import java.util.List;

/** lockedDaysConfirmed: the maintainer knows that days before today change and accepts that deadlines already worked out may move. */
record MaintainWorkCalendarRequest(List<CalendarDayRequest> days, @org.jspecify.annotations.Nullable Boolean lockedDaysConfirmed) {
    MaintainWorkCalendarRequest {
        days = days == null ? List.of() : List.copyOf(days);
    }

    boolean confirmed() {
        return Boolean.TRUE.equals(lockedDaysConfirmed);
    }

    record CalendarDayRequest(LocalDate calendarDate, DayKind dayKind) {
    }
}
