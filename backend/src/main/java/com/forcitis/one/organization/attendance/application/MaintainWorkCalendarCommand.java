package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.ExpectedVersion;
import java.util.List;
import java.util.Objects;

public record MaintainWorkCalendarCommand(int calendarYear, List<DayEntry> days, ExpectedVersion expected) {
    public MaintainWorkCalendarCommand {
        days = List.copyOf(Objects.requireNonNull(days, "days"));
        Objects.requireNonNull(expected, "expected");
    }
}
