package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import java.time.LocalDate;

public record DayEntry(LocalDate calendarDate, String dayKind) {
    static DayEntry of(CalendarDay day) {
        return new DayEntry(day.calendarDate(), day.dayKind().name());
    }

    CalendarDay toDay() {
        try {
            return new CalendarDay(calendarDate, DayKind.valueOf(dayKind));
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN, ErrorKind.UNPROCESSABLE,
                    "day kind must be HOLIDAY or MAKE_UP_WORKDAY and the date must be given");
        }
    }
}
