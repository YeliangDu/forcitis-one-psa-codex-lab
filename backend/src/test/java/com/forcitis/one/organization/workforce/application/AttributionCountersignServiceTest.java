package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AccountRoles;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.ApproveAttributionCountersign;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.CommandEffects;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #895: three lines of defence the acceptance tests cannot see. Who may decide a request (the escalated role is checked, the drafter and the requester are refused
 * before the database check could answer with an integrity error), and that the day of a countersigned command lapses the withdrawal requests still open.
 */
class AttributionCountersignServiceTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final EmployeeId DECIDER_EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000002"));
    static final UserId DECIDER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000d1"));
    static final UserId DRAFTER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa"));
    static final PersonnelCommandId COMMAND = new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final LegalEntityId E1 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    static final LegalEntityId E2 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e2"));
    static final AttributionCountersignId ROW1 = new AttributionCountersignId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
    static final AttributionCountersignId ROW2 = new AttributionCountersignId(UUID.fromString("019a0000-0000-7000-8000-0000000000f2"));

    final AttributionCountersignRepository countersigns = mock(AttributionCountersignRepository.class);
    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final CountersignRequests requests = mock(CountersignRequests.class);
    final AccountRoles roles = mock(AccountRoles.class);
    final OwnAttributionGuard ownGuard = mock(OwnAttributionGuard.class);
    final CountersignBases bases = mock(CountersignBases.class);
    final EffectPublisher effects = mock(EffectPublisher.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final BusinessZone zone = new BusinessZone(ZoneId.of("Asia/Shanghai"));
    final AttributionCountersignService service = new AttributionCountersignService(countersigns, commands, requests, bases,
            new PersonnelPlans(ids), effects, ownGuard, roles, mock(AuditLog.class), currentUser, ids, clock, zone);

    AttributionCountersignServiceTest() {
        when(ids.next()).thenReturn(COMMAND.value());
        when(currentUser.userId()).thenReturn(DECIDER);
        when(currentUser.employeeId()).thenReturn(DECIDER_EMPLOYEE);
        when(commands.lockEmployee(any())).thenReturn(true);
        PersonnelCommand awaiting = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.CHANGE_LEGAL_ENTITY, TODAY.plusDays(5),
                CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN, null, null, null, null, null, E2, E1, E2, DRAFTER, NOW, null, 0, 0);
        when(commands.find(COMMAND)).thenReturn(Optional.of(awaiting));
        AttributionCountersign one = row(ROW1, E1, 2, DRAFTER);
        AttributionCountersign two = row(ROW2, E2, 0, DRAFTER);
        when(countersigns.find(ROW1)).thenReturn(Optional.of(one));
        when(countersigns.ofCommand(COMMAND)).thenReturn(List.of(one, two));
    }

    private static AttributionCountersign row(AttributionCountersignId id, LegalEntityId entity, int level, UserId requestedBy) {
        return AttributionCountersign.restore(id, COMMAND, EMPLOYEE, DRAFTER, CountersignRequestKind.CHANGE, 1, entity, NOW, requestedBy, TODAY.plusDays(3), level, null, 0,
                null, null, null, null);
    }

    @Test
    @DisplayName("#895 note #878 section 6: a request escalated to level 2 may be decided by a business administrator only; another holder of the permission who is not the entity's head is refused")
    void theEscalatedRoleIsChecked() {
        when(countersigns.isFinanceHead(E1, DECIDER_EMPLOYEE, TODAY)).thenReturn(false);
        when(roles.holds(DECIDER, "business_admin")).thenReturn(false);

        assertThatThrownBy(() -> service.approve(new ApproveAttributionCountersign(ROW1, null))).isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.kind()).isEqualTo(ErrorKind.FORBIDDEN));
        verify(requests, never()).approved(any(), any(), anyLong(), any(), any(), any(), any());

        when(roles.holds(DECIDER, "business_admin")).thenReturn(true);
        service.approve(new ApproveAttributionCountersign(ROW1, null));
        verify(requests).approved(any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#895 ADR-0073 item 3.6 (line 71): the drafter and the requester are refused by the application, before the database check could answer with an integrity error")
    void theDrafterAndTheRequesterAreRefusedByTheApplication() {
        when(countersigns.isFinanceHead(any(), any(), any())).thenReturn(true);
        when(currentUser.userId()).thenReturn(DRAFTER);
        assertThatThrownBy(() -> service.approve(new ApproveAttributionCountersign(ROW1, null))).as("the drafter").isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.kind()).isEqualTo(ErrorKind.FORBIDDEN));

        when(currentUser.userId()).thenReturn(DECIDER);
        when(countersigns.find(ROW1)).thenReturn(Optional.of(row(ROW1, E1, 0, DECIDER)));
        assertThatThrownBy(() -> service.approve(new ApproveAttributionCountersign(ROW1, null))).as("the requester").isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.kind()).isEqualTo(ErrorKind.FORBIDDEN));
        verify(requests, never()).approved(any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("#895 user answer Q2: the due-date step lapses the withdrawal requests of a countersigned command that are still open")
    void theDayLapsesOpenWithdrawalRequests() {
        PersonnelCommand pending = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.CHANGE_LEGAL_ENTITY, TODAY, CommandStatus.PENDING,
                null, null, null, null, null, E2, E1, E2, DRAFTER, NOW, null, 0, 0);
        when(commands.find(COMMAND)).thenReturn(Optional.of(pending));
        ApplyDueCommand due = new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), mock(LegalEntityResolutionSources.class), requests,
                countersigns, mock(DomainEvents.class), ids, clock, zone);

        due.execute(COMMAND);

        verify(requests).lapse(any(), any(), any(), any(), any(), any(), any());
        assertThat(pending.status()).isEqualTo(CommandStatus.EFFECTIVE);
    }

    @Test
    @DisplayName("#895 a correction dated after today is effective at the last approval, and its events leave there: no due-date step looks at an effective command")
    void aFutureCorrectionPublishesWhenCountersigned() {
        PersonnelCommand correction = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.CORRECTION, TODAY.plusDays(5),
                CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN, null, null, null, null, null, E2, E1, E2, DRAFTER, NOW, null, 0, 0);
        AttributionCountersign open = row(ROW1, E1, 0, DRAFTER);
        AttributionCountersign approved = AttributionCountersign.restore(ROW2, COMMAND, EMPLOYEE, DRAFTER, CountersignRequestKind.CHANGE, 1, E2, NOW, DRAFTER,
                TODAY.plusDays(3), 0, null, 1, "APPROVED", NOW, DECIDER, null);
        when(commands.find(COMMAND)).thenReturn(Optional.of(correction));
        when(countersigns.find(ROW1)).thenReturn(Optional.of(open));
        when(countersigns.ofCommand(COMMAND)).thenReturn(List.of(open, approved));
        when(countersigns.isFinanceHead(E1, DECIDER_EMPLOYEE, TODAY)).thenReturn(true);
        when(bases.of(any(), any())).thenReturn(Optional.of(new CountersignBases.Basis(E1, E2)));
        VersionRow employed = new VersionRow(UUID.fromString("019a0000-0000-7000-8000-0000000000b1"), TODAY.minusDays(100), null,
                Map.of(CommandEffects.EMPLOYMENT, "EMPLOYED"), ChangeSource.MIGRATION, null, false);
        when(commands.load(EMPLOYEE)).thenReturn(new PersonnelHistory(List.of(employed), List.of(), List.of(), List.of(), Map.of()));

        service.approve(new ApproveAttributionCountersign(ROW1, null));

        assertThat(correction.status()).isEqualTo(CommandStatus.EFFECTIVE);
        verify(effects).publish(any(), any());
    }

    @Test
    @DisplayName("#895 ADR-0073 item 3.5: the employee whose legal entity changed cannot ask to take the change back; the guard that refuses the entry refuses this too")
    void theEmployeeCannotAskToWithdrawTheirOwnChange() {
        PersonnelCommand countersigned = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.CHANGE_LEGAL_ENTITY, TODAY.plusDays(5), CommandStatus.PENDING,
                null, null, null, null, null, E2, E1, E2, DRAFTER, NOW, null, 0, 0);
        when(commands.find(COMMAND)).thenReturn(Optional.of(countersigned));
        org.mockito.Mockito.doThrow(new DomainException(com.forcitis.one.organization.workforce.api.WorkforceError.ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN,
                ErrorKind.FORBIDDEN, "own")).when(ownGuard).requireNotOwn(any(), any());
        PersonnelCommandService personnel = new PersonnelCommandService(commands, mock(NewEmployees.class), new PersonnelPlans(ids), mock(SeparationHoldCheck.class),
                mock(UnpaidLeaveStartFloor.class), mock(LegalEntityResolutionSources.class), bases, requests, countersigns, ownGuard, effects, mock(AuditLog.class),
                mock(DomainEvents.class), currentUser, ids, clock, zone);

        assertThatThrownBy(() -> personnel.withdraw(new com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand(COMMAND, "plan changed")))
                .isInstanceOf(DomainException.class);

        verify(requests, never()).open(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
