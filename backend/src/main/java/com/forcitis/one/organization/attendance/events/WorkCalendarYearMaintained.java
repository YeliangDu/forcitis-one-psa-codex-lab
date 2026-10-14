package com.forcitis.one.organization.attendance.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/** changedDates are the days added, removed or given another kind by this save, in date order; empty when the year was only re-checked. */
public record WorkCalendarYearMaintained(EventEnvelope envelope, Year calendarYear, List<LocalDate> changedDates) implements DomainEvent {
    public WorkCalendarYearMaintained {
        changedDates = List.copyOf(changedDates);
    }
}
