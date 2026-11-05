package com.forcitis.one;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
