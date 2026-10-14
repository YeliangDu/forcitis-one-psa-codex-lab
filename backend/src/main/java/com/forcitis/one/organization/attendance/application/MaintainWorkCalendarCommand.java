package com.forcitis.one.organization.attendance.application;

import java.util.List;
import java.util.Objects;

public record MaintainWorkCalendarCommand(int calendarYear, List<DayEntry> days) {
    public MaintainWorkCalendarCommand {
        days = List.copyOf(Objects.requireNonNull(days, "days"));
    }
}
