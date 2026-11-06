package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.ActingDutyRow;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("#1579 acting reads share employee visibility and use business-day terms")
class ActingDutyReadTest {
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(1));
    static final EmployeeId COVERED = new EmployeeId(TestIds.uuid(2));
    static final LocalDate FIRST = LocalDate.of(2026, 11, 6);
    static final EmployeeRosterQueries.Visible ALL = new EmployeeRosterQueries.Visible(true, Set.of());
    static final BusinessZone ZONE = new BusinessZone(ZoneId.of("Asia/Shanghai"));
    final EmployeeRosterQueries queries = mock(EmployeeRosterQueries.class);
    final OrgUnitAccess access = mock(OrgUnitAccess.class);
    final AuditLog audit = mock(AuditLog.class);
    final EmployeeVisibility visibility = new EmployeeVisibility(access, mock(OrgSnapshotQueries.class));
    final GetEmployee employee = new GetEmployee(queries, visibility, audit, mock(CurrentUser.class));
    final Clock clock = Clock.fixed(Instant.parse("2026-11-06T16:00:00Z"), ZoneId.of("UTC"));
    final ActingDutyViews views = new ActingDutyViews(queries, visibility, clock, ZONE);
    final GetActingDuties duties = new GetActingDuties(employee, queries, views);
    final GetCoveredActingDuties covering = new GetCoveredActingDuties(employee, queries, views);

    ActingDutyReadTest() {
        when(access.visibleOrgUnits("organization.workforce:employee:read")).thenReturn(OrgUnitAccess.Visible.all());
    }

    @Test
    void openTermUsesInclusiveLastDayAndPreservesTheDuty() {
        var row = duty(null, null);
        var view = ActingDutyView.of(row, "Covered person", FIRST);
        assertThat(view.actingDutyId()).isEqualTo(row.actingDutyId());
        assertThat(view.employeeId()).isEqualTo(EMPLOYEE);
        assertThat(view.coveredOrgUnitId()).isEqualTo(row.coveredOrgUnitId());
        assertThat(view.coveredPositionId()).isEqualTo(row.coveredPositionId());
        assertThat(view.coveredEmployeeId()).isEqualTo(COVERED);
        assertThat(view.coveredEmployeeName()).isEqualTo("Covered person");
        assertThat(view.firstDay()).isEqualTo(FIRST);
        assertThat(view.lastDay()).isEqualTo(LocalDate.of(2026, 11, 19));
        assertThat(view.state()).isEqualTo(ActingDutyView.State.ACTIVE);
        assertThat(view.endCause()).isNull();
        assertThat(view.reason()).isEqualTo("temporary coverage");
    }

    @ParameterizedTest
    @EnumSource(ActingDutyEndCause.class)
    void closedTermUsesEndedOnAndKeepsItsCause(ActingDutyEndCause cause) {
        var view = ActingDutyView.of(duty(FIRST.plusDays(2), cause), null, FIRST);
        assertThat(view.lastDay()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(view.state()).isEqualTo(ActingDutyView.State.ENDED);
        assertThat(view.endCause()).isEqualTo(cause);
    }

    @Test
    void separationOnStartDayHasNoLastDay() {
        var view = ActingDutyView.of(duty(FIRST, ActingDutyEndCause.SEPARATION), null, FIRST);
        assertThat(view.lastDay()).isNull();
        assertThat(view.state()).isEqualTo(ActingDutyView.State.ENDED);
        assertThat(view.endCause()).isEqualTo(ActingDutyEndCause.SEPARATION);
    }

    @Test
    void expiryChangesStateAtTheBusinessDayBoundaryBeforeTheJobClosesIt() {
        var row = new ActingDutyRow(new ActingDutyId(TestIds.uuid(3)), EMPLOYEE, new OrgUnitId(TestIds.uuid(4)), null, null,
                FIRST, FIRST.plusDays(1), null, null, "one-day coverage");
        assertThat(ActingDutyView.of(row, null, FIRST).state()).isEqualTo(ActingDutyView.State.ACTIVE);
        when(queries.find(ALL, EMPLOYEE)).thenReturn(Optional.of(person(EMPLOYEE)));
        when(queries.actingDuties(EMPLOYEE)).thenReturn(List.of(row));
        var result = duties.execute(EMPLOYEE).orElseThrow().getFirst();
        assertThat(result.state()).isEqualTo(ActingDutyView.State.ENDED);
        assertThat(result.firstDay()).isEqualTo(result.lastDay());
        assertThat(result.endCause()).isNull();
    }

    @Test
    void unknownSubjectsHaveNoHistoryOnEitherRead() {
        assertThat(duties.execute(EMPLOYEE)).isEmpty();
        assertThat(covering.execute(EMPLOYEE)).isEmpty();
        verify(queries, never()).actingDuties(any());
        verify(queries, never()).coveredActingDuties(any());
        verifyNoInteractions(audit);
    }

    @Test
    void outOfScopeSubjectsHaveNoHistoryAndKeepTheEmployeeDeniedAudit() {
        when(access.visibleOrgUnits("organization.workforce:employee:read")).thenReturn(OrgUnitAccess.Visible.nothing());
        when(queries.exists(EMPLOYEE)).thenReturn(true);
        assertThat(duties.execute(EMPLOYEE)).isEmpty();
        assertThat(covering.execute(EMPLOYEE)).isEmpty();
        verify(audit, times(2)).recordIndependently(argThat(entry -> entry.action().equals("organization.workforce.employee.denied")));
        verify(queries, never()).actingDuties(any());
        verify(queries, never()).coveredActingDuties(any());
        verify(queries, times(2)).find(new EmployeeRosterQueries.Visible(false, Set.of()), EMPLOYEE);
    }

    @Test
    void invisibleCoveredPersonKeepsTheDutyAndIdButNotTheName() {
        when(queries.find(ALL, EMPLOYEE)).thenReturn(Optional.of(person(EMPLOYEE)));
        when(queries.actingDuties(EMPLOYEE)).thenReturn(List.of(duty(null, null)));
        var view = duties.execute(EMPLOYEE).orElseThrow().getFirst();
        assertThat(view.coveredEmployeeId()).isEqualTo(COVERED);
        assertThat(view.coveredEmployeeName()).isNull();
        verifyNoInteractions(audit);
    }

    @Test
    void bothReadsKeepVisibleCoveredNamesAndTheQueryOrder() {
        when(queries.find(ALL, EMPLOYEE)).thenReturn(Optional.of(person(EMPLOYEE)));
        when(queries.find(ALL, COVERED)).thenReturn(Optional.of(person(COVERED)));
        var rows = List.of(duty(null, null), duty(FIRST, ActingDutyEndCause.SEPARATION));
        when(queries.actingDuties(EMPLOYEE)).thenReturn(rows);
        when(queries.coveredActingDuties(COVERED)).thenReturn(rows);
        var actual = duties.execute(EMPLOYEE).orElseThrow();
        assertThat(actual).extracting(ActingDutyView::coveredEmployeeName).containsExactly("Visible person", "Visible person");
        assertThat(actual).extracting(ActingDutyView::lastDay).containsExactly(LocalDate.of(2026, 11, 19), null);
        assertThat(covering.execute(COVERED)).contains(actual);
    }

    static EmployeeRosterQueries.Row person(EmployeeId id) {
        return new EmployeeRosterQueries.Row(id, "SYN", "Visible person", EmploymentStatus.EMPLOYED, FIRST, false, List.of());
    }

    static ActingDutyRow duty(LocalDate endedOn, ActingDutyEndCause cause) {
        return new ActingDutyRow(new ActingDutyId(TestIds.uuid(3)), EMPLOYEE, new OrgUnitId(TestIds.uuid(4)), new PositionId(TestIds.uuid(5)),
                COVERED, FIRST, LocalDate.of(2026, 11, 20), endedOn, cause, "temporary coverage");
    }
}
