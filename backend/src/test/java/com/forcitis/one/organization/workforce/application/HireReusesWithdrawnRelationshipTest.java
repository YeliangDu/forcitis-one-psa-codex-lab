package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.GlobalDocumentNumberSource;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.RelationshipRow;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1372 review: a hire whose relationship was left without a live version by a withdrawn hire re-uses it (design 4.2-9) and never makes a second one for the same employer. */
class HireReusesWithdrawnRelationshipTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final UserId USER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa"));
    static final LegalEntityId EMPLOYER = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    static final EmploymentRelationshipId VOID = new EmploymentRelationshipId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
    static final EmploymentRelationshipId OTHER = new EmploymentRelationshipId(UUID.fromString("019a0000-0000-7000-8000-0000000000f2"));
    static final com.forcitis.one.organization.workforce.api.OrgUnitId UNIT = new com.forcitis.one.organization.workforce.api.OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b1"));
    static final com.forcitis.one.organization.workforce.api.PositionId POSITION = new com.forcitis.one.organization.workforce.api.PositionId(UUID.fromString("019a0000-0000-7000-8000-0000000000b3"));
    static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final PersonnelCommandService service;

    HireReusesWithdrawnRelationshipTest() {
        when(ids.next()).thenReturn(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
        when(currentUser.userId()).thenReturn(USER);
        when(commands.lockEmployee(any())).thenReturn(true);
        when(commands.legalEntityIsActive(EMPLOYER)).thenReturn(true);
        when(commands.orgUnitExists(any())).thenReturn(true);
        when(commands.positionExists(any())).thenReturn(true);
        service = new PersonnelCommandService(commands, new NewEmployees(commands, mock(GlobalDocumentNumberSource.class), ids), new PersonnelPlans(ids), mock(SeparationHoldCheck.class),
                mock(UnpaidLeaveStartFloor.class), mock(LegalEntityResolutionSources.class), mock(CountersignBases.class), mock(CountersignRequests.class),
                new OrgCountersignFreeze(mock(com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository.class)),
                mock(AttributionCountersignRepository.class), mock(OwnAttributionGuard.class), mock(OrgMergeProgress.class), mock(EffectPublisher.class), mock(AuditLog.class),
                mock(DomainEvents.class), currentUser, ids, Clock.fixed(NOW, ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")), mock(com.forcitis.one.organization.workforce.domain.ActingDutyRepository.class));
    }

    private void historyWithVoidRelationship(EmploymentRelationshipId predecessor) {
        when(commands.load(EMPLOYEE)).thenReturn(new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                List.of(new RelationshipRow(VOID, EMPLOYER, predecessor, false))));
    }

    private static EnterPersonnelCommand hire(EmploymentRelationshipId predecessor) {
        return new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.HIRE, DAY, null, UNIT, POSITION, null, true, EmploymentPhaseKind.PROBATION, null, null, null, null, null,
                null, EMPLOYER, predecessor, null);
    }

    @Test
    @DisplayName("#1372 评审必改2：撤回后重新入职，同一雇主且前任相同时复用原关系，不再插入第二条")
    void theSamePredecessorReusesTheVoidRelationship() {
        historyWithVoidRelationship(null);
        try {
            service.enter(hire(null));
        } catch (RuntimeException afterTheBinding) {
            assertThat(afterTheBinding.getMessage()).doesNotContain("already has a relationship").doesNotContain("another predecessor");
            // The mocks end the run after the relationship is chosen; the assertion below is about that choice only.
        }
        verify(commands, never()).insertRelationship(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#1372 评审必改2：前任与撤回的那次不同则拒绝，且不插入关系")
    void anotherPredecessorIsRefused() {
        historyWithVoidRelationship(null);
        assertThatThrownBy(() -> service.enter(hire(OTHER))).isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.getMessage()).contains("another predecessor"));
        verify(commands, never()).insertRelationship(any(), any(), any(), any(), any());
    }
}
