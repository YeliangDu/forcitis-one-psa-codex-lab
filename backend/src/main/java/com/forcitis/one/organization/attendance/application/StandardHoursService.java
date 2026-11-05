package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.api.StandardHours;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.organization.attendance.domain.DateOverride;
import com.forcitis.one.organization.attendance.domain.StandardHoursQueries;
import com.forcitis.one.organization.attendance.domain.StandardHoursVersionRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * An entity that has a version of its own follows its own timeline alone, so a gap in it is an error; an entity with none uses the
 * DEFAULT timeline. No version covering the date fails and raises an alert: a made-up number would be wrong silently.
 */
@Component
class StandardHoursService implements StandardHours {
    private static final Logger LOG = LoggerFactory.getLogger(StandardHoursService.class);

    private final StandardHoursQueries versions;
    private final WorkCalendar calendar;
    private final Counter missing;

    StandardHoursService(StandardHoursQueries versions, WorkCalendar calendar, MeterRegistry meters) {
        this.versions = versions;
        this.calendar = calendar;
        this.missing = Counter.builder("forcitis.organization.attendance.standard.hours.missing")
                .description("Standard minutes answers refused because no version covered the date").register(meters);
    }

    @Override
    public int minutesOn(LegalEntityId legalEntityId, LocalDate date) {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(date, "date");
        LegalEntityId scope = versions.hasVersionInEffect(legalEntityId) ? legalEntityId : null;
        StandardHoursVersionRecord version = versions.inEffectOn(scope, date).orElseThrow(() -> notFound(legalEntityId, date));
        if (!calendar.isWorkday(date, legalEntityId)) {
            return 0;
        }
        // The calendar only lets a make-up workday fall on a weekend, so a workday on a weekend is one.
        boolean makeUp = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        return overrideOn(version, date, makeUp ? version.adjustedWorkdayMinutes() : version.workdayMinutes());
    }

    private static int overrideOn(StandardHoursVersionRecord version, LocalDate date, int ordinary) {
        return version.dateOverrides().stream().filter(o -> o.workDate().equals(date)).map(DateOverride::minutes).findFirst().orElse(ordinary);
    }

    private DomainException notFound(LegalEntityId legalEntityId, LocalDate date) {
        missing.increment();
        LOG.error("ALERT organization.attendance no standard hours version covers {} for legal entity {}; an administrator must publish one", date, legalEntityId);
        return new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_FOUND, ErrorKind.NOT_FOUND,
                "no standard hours version covers " + date);
    }
}
