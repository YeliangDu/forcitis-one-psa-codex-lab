package com.forcitis.one.organization.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.events.WorkCalendarLockedDaysChanged;
import com.forcitis.one.organization.attendance.events.WorkCalendarYearMaintained;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkCalendarYearTest {
    static final EventEnvelopes ENVELOPES = draft -> new EventEnvelope(UUID.randomUUID(), draft.eventType(), draft.schemaVersion(),
            Instant.EPOCH, draft.aggregateType(), draft.aggregateId(), draft.aggregateVersion(), UUID.randomUUID(), null,
            ActorType.SYSTEM, "test", draft.legalEntityId(), draft.businessDate(), draft.source(), null, draft.relatedAggregates());

    static final LocalDate TODAY = LocalDate.parse("2026-01-01");

    static CalendarDay day(String date, DayKind kind) {
        return new CalendarDay(LocalDate.parse(date), kind);
    }

    @Test
    @DisplayName("a year outside 2000..2100 is refused")
    void yearRange() {
        assertThatThrownBy(() -> WorkCalendarKey.defaultFor(1999)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE));
    }

    @Test
    @DisplayName("a make-up workday must fall on a weekend")
    void makeUpOnAWeekday() {
        assertThatThrownBy(() -> day("2026-10-09", DayKind.MAKE_UP_WORKDAY)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY));
        assertThat(day("2026-10-10", DayKind.MAKE_UP_WORKDAY).dayKind()).isEqualTo(DayKind.MAKE_UP_WORKDAY);
    }

    @Test
    @DisplayName("a day of another year or a day listed twice is refused and leaves the calendar untouched")
    void invalidList() {
        WorkCalendarYear year = WorkCalendarYear.unmaintained(WorkCalendarKey.defaultFor(2026));
        assertThatThrownBy(() -> year.maintain(List.of(day("2027-01-01", DayKind.HOLIDAY)), TODAY, false)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR));
        assertThatThrownBy(() -> year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY), day("2026-10-01", DayKind.HOLIDAY)), TODAY, false))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_DUPLICATE));
        assertThat(year.days()).isEmpty();
        assertThat(year.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("maintaining records one WorkCalendarYearMaintained with the aggregate version and no legal entity for the default calendar")
    void maintainingRecordsTheEvent() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026), List.of(), 3, 3);
        year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY)), TODAY, false);

        List<DomainEvent> events = year.drainEvents(ENVELOPES);

        assertThat(events).singleElement().isInstanceOfSatisfying(WorkCalendarYearMaintained.class, e -> {
            assertThat(e.calendarYear()).isEqualTo(Year.of(2026));
            assertThat(e.envelope().eventType()).isEqualTo("organization.attendance.WorkCalendarYearMaintained");
            assertThat(e.envelope().aggregateId()).isEqualTo("default:2026");
            assertThat(e.envelope().aggregateVersion()).isEqualTo(4);
            assertThat(e.envelope().legalEntityId()).isNull();
        });
    }

    @Test
    @DisplayName("T2 a day before today cannot be added, removed or changed without the confirmation; the refusal leaves the calendar untouched")
    void daysBeforeTodayAreLocked() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026),
                List.of(day("2026-10-03", DayKind.MAKE_UP_WORKDAY), day("2026-10-10", DayKind.MAKE_UP_WORKDAY)), 3, 3);
        LocalDate today = LocalDate.parse("2026-10-14");

        for (List<CalendarDay> attempt : List.of(
                List.of(day("2026-10-10", DayKind.MAKE_UP_WORKDAY)),
                List.of(day("2026-10-03", DayKind.MAKE_UP_WORKDAY), day("2026-10-10", DayKind.MAKE_UP_WORKDAY), day("2026-10-13", DayKind.HOLIDAY)),
                List.of(day("2026-10-03", DayKind.HOLIDAY), day("2026-10-10", DayKind.MAKE_UP_WORKDAY)))) {
            assertThatThrownBy(() -> year.maintain(attempt, today, false)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_LOCKED_DAY_CHANGED));
        }
        assertThat(year.days()).hasSize(2);
        assertThat(year.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("T2 days from today on stay editable, and an unchanged past does not need the confirmation")
    void todayAndLaterAreEditable() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026), List.of(day("2026-10-01", DayKind.HOLIDAY)), 3, 3);
        LocalDate today = LocalDate.parse("2026-10-14");

        year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY), day("2026-10-14", DayKind.HOLIDAY), day("2026-12-25", DayKind.HOLIDAY)), today, false);

        assertThat(year.days()).hasSize(3);
        assertThat(year.drainEvents(ENVELOPES)).singleElement().isInstanceOf(WorkCalendarYearMaintained.class);
    }

    @Test
    @DisplayName("T2 with the confirmation a past day can change, and WorkCalendarLockedDaysChanged names exactly the changed past days")
    void confirmedChangeIsRecorded() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026), List.of(day("2026-10-01", DayKind.HOLIDAY)), 3, 3);

        year.maintain(List.of(day("2026-10-03", DayKind.MAKE_UP_WORKDAY), day("2026-10-20", DayKind.HOLIDAY)), LocalDate.parse("2026-10-14"), true);

        List<DomainEvent> events = year.drainEvents(ENVELOPES);
        assertThat(events).hasSize(2);
        assertThat(events.get(1)).isInstanceOfSatisfying(WorkCalendarLockedDaysChanged.class, e -> {
            assertThat(e.lockedDates()).containsExactly(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"));
            assertThat(e.envelope().aggregateVersion()).isEqualTo(5);
        });
    }
}
