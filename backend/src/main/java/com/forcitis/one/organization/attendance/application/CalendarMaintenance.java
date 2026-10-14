package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Shared by the two ways to maintain a year; what differs is who may do it and whether days before today may change. */
@Component
class CalendarMaintenance {
    private final WorkCalendarRepository calendars;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    CalendarMaintenance(WorkCalendarRepository calendars, DomainEvents events, Clock clock, BusinessZone zone) {
        this.calendars = calendars;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    WorkCalendarYearView run(MaintainWorkCalendarCommand command, boolean lockedDaysConfirmed) {
        WorkCalendarKey key = WorkCalendarKey.defaultFor(command.calendarYear());
        WorkCalendarYear stored = calendars.find(key).orElse(null);
        command.expected().check(stored == null ? null : stored.version());
        WorkCalendarYear year = stored != null ? stored : WorkCalendarYear.unmaintained(key);
        year.maintain(command.days().stream().map(DayEntry::toDay).toList(), zone.today(clock), lockedDaysConfirmed);
        calendars.save(year);
        events.publish(year);
        return WorkCalendarYearView.of(year);
    }
}
