package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the default calendar is maintained for now; the legal-entity calendar is reserved in the model and the schema. */
@Service
public class MaintainWorkCalendarYear {
    private final WorkCalendarRepository calendars;
    private final DomainEvents events;

    MaintainWorkCalendarYear(WorkCalendarRepository calendars, DomainEvents events) {
        this.calendars = calendars;
        this.events = events;
    }

    @Transactional
    public WorkCalendarYearView execute(int calendarYear, List<DayEntry> days) {
        WorkCalendarKey key = WorkCalendarKey.defaultFor(calendarYear);
        WorkCalendarYear year = calendars.find(key).orElseGet(() -> WorkCalendarYear.unmaintained(key));
        year.maintain(days.stream().map(DayEntry::toDay).toList());
        calendars.save(year);
        events.publish(year);
        return WorkCalendarYearView.of(year);
    }
}
