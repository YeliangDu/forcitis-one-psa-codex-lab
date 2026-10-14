package com.forcitis.one.organization.attendance.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import java.time.Year;

public record WorkCalendarYearMaintained(EventEnvelope envelope, Year calendarYear) implements DomainEvent {
}
