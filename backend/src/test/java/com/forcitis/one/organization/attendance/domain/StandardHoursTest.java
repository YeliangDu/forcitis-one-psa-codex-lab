package com.forcitis.one.organization.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.events.StandardHoursVersionPublished;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StandardHoursTest {
    static final LocalDate TODAY = LocalDate.parse("2026-11-02");
    static final UserId BY = new UserId(TestIds.uuid(9));
    static final Instant AT = Instant.parse("2026-11-02T01:00:00Z");
    static final AuditEntryId AUDIT = new AuditEntryId(TestIds.uuid(90));
    static final LegalEntityId ENTITY = new LegalEntityId(TestIds.uuid(5));

    private static StandardHours publish(StandardHoursScopeTail tail, LocalDate from, int workday, List<DateOverride> overrides) {
        return StandardHours.publish(TestIds.uuid(1), ENTITY, tail, TODAY, from, workday, 420, overrides, "Synthetic basis", BY, AT, AUDIT);
    }

    private static void assertRefused(Runnable action, AttendanceError code, ErrorKind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(code);
            assertThat(e.kind()).isEqualTo(kind);
        });
    }

    @Test
    @DisplayName("#1310 no version starts before today, not even the first of a scope or the first after every version was voided")
    void noVersionStartsBeforeToday() {
        assertRefused(() -> publish(StandardHoursScopeTail.empty(), TODAY.minusDays(1), 480, List.of()), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_BACKDATED, ErrorKind.UNPROCESSABLE);
    }

    @Test
    @DisplayName("#1310 the first version of a scope may start today, takes number one, and records one event with its start as the business date")
    void firstVersionStartsTodayAndRecordsOneEvent() {
        StandardHours version = publish(StandardHoursScopeTail.empty(), TODAY, 480, List.of());

        assertThat(version.versionNumber()).isEqualTo(1);
        assertThat(version.status()).isEqualTo(StandardHoursStatus.IN_EFFECT);
        List<DomainEvent> events = version.drainEvents(WorkCalendarYearTest.ENVELOPES);
        assertThat(events).singleElement().isInstanceOfSatisfying(StandardHoursVersionPublished.class, e -> {
            assertThat(e.standardHoursVersionId().value()).isEqualTo(TestIds.uuid(1));
            assertThat(e.effectiveFromDate()).isEqualTo(TODAY);
            assertThat(e.envelope().businessDate()).isEqualTo(TODAY);
            assertThat(e.envelope().aggregateVersion()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("#1310 a later version is numbered after the highest number ever used and must start after the latest version in effect")
    void laterVersionNumberingAndOrdering() {
        StandardHoursScopeTail tail = new StandardHoursScopeTail(3, TODAY.plusDays(10));

        assertThat(publish(tail, TODAY.plusDays(11), 450, List.of()).versionNumber()).isEqualTo(4);
        assertRefused(() -> publish(tail, TODAY.plusDays(10), 450, List.of()), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP, ErrorKind.CONFLICT);
        assertRefused(() -> publish(tail, TODAY.plusDays(9), 450, List.of()), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP, ErrorKind.CONFLICT);
    }

    @Test
    @DisplayName("#1310 a version after the first cannot start before today, but today is accepted")
    void laterVersionIsNotBackdated() {
        StandardHoursScopeTail tail = new StandardHoursScopeTail(1, TODAY.minusDays(30));

        assertThat(publish(tail, TODAY, 450, List.of()).versionNumber()).isEqualTo(2);
        assertRefused(() -> publish(tail, TODAY.minusDays(1), 450, List.of()), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_BACKDATED, ErrorKind.UNPROCESSABLE);
    }

    @Test
    @DisplayName("#1310 minutes outside 1..1440, an override before the start and two overrides on one day are refused as invalid")
    void invalidShapes() {
        StandardHoursScopeTail none = StandardHoursScopeTail.empty();
        DateOverride ok = new DateOverride(TODAY, 240, "Synthetic reason");

        for (int minutes : new int[] {0, 1441}) {
            assertRefused(() -> publish(none, TODAY, minutes, List.of()), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID);
            assertRefused(() -> publish(none, TODAY, 480, List.of(new DateOverride(TODAY, minutes, "Synthetic reason"))),
                    AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID);
        }
        assertRefused(() -> publish(none, TODAY, 480, List.of(new DateOverride(TODAY.minusDays(1), 240, "Synthetic reason"))),
                AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID);
        assertRefused(() -> publish(none, TODAY, 480, List.of(ok, ok)), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID);
        assertRefused(() -> StandardHours.publish(UUID.randomUUID(), ENTITY, none, TODAY, TODAY, 480, 420, List.of(), " padded ", BY, AT, AUDIT),
                AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID);
    }

    @Test
    @DisplayName("#1310 only the latest version in effect that starts after today can be voided, once")
    void voidRules() {
        StandardHours future = publish(new StandardHoursScopeTail(1, TODAY.minusDays(30)), TODAY.plusDays(5), 450, List.of());
        LocalDate latest = TODAY.plusDays(5);

        assertRefused(() -> future.voidVersion(TODAY, TODAY.plusDays(6), "Synthetic reason", BY, AT), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_VOIDABLE, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> future.voidVersion(TODAY.plusDays(5), latest, "Synthetic reason", BY, AT), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_VOIDABLE, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> future.voidVersion(TODAY, latest, " ", BY, AT), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID);
        assertThat(future.status()).as("refusals change nothing").isEqualTo(StandardHoursStatus.IN_EFFECT);

        future.voidVersion(TODAY, latest, "Synthetic reason", BY, AT);

        assertThat(future.status()).isEqualTo(StandardHoursStatus.VOIDED);
        assertThat(future.voidReason()).isEqualTo("Synthetic reason");
        assertRefused(() -> future.voidVersion(TODAY, latest, "Synthetic reason", BY, AT), AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_VOIDABLE, ErrorKind.UNPROCESSABLE);
    }
}
