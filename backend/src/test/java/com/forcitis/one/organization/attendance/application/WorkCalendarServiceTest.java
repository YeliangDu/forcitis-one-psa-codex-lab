package com.forcitis.one.organization.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarQueries;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYearRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkCalendarServiceTest {
    static final LegalEntityId FST = new LegalEntityId(TestIds.uuid(1));

    static class MemoryCalendars implements WorkCalendarQueries {
        final Map<WorkCalendarKey, WorkCalendarYearRecord> years = new HashMap<>();

        @Override
        public Optional<WorkCalendarYearRecord> find(WorkCalendarKey key) {
            return Optional.ofNullable(years.get(key));
        }

        @Override
        public List<Integer> findMaintainedYearsBefore(LegalEntityId legalEntityId, Integer beforeYear, int limit) {
            return years.keySet().stream().filter(k -> java.util.Objects.equals(k.legalEntityId(), legalEntityId))
                    .map(WorkCalendarKey::calendarYear).filter(year -> beforeYear == null || year < beforeYear)
                    .sorted(java.util.Comparator.reverseOrder()).limit(limit).toList();
        }

        void maintain(WorkCalendarKey key, CalendarDay... days) {
            years.put(key, new WorkCalendarYearRecord(key, List.of(days)));
        }
    }

    MemoryCalendars calendars;
    SimpleMeterRegistry meters;
    WorkCalendarService service;

    static CalendarDay holiday(String date) {
        return new CalendarDay(LocalDate.parse(date), DayKind.HOLIDAY);
    }

    static CalendarDay makeUp(String date) {
        return new CalendarDay(LocalDate.parse(date), DayKind.MAKE_UP_WORKDAY);
    }

    static Instant midnight(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
    }

    @BeforeEach
    void setUp() {
        calendars = new MemoryCalendars();
        meters = new SimpleMeterRegistry();
        service = new WorkCalendarService(calendars, new BusinessZone(ZoneId.of("Asia/Shanghai")), meters);
    }

    @Test
    @DisplayName("without exceptions weekends rest and the other days work")
    void weekendsRestByDefault() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));

        assertThat(service.isWorkday(LocalDate.parse("2026-10-09"), FST)).isTrue();
        assertThat(service.isWorkday(LocalDate.parse("2026-10-10"), FST)).isFalse();
        assertThat(service.isWorkday(LocalDate.parse("2026-10-11"), FST)).isFalse();
        assertThat(service.isWorkday(LocalDate.parse("2026-10-12"), FST)).isTrue();
    }

    @Test
    @DisplayName("the n-th workday after a Friday skips the weekend and ends at 24:00 in Asia/Shanghai")
    void spansTheWeekend() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));

        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 1, FST)).isEqualTo(midnight("2026-10-13"));
        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 5, FST)).isEqualTo(midnight("2026-10-17"));
        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 1, FST)).isEqualTo(Instant.parse("2026-10-12T16:00:00Z"));
    }

    @Test
    @DisplayName("#730 A-6: no legal entity counts on the default calendar, an entity with its own holidays counts on its own")
    void noLegalEntityUsesTheDefaultCalendar() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));
        calendars.maintain(new WorkCalendarKey(FST, 2026), holiday("2026-10-12"));

        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 1, null)).isEqualTo(midnight("2026-10-13"));
        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 1, FST)).isEqualTo(midnight("2026-10-14"));
    }

    @Test
    @DisplayName("a start date that is itself a rest day never counts")
    void startDateNeverCounts() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));

        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-10"), 1, FST)).isEqualTo(midnight("2026-10-13"));
    }

    @Test
    @DisplayName("a holiday on a weekday is skipped and a make-up workday on a weekend counts")
    void holidayAndMakeUpWorkday() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026), makeUp("2026-10-10"), holiday("2026-10-12"));

        assertThat(service.isWorkday(LocalDate.parse("2026-10-10"), FST)).isTrue();
        assertThat(service.isWorkday(LocalDate.parse("2026-10-12"), FST)).isFalse();
        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 1, FST)).isEqualTo(midnight("2026-10-11"));
        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 2, FST)).isEqualTo(midnight("2026-10-14"));
    }

    @Test
    @DisplayName("counting across New Year needs both years and honours the holiday of the new one")
    void acrossTheYearBoundary() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));
        calendars.maintain(WorkCalendarKey.defaultFor(2027), holiday("2027-01-01"));

        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-12-31"), 1, FST)).isEqualTo(midnight("2027-01-05"));
        assertThat(service.endOfWorkdayAfter(LocalDate.parse("2026-12-30"), 1, FST)).isEqualTo(midnight("2027-01-01"));
    }

    @Test
    @DisplayName("a year nobody maintained fails with an alert instead of assuming plain weekdays")
    void unmaintainedYearFails() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));

        assertThatThrownBy(() -> service.isWorkday(LocalDate.parse("2027-03-01"), FST))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED);
                    assertThat(e.kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
                });
        assertThatThrownBy(() -> service.endOfWorkdayAfter(LocalDate.parse("2026-12-31"), 1, FST))
                .isInstanceOf(DomainException.class).hasMessageContaining("2027");
        assertThat(meters.get("forcitis.organization.attendance.calendar.unmaintained").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("nothing maintained at all fails too, for any date")
    void nothingMaintained() {
        assertThatThrownBy(() -> service.isWorkday(LocalDate.parse("2026-10-09"), FST)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("a legal entity with its own calendar for a year follows it; the others follow the default")
    void legalEntityCalendarOverridesTheDefault() {
        LegalEntityId other = new LegalEntityId(TestIds.uuid(2));
        calendars.maintain(WorkCalendarKey.defaultFor(2026));
        calendars.maintain(new WorkCalendarKey(FST, 2026), holiday("2026-10-09"));

        assertThat(service.isWorkday(LocalDate.parse("2026-10-09"), FST)).isFalse();
        assertThat(service.isWorkday(LocalDate.parse("2026-10-09"), other)).isTrue();
    }

    @Test
    @DisplayName("n must be at least 1")
    void nIsPositive() {
        calendars.maintain(WorkCalendarKey.defaultFor(2026));
        assertThatThrownBy(() -> service.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 0, FST)).isInstanceOf(IllegalArgumentException.class);
    }
}
