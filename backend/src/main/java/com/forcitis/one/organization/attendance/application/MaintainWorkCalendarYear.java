package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the default calendar is maintained for now; the legal-entity calendar is reserved in the model and the schema. */
@Service
public class MaintainWorkCalendarYear {
    private final WorkCalendarRepository calendars;
    private final DomainEvents events;
    private final Clock clock;
    private final BusinessZone zone;

    MaintainWorkCalendarYear(WorkCalendarRepository calendars, DomainEvents events, Clock clock, BusinessZone zone) {
        this.calendars = calendars;
        this.events = events;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public WorkCalendarYearView execute(MaintainWorkCalendarCommand command) {
        WorkCalendarKey key = WorkCalendarKey.defaultFor(command.calendarYear());
        WorkCalendarYear year = calendars.find(key).orElseGet(() -> WorkCalendarYear.unmaintained(key));
        year.maintain(command.days().stream().map(DayEntry::toDay).toList(), zone.today(clock), command.lockedDaysConfirmed());
        calendars.save(year);
        events.publish(year);
        return WorkCalendarYearView.of(year);
    }
}
