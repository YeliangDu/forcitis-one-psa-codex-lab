package com.forcitis.one.organization.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.organization.attendance.api.AttendanceError;
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
        assertThatThrownBy(() -> year.maintain(List.of(day("2027-01-01", DayKind.HOLIDAY)))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR));
        assertThatThrownBy(() -> year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY), day("2026-10-01", DayKind.HOLIDAY))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_DAY_DUPLICATE));
        assertThat(year.days()).isEmpty();
        assertThat(year.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("maintaining records one WorkCalendarYearMaintained with the aggregate version and no legal entity for the default calendar")
    void maintainingRecordsTheEvent() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026), List.of(), 3, 3);
        year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY)));

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
    @DisplayName("the event names exactly the days this save added, removed or changed, in date order, and has no businessDate")
    void eventNamesTheChangedDays() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026),
                List.of(day("2026-10-01", DayKind.HOLIDAY), day("2026-10-02", DayKind.HOLIDAY), day("2026-10-10", DayKind.MAKE_UP_WORKDAY)), 3, 3);

        year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY), day("2026-10-02", DayKind.HOLIDAY),
                day("2026-10-12", DayKind.HOLIDAY), day("2026-05-01", DayKind.HOLIDAY)));

        assertThat(year.drainEvents(ENVELOPES)).singleElement().isInstanceOfSatisfying(WorkCalendarYearMaintained.class, e -> {
            assertThat(e.changedDates()).containsExactly(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-12"));
            assertThat(e.envelope().businessDate()).as("one save can touch many days, so they travel in the payload").isNull();
        });
    }

    @Test
    @DisplayName("saving a year as it already is names no day: it only says the year has been checked")
    void unchangedYearNamesNoDay() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026), List.of(day("2026-10-01", DayKind.HOLIDAY)), 3, 3);

        year.maintain(List.of(day("2026-10-01", DayKind.HOLIDAY)));

        assertThat(year.drainEvents(ENVELOPES)).singleElement().isInstanceOfSatisfying(WorkCalendarYearMaintained.class,
                e -> assertThat(e.changedDates()).isEmpty());
    }

    @Test
    @DisplayName("a day that changes kind is named")
    void changedKindIsNamed() {
        WorkCalendarYear year = WorkCalendarYear.restore(7, WorkCalendarKey.defaultFor(2026), List.of(day("2026-10-10", DayKind.MAKE_UP_WORKDAY)), 3, 3);

        year.maintain(List.of(day("2026-10-10", DayKind.HOLIDAY)));

        assertThat(year.drainEvents(ENVELOPES)).singleElement().isInstanceOfSatisfying(WorkCalendarYearMaintained.class,
                e -> assertThat(e.changedDates()).containsExactly(LocalDate.parse("2026-10-10")));
    }
}
