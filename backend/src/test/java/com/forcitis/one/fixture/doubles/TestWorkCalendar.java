package com.forcitis.one.fixture.doubles;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Monday to Friday are workdays; tests add public holidays and make-up workdays. One calendar for every legal entity. */
public class TestWorkCalendar implements WorkCalendar {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final Set<LocalDate> holidays = ConcurrentHashMap.newKeySet();
    private final Set<LocalDate> makeUpWorkdays = ConcurrentHashMap.newKeySet();
    private volatile String failure;

    public void holiday(LocalDate date) {
        holidays.add(date);
    }

    public void makeUpWorkday(LocalDate date) {
        makeUpWorkdays.add(date);
    }

    public void reset() {
        holidays.clear();
        makeUpWorkdays.clear();
        failure = null;
    }

    /** The calendar of a year nobody maintained: every answer fails. */
    public void failWith(String message) {
        failure = message;
    }

    @Override
    public Instant endOfWorkdayAfter(LocalDate startDate, int n, LegalEntityId legalEntityId) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
        LocalDate day = startDate;
        int counted = 0;
        while (counted < n) {
            day = day.plusDays(1);
            if (isWorkday(day, legalEntityId)) {
                counted++;
            }
        }
        return day.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    @Override
    public boolean isWorkday(LocalDate date, LegalEntityId legalEntityId) {
        if (makeUpWorkdays.contains(date)) {
            return true;
        }
        DayOfWeek weekday = date.getDayOfWeek();
        return weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY && !holidays.contains(date);
    }
}
