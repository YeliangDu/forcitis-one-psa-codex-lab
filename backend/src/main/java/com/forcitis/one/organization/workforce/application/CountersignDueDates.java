package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The day a countersign request falls due: three working days after the request (ADR-0073 item 3.6, ADR-0072 calendar). When the calendar of the year is not
 * confirmed, Monday to Friday count and an alert says so (the note of #878 section 5.2).
 */
@Component
class CountersignDueDates {
    private static final Logger LOG = LoggerFactory.getLogger(CountersignDueDates.class);
    static final int WORKING_DAYS = 3;

    private final WorkCalendar calendar;
    private final BusinessZone zone;

    CountersignDueDates(WorkCalendar calendar, BusinessZone zone) {
        this.calendar = calendar;
        this.zone = zone;
    }

    LocalDate after(LocalDate requestDay, LegalEntityId legalEntity) {
        try {
            return zone.dateOf(calendar.endOfWorkdayAfter(requestDay, WORKING_DAYS, legalEntity).minusNanos(1));
        } catch (DomainException unconfirmed) {
            LOG.error("ALERT organization.workforce the work calendar around {} is not confirmed for legal entity {}: a countersign falls due after {} weekdays", requestDay,
                    legalEntity.value(), WORKING_DAYS);
            LocalDate day = requestDay;
            for (int counted = 0; counted < WORKING_DAYS; ) {
                day = day.plusDays(1);
                if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    counted++;
                }
            }
            return day;
        }
    }
}
