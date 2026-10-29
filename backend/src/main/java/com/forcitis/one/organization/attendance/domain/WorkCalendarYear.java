package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.api.DayException;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.api.LockedDayChange;
import com.forcitis.one.organization.attendance.events.WorkCalendarLockedDaysChanged;
import com.forcitis.one.organization.attendance.events.WorkCalendarYearMaintained;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One maintained year of one calendar. A year with no exception is still maintained: saving it says "this year has been checked". */
public final class WorkCalendarYear extends AggregateRoot {
    private final UUID id;
    private final WorkCalendarKey key;
    private Map<LocalDate, DayKind> exceptions;

    private WorkCalendarYear(UUID id, WorkCalendarKey key, Map<LocalDate, DayKind> exceptions, long version, long eventSequence) {
        super(version, eventSequence);
        this.id = Objects.requireNonNull(id, "id");
        this.key = Objects.requireNonNull(key, "key");
        this.exceptions = Map.copyOf(exceptions);
    }

    public static WorkCalendarYear unmaintained(UUID id, WorkCalendarKey key) {
        return new WorkCalendarYear(id, key, Map.of(), 0, 0);
    }

    public static WorkCalendarYear restore(UUID id, WorkCalendarKey key, List<CalendarDay> days, long version, long eventSequence) {
        Map<LocalDate, DayKind> restored = new TreeMap<>();
        days.forEach(day -> restored.put(day.calendarDate(), day.dayKind()));
        return new WorkCalendarYear(id, key, restored, version, eventSequence);
    }

    public UUID id() {
        return id;
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
        return WorkdayRules.isWorkday(date, exceptions);
    }

    /**
     * Replaces the whole year: what the administrator saves is the complete list of exceptions. In a year that was maintained before, a
     * day before today is locked, because deadlines already computed on it are not recomputed; changing one needs lockedDaysConfirmed and
     * is recorded as its own event with the kind before and after. The first maintenance of a year is not a change of anything computed.
     */
    public void maintain(List<CalendarDay> days, LocalDate today, boolean lockedDaysConfirmed) {
        Objects.requireNonNull(today, "today");
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
        List<LockedDayChange> locked = version() == 0 ? List.of() : changedDates(exceptions, next).stream().filter(date -> date.isBefore(today))
                .map(date -> new LockedDayChange(date, exception(exceptions.get(date)), exception(next.get(date)))).toList();
        if (!locked.isEmpty() && !lockedDaysConfirmed) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED, ErrorKind.UNPROCESSABLE,
                    "days before " + today + " are locked: " + locked.stream().map(LockedDayChange::calendarDate).limit(5).toList() + (locked.size() > 5 ? " and " + (locked.size() - 5) + " more" : ""));
        }
        List<LocalDate> changedDates = changedDates(exceptions, next);
        this.exceptions = Map.copyOf(next);
        record((envelopes, version) -> new WorkCalendarYearMaintained(envelopes.wrap(new EventDraft(
                "organization.attendance.WorkCalendarYearMaintained", 1, "WorkCalendarYear", id, version,
                key.legalEntityId(), null, List.of())), Year.of(key.calendarYear()), changedDates));
        if (!locked.isEmpty()) {
            record((envelopes, version) -> new WorkCalendarLockedDaysChanged(envelopes.wrap(new EventDraft(
                    "organization.attendance.WorkCalendarLockedDaysChanged", 1, "WorkCalendarYear", id, version,
                    key.legalEntityId(), null, List.of())), Year.of(key.calendarYear()), locked));
        }
    }

    private static DayException exception(@Nullable DayKind kind) {
        return kind == null ? DayException.NONE : DayException.valueOf(kind.name());
    }

    private static List<LocalDate> changedDates(Map<LocalDate, DayKind> before, Map<LocalDate, DayKind> after) {
        TreeSet<LocalDate> changed = new TreeSet<>();
        before.forEach((date, kind) -> {
            if (after.get(date) != kind) {
                changed.add(date);
            }
        });
        after.forEach((date, kind) -> {
            if (before.get(date) != kind) {
                changed.add(date);
            }
        });
        return List.copyOf(changed);
    }
}
