package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.ValueObject;
import java.time.LocalDate;

@ValueObject
public record LockedDayChange(LocalDate calendarDate, DayException before, DayException after) {
}
