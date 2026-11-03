package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.ValueObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;

/** How long upholding a sensitive-role review lasts: until 00:00 of the first day of the next natural quarter, in the business zone (ADR-0044 decision 2 item 2). */
@ValueObject
public record UpholdExpiry(Instant value) {

    public static UpholdExpiry after(Instant upheldAt, BusinessZone zone) {
        LocalDate day = zone.dateOf(upheldAt);
        int nextQuarterFirstMonth = (day.getMonthValue() - 1) / 3 * 3 + 4;
        LocalDate first = nextQuarterFirstMonth > 12 ? LocalDate.of(day.getYear() + 1, Month.JANUARY, 1) : LocalDate.of(day.getYear(), nextQuarterFirstMonth, 1);
        return new UpholdExpiry(first.atStartOfDay(zone.zone()).toInstant());
    }
}
