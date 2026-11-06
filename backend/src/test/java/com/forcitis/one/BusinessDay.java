package com.forcitis.one;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

/**
 * The business today of the integration tests: the date in Asia/Shanghai that the database triggers also use ({@code transaction_timestamp()} at that zone),
 * read once per JVM so that every test of a run agrees on it. An integration test that writes to a date-guarded table (standard hours, work calendar, leave)
 * takes its "today" from here and builds every other day relative to it, never from a fixed calendar date: a fixed date is correct on the day it is written
 * and wrong from the day the real date passes it (#1310). Fixed dates stay for historical facts that do not move with the clock (a hire date, a past year).
 */
public final class BusinessDay {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalDate TODAY = LocalDate.now(ZONE);

    private BusinessDay() {
    }

    /** The business today. */
    public static LocalDate today() {
        return TODAY;
    }

    /**
     * The real instant, for an integration test whose application and database run on the real clock and compare their own stamps with it. This class and
     * {@link #today()} are the only places of the test tree that read the machine's clock (registered in architecture/test-clock-exceptions.txt, enforced by
     * TimeSourceGuardTest); a test that can move its own clock takes a MutableClock or Clock.fixed instead.
     */
    public static Instant instant() {
        return Instant.now();
    }

    /** The UTC month of the real instant: the audit partition the application has just created. */
    public static YearMonth utcMonth() {
        return YearMonth.now(ZoneOffset.UTC);
    }

    /** A clock stopped at the real instant of this call, for a test that needs a Clock but not a moving one. */
    public static Clock fixedClock() {
        return Clock.fixed(Instant.now(), ZoneOffset.UTC);
    }

    /** 10:00 on the business today in the business zone: the instant a pinned test clock starts at. */
    public static Instant tenOClock() {
        return TODAY.atTime(10, 0).atZone(ZONE).toInstant();
    }

    /** The day {@code days} from the business today (negative: before). */
    public static LocalDate daysFromToday(int days) {
        return TODAY.plusDays(days);
    }

    /** The first day on or after the business today that falls on the given weekday. */
    public static LocalDate nextOrSame(DayOfWeek weekday) {
        return TODAY.with(TemporalAdjusters.nextOrSame(weekday));
    }
}
