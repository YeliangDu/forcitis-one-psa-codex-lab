package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.events.WorkCalendarYearMaintained;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One maintained year of one calendar. A year with no exception is still maintained: saving it says "this year has been checked". */
public final class WorkCalendarYear extends AggregateRoot {
    private final Long persistedId;
    private final WorkCalendarKey key;
    private Map<LocalDate, DayKind> exceptions;

    private WorkCalendarYear(Long persistedId, WorkCalendarKey key, Map<LocalDate, DayKind> exceptions, long version) {
        super(version);
        this.persistedId = persistedId;
        this.key = Objects.requireNonNull(key, "key");
        this.exceptions = Map.copyOf(exceptions);
    }

    public static WorkCalendarYear unmaintained(WorkCalendarKey key) {
        return new WorkCalendarYear(null, key, Map.of(), 0);
    }

    public static WorkCalendarYear restore(long persistedId, WorkCalendarKey key, List<CalendarDay> days, long version) {
        Map<LocalDate, DayKind> restored = new TreeMap<>();
        days.forEach(day -> restored.put(day.calendarDate(), day.dayKind()));
        return new WorkCalendarYear(persistedId, key, restored, version);
    }

    public Long persistedId() {
        return persistedId;
    }

    public WorkCalendarKey key() {
        return key;
    }

    public List<CalendarDay> days() {
        return exceptions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new CalendarDay(e.getKey(), e.getValue())).toList();
    }

    public boolean isWorkday(LocalDate date) {
        if (date.getYear() != key.calendarYear()) {
            throw new IllegalArgumentException(date + " is not in calendar year " + key.calendarYear());
        }
        DayKind exception = exceptions.get(date);
        if (exception != null) {
            return exception == DayKind.MAKE_UP_WORKDAY;
        }
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Replaces the whole year: what the administrator saves is the complete list of exceptions. */
    public void maintain(List<CalendarDay> days) {
        Map<LocalDate, DayKind> next = new TreeMap<>();
        for (CalendarDay day : days) {
            if (day.calendarDate().getYear() != key.calendarYear()) {
                throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR, ErrorKind.UNPROCESSABLE,
                        day.calendarDate() + " is not in calendar year " + key.calendarYear());
            }
            if (next.put(day.calendarDate(), day.dayKind()) != null) {
                throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_DUPLICATE, ErrorKind.UNPROCESSABLE,
                        day.calendarDate() + " is listed twice");
            }
        }
        this.exceptions = Map.copyOf(next);
        record((envelopes, version) -> new WorkCalendarYearMaintained(envelopes.wrap(new EventDraft(
                "organization.attendance.WorkCalendarYearMaintained", 1, "WorkCalendarYear", key.asText(), version,
                key.legalEntityId(), null, new EventSource(SourceSystem.ONE, null), List.of())), Year.of(key.calendarYear())));
    }
}
