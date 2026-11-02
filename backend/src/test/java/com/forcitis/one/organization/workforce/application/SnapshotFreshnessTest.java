package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.organization.workforce.domain.SyncRunQueries;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestAccounts;

class SnapshotFreshnessTest {
    static final Instant NOW = Instant.parse("2026-10-15T08:00:00Z");
    static final String EVERY_FIVE_MINUTES = "0 */5 * * * *";

    final SyncRunQueries runs = mock(SyncRunQueries.class);

    private SnapshotFreshness freshness(Instant now, Duration staleAfter) {
        return freshness(now, staleAfter, EVERY_FIVE_MINUTES);
    }

    private SnapshotFreshness freshness(Instant now, Duration staleAfter, String cron) {
        return new SnapshotFreshness(runs, Clock.fixed(now, ZoneOffset.UTC), staleAfter, cron);
    }

    @Test
    @DisplayName("P2-M6 the snapshot is current up to stale-after after the last successful run, and stale one second later; never synced is not current")
    void P2_M6_snapshotIsCurrentUpToStaleAfter() {
        when(runs.findLastSucceededAt()).thenReturn(Optional.of(NOW.minus(Duration.ofMinutes(10))));

        assertThat(freshness(NOW, Duration.ofMinutes(10)).isCurrent()).isTrue();
        assertThat(freshness(NOW.plusSeconds(1), Duration.ofMinutes(10)).isCurrent()).isFalse();

        when(runs.findLastSucceededAt()).thenReturn(Optional.empty());
        assertThat(freshness(NOW, Duration.ofMinutes(10)).isCurrent()).isFalse();
    }

    @Test
    @DisplayName("P2-M6 stale-after must leave room for two sync runs and stay within an hour, or the application does not start")
    void P2_M6_staleAfterIsBounded() {
        assertThatThrownBy(() -> freshness(NOW, Duration.ofMinutes(9))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("two sync runs");
        assertThatThrownBy(() -> freshness(NOW, Duration.ofMinutes(61))).isInstanceOf(IllegalArgumentException.class);
        assertThat(freshness(NOW, Duration.ofMinutes(10))).isNotNull();
        assertThat(freshness(NOW, Duration.ofHours(1))).isNotNull();
    }

    @Test
    @DisplayName("L-8 the cycle of a cron is its longest gap within a week: even, uneven and too rare crons")
    void L8_cycleIsTheLongestGap() {
        assertThat(SnapshotFreshness.cycleOf(EVERY_FIVE_MINUTES)).isEqualTo(Duration.ofMinutes(5));
        assertThat(SnapshotFreshness.cycleOf("0 0 8,20 * * *")).as("08:00 and 20:00: the night gap is 12 hours, the day gap 12 hours").isEqualTo(Duration.ofHours(12));
        assertThat(SnapshotFreshness.cycleOf("0 0 8,10 * * *")).as("the gap from 10:00 to 08:00 the next day is 22 hours, not the first gap of 2 hours").isEqualTo(Duration.ofHours(22));
        assertThat(SnapshotFreshness.cycleOf("0 0 8 * * MON,THU")).as("Monday to Thursday is 3 days, Thursday to Monday 4").isEqualTo(Duration.ofDays(4));
        assertThatThrownBy(() -> SnapshotFreshness.cycleOf("0 0 8 1 * *")).as("monthly").isInstanceOf(IllegalArgumentException.class).hasMessageContaining("less than twice");
        assertThatThrownBy(() -> freshness(NOW, Duration.ofMinutes(10), "0 0 8 1 * *")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P2-H2 employmentOf: listed as separated is said even by a stale snapshot; not listed only by a current one; otherwise the snapshot is unavailable")
    void P2_H2_employmentVerdict() {
        OrgSnapshotQueries snapshots = mock(OrgSnapshotQueries.class);
        StaffEmployee employed = staff(EmploymentStatus.EMPLOYED);
        StaffEmployee separated = staff(EmploymentStatus.SEPARATED);
        when(snapshots.findStaff(TestAccounts.employee(1))).thenReturn(Optional.of(employed));
        when(snapshots.findStaff(TestAccounts.employee(2))).thenReturn(Optional.of(separated));
        when(snapshots.findStaff(TestAccounts.employee(3))).thenReturn(Optional.empty());
        OrgDirectoryService current = directory(snapshots, NOW.minus(Duration.ofMinutes(1)));
        OrgDirectoryService stale = directory(snapshots, NOW.minus(Duration.ofDays(30)));

        assertThat(current.employmentOf(TestAccounts.employee(1))).isEqualTo(EmploymentVerdict.EMPLOYED);
        assertThat(current.employmentOf(TestAccounts.employee(2))).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(current.employmentOf(TestAccounts.employee(3))).isEqualTo(EmploymentVerdict.NOT_LISTED);
        assertThat(stale.employmentOf(TestAccounts.employee(1))).isEqualTo(EmploymentVerdict.SNAPSHOT_UNAVAILABLE);
        assertThat(stale.employmentOf(TestAccounts.employee(2))).isEqualTo(EmploymentVerdict.SEPARATED);
        assertThat(stale.employmentOf(TestAccounts.employee(3))).isEqualTo(EmploymentVerdict.SNAPSHOT_UNAVAILABLE);
    }

    private OrgDirectoryService directory(OrgSnapshotQueries snapshots, Instant lastSucceeded) {
        SyncRunQueries history = mock(SyncRunQueries.class);
        when(history.findLastSucceededAt()).thenReturn(Optional.of(lastSucceeded));
        return new OrgDirectoryService(snapshots, mock(RegisterEmployeeOnLogin.class), mock(RegisterEmployeeOnLoginName.class),
                new SnapshotFreshness(history, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10), EVERY_FIVE_MINUTES),
                mock(com.forcitis.one.organization.workforce.domain.EmployeeVersionQueries.class),
                mock(com.forcitis.one.organization.workforce.domain.PersonnelCommandQueries.class), Clock.fixed(NOW, ZoneOffset.UTC),
                new com.forcitis.one.kernel.BusinessZone(java.time.ZoneId.of("Asia/Shanghai")));
    }

    private static StaffEmployee staff(EmploymentStatus status) {
        Employee employee = mock(Employee.class);
        when(employee.employmentStatus()).thenReturn(status);
        StaffEmployee staff = mock(StaffEmployee.class);
        when(staff.employee()).thenReturn(employee);
        return staff;
    }
}
