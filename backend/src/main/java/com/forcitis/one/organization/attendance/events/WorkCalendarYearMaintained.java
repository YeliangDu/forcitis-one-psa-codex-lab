package com.forcitis.one.organization.attendance.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * changedDates are the days whose kind differs from before; lockedDatesChanged are those of them before the business day of the
 * change: a correction of the past, confirmed by the maintainer, which the audit trail must show.
 */
public record WorkCalendarYearMaintained(EventEnvelope envelope, Year calendarYear, List<LocalDate> changedDates,
                                         List<LocalDate> lockedDatesChanged) implements DomainEvent {
    public WorkCalendarYearMaintained {
        changedDates = List.copyOf(changedDates);
        lockedDatesChanged = List.copyOf(lockedDatesChanged);
    }
}
