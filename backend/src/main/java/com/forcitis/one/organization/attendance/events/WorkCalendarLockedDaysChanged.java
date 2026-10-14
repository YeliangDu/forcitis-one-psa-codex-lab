package com.forcitis.one.organization.attendance.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

public record WorkCalendarLockedDaysChanged(EventEnvelope envelope, Year calendarYear, List<LocalDate> lockedDates) implements DomainEvent {
}
