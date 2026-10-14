package com.forcitis.one.kernel;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accounting period: calendar year + month (ADR-0016 decision 8). Only the coordinates of a period; whether a period is open
 * or closed for a legal entity is state owned by accounting.period, not part of this type.
 */
@ValueObject
public record AccountingPeriod(int year, int month) implements Comparable<AccountingPeriod> {

    public static final int MIN_YEAR = 1900;
    public static final int MAX_YEAR = 9999;

    private static final Pattern COMPACT = Pattern.compile("(\\d{4})(\\d{2})");

    public AccountingPeriod {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new IllegalArgumentException("Accounting period year must be " + MIN_YEAR + ".." + MAX_YEAR + ": " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Accounting period month must be 1..12: " + month);
        }
    }

    public static AccountingPeriod of(int year, int month) {
        return new AccountingPeriod(year, month);
    }

    /** The period a business date falls into. */
    public static AccountingPeriod containing(LocalDate date) {
        return new AccountingPeriod(date.getYear(), date.getMonthValue());
    }

    /** Parses the compact form yyyyMM, e.g. 202610. */
    public static AccountingPeriod parse(String compact) {
        Matcher m = COMPACT.matcher(Objects.requireNonNull(compact, "compact"));
        if (!m.matches()) {
            throw new IllegalArgumentException("Accounting period must be yyyyMM: " + compact);
        }
        return new AccountingPeriod(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    public AccountingPeriod next() {
        return fromYearMonth(YearMonth.of(year, month).plusMonths(1));
    }

    public AccountingPeriod previous() {
        return fromYearMonth(YearMonth.of(year, month).minusMonths(1));
    }

    public boolean isBefore(AccountingPeriod other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(AccountingPeriod other) {
        return compareTo(other) > 0;
    }

    /** Whether {@code other} is the period directly after this one. */
    public boolean isFollowedBy(AccountingPeriod other) {
        return next().equals(other);
    }

    public LocalDate firstDay() {
        return LocalDate.of(year, month, 1);
    }

    public LocalDate lastDay() {
        return YearMonth.of(year, month).atEndOfMonth();
    }

    public boolean contains(LocalDate date) {
        return containing(date).equals(this);
    }

    @Override
    public int compareTo(AccountingPeriod other) {
        int byYear = Integer.compare(year, other.year);
        return byYear != 0 ? byYear : Integer.compare(month, other.month);
    }

    /** Compact form yyyyMM. */
    @Override
    public String toString() {
        return String.format("%04d%02d", year, month);
    }

    private static AccountingPeriod fromYearMonth(YearMonth ym) {
        return new AccountingPeriod(ym.getYear(), ym.getMonthValue());
    }
}
