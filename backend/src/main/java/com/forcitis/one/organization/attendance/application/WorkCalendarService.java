package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A legal entity follows its own calendar for a year if it has one, otherwise the default calendar. A year nobody maintained is
 * never guessed: the answer fails and an alert is logged, because deadlines computed on an assumed calendar are wrong silently.
 */
@Component
class WorkCalendarService implements WorkCalendar {
    private static final Logger LOG = LoggerFactory.getLogger(WorkCalendarService.class);
    static final int MAX_WORKDAYS = 3660;

    private final WorkCalendarRepository calendars;
    private final BusinessZone businessZone;
    private final Counter unmaintained;

    WorkCalendarService(WorkCalendarRepository calendars, BusinessZone businessZone, MeterRegistry meters) {
        this.calendars = calendars;
        this.businessZone = businessZone;
        this.unmaintained = Counter.builder("forcitis.organization.attendance.calendar.unmaintained")
                .description("Work calendar answers refused because the year was not maintained").register(meters);
    }

    @Override
    public boolean isWorkday(LocalDate date, LegalEntityId legalEntityId) {
        return isWorkday(date, legalEntityId, new HashMap<>());
    }

    @Override
    public Instant endOfWorkdayAfter(LocalDate startDate, int n, LegalEntityId legalEntityId) {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        if (n < 1 || n > MAX_WORKDAYS) {
            throw new IllegalArgumentException("n must be 1.." + MAX_WORKDAYS + ": " + n);
        }
        Map<Integer, WorkCalendarYear> loaded = new HashMap<>();
        LocalDate day = startDate;
        int counted = 0;
        while (counted < n) {
            day = day.plusDays(1);
            if (isWorkday(day, legalEntityId, loaded)) {
                counted++;
            }
        }
        return day.plusDays(1).atStartOfDay(businessZone.zone()).toInstant();
    }

    private boolean isWorkday(LocalDate date, LegalEntityId legalEntityId, Map<Integer, WorkCalendarYear> loaded) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        WorkCalendarYear year = loaded.get(date.getYear());
        if (year == null) {
            year = calendars.find(new WorkCalendarKey(legalEntityId, date.getYear()))
                    .or(() -> calendars.find(WorkCalendarKey.defaultFor(date.getYear())))
                    .orElseThrow(() -> notMaintained(date.getYear()));
            loaded.put(date.getYear(), year);
        }
        return year.isWorkday(date);
    }

    private DomainException notMaintained(int calendarYear) {
        unmaintained.increment();
        LOG.error("ALERT organization.attendance work calendar of {} is not maintained; an administrator must save it", calendarYear);
        return new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED, ErrorKind.UNPROCESSABLE,
                "the work calendar of " + calendarYear + " has not been maintained");
    }
}
