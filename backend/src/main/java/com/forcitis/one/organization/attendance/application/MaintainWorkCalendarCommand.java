package com.forcitis.one.organization.attendance.application;

import java.util.List;
import java.util.Objects;

/** lockedDaysConfirmed: the caller is an organisation data administrator who confirms changing days before today. */
public record MaintainWorkCalendarCommand(int calendarYear, List<DayEntry> days, boolean lockedDaysConfirmed) {
    public MaintainWorkCalendarCommand {
        days = List.copyOf(Objects.requireNonNull(days, "days"));
    }
}
