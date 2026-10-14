package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/** A make-up workday on a weekday would change nothing, so it is refused as a typing mistake. */
@ValueObject
public record CalendarDay(LocalDate calendarDate, DayKind dayKind) {

    public CalendarDay {
        Objects.requireNonNull(calendarDate, "calendarDate");
        Objects.requireNonNull(dayKind, "dayKind");
        boolean weekend = calendarDate.getDayOfWeek() == DayOfWeek.SATURDAY || calendarDate.getDayOfWeek() == DayOfWeek.SUNDAY;
        if (dayKind == DayKind.MAKE_UP_WORKDAY && !weekend) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY, ErrorKind.UNPROCESSABLE,
                    calendarDate + " is a weekday; a make-up workday must fall on a Saturday or Sunday");
        }
    }
}
