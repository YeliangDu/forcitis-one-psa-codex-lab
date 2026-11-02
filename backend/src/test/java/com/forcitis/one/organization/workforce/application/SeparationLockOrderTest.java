package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.ReleaseSeparationHold;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WithdrawPersonnelCommand;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * IMP-1030-PAR-B6 (ADR-0068 item 2.2): entering a separation, releasing a hold and withdrawing a held command each take the separation lock
 * <em>before</em> the employee row, so that all of them lock in one order and cannot deadlock. The acceptance tests only show that each waits for the
 * lock; this pins the order, which they cannot see.
 */
class SeparationLockOrderTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final UserId USER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa"));
    static final PersonnelCommandId COMMAND = new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final SeparationHoldId HOLD = new SeparationHoldId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));

    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final BusinessZone zone = new BusinessZone(ZoneId.of("Asia/Shanghai"));

    SeparationLockOrderTest() {
        when(ids.next()).thenReturn(COMMAND.value());
        when(currentUser.userId()).thenReturn(USER);
        when(commands.lockEmployee(any())).thenReturn(true);
    }

    private PersonnelCommand heldSeparation() {
        PersonnelCommand separation = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.SEPARATE, LocalDate.of(2026, 11, 2), CommandStatus.HELD,
                null, null, null, null, null, USER, NOW, null, 0, 0);
        when(commands.find(COMMAND)).thenReturn(java.util.Optional.of(separation));
        return separation;
    }

    @Test
    @DisplayName("IMP-1030-PAR-B6 entering a separation takes the separation lock before the employee row")
    void enteringLocksTheDecisionFirst() {
        PersonnelCommandService service = new PersonnelCommandService(commands, mock(NewEmployees.class), new PersonnelPlans(ids), mock(SeparationHoldCheck.class), mock(UnpaidLeaveStartFloor.class), mock(LegalEntityResolutionSources.class),
                mock(AuditLog.class), mock(DomainEvents.class), currentUser, ids, clock, zone);
        when(commands.eventSequence(EMPLOYEE)).thenReturn(0L);
        when(commands.load(EMPLOYEE)).thenReturn(new PersonnelHistory(List.of(), List.of(), List.of(), Map.of()));

        assertThatThrownBy(() -> service.enter(new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.SEPARATE, null, LocalDate.of(2026, 11, 1), null, null, null, null, null, null, null, null, null)))
                .as("refused later, by the rules (the employee has no version); the locks were taken before that").isInstanceOf(DomainException.class);

        InOrder order = inOrder(commands);
        order.verify(commands).lockSeparationDecisions();
        order.verify(commands).lockEmployee(EMPLOYEE);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B6 withdrawing a held separation takes the separation lock before the employee row")
    void withdrawingLocksTheDecisionFirst() {
        PersonnelCommandService service = new PersonnelCommandService(commands, mock(NewEmployees.class), new PersonnelPlans(ids), mock(SeparationHoldCheck.class), mock(UnpaidLeaveStartFloor.class), mock(LegalEntityResolutionSources.class),
                mock(AuditLog.class), mock(DomainEvents.class), currentUser, ids, clock, zone);
        heldSeparation();
        when(commands.load(EMPLOYEE)).thenReturn(new PersonnelHistory(List.of(), List.of(), List.of(), Map.of()));

        service.withdraw(new WithdrawPersonnelCommand(COMMAND, "synthetic: the person stays"));

        InOrder order = inOrder(commands);
        order.verify(commands).lockSeparationDecisions();
        order.verify(commands).lockEmployee(EMPLOYEE);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B6 releasing a hold takes the separation lock before the employee row")
    void releasingLocksTheDecisionFirst() {
        SeparationReview review = mock(SeparationReview.class);
        SeparationHoldService service = new SeparationHoldService(commands, review,
                new HeldSeparationRelease(commands, new PersonnelPlans(ids), mock(LegalEntityResolutionSources.class), mock(AuditLog.class), mock(DomainEvents.class), ids, zone),
                mock(SeparationHoldNotice.class), currentUser, clock);
        heldSeparation();
        SeparationHold hold = SeparationHold.restore(HOLD, COMMAND, EMPLOYEE, HoldStatus.HELD, 0, null, null, null, null);
        when(commands.findHold(HOLD)).thenReturn(java.util.Optional.of(hold));
        when(commands.excludedEmployees()).thenReturn(Set.of(EMPLOYEE));
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(), false, 0, "", "", Set.of()));

        assertThatThrownBy(() -> service.release(new ReleaseSeparationHold(HOLD, "synthetic: confirmed"))).as("refused later: the releaser is not retained")
                .isInstanceOf(DomainException.class);

        InOrder order = inOrder(commands);
        order.verify(commands).lockSeparationDecisions();
        order.verify(commands).lockEmployee(EMPLOYEE);
        verify(commands, never()).updateHold(any(), any(), anyLong());
        verify(commands, never()).update(any(), any(), anyLong());
    }

    @Test
    @DisplayName("#933 the due-date step takes the separation lock before the employee row, whatever the kind of the command")
    void dueStepLocksTheDecisionFirst() {
        ApplyDueCommand due = new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), mock(LegalEntityResolutionSources.class), mock(DomainEvents.class), ids, clock, zone);
        PersonnelCommand pending = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.REGULARIZE, LocalDate.of(2026, 11, 2), CommandStatus.PENDING,
                null, null, null, null, null, USER, NOW, null, 0, 0);
        when(commands.find(COMMAND)).thenReturn(java.util.Optional.of(pending));

        due.execute(COMMAND);

        InOrder order = inOrder(commands);
        order.verify(commands).lockSeparationDecisions();
        order.verify(commands).lockEmployee(EMPLOYEE);
        order.verify(commands).update(any(), any(), anyLong());
    }

    @Test
    @DisplayName("#931 the notice at one remaining leaves out the held only: an administrator released and waiting for the date is still told")
    void theNoticeLeavesOutOnlyTheHeld() {
        SeparationReview review = mock(SeparationReview.class);
        SeparationHoldNotice notice = mock(SeparationHoldNotice.class);
        SeparationHoldService service = new SeparationHoldService(commands, review, mock(HeldSeparationRelease.class), notice, currentUser, clock);
        EmployeeId held = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000002"));
        EmployeeId waiting = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000003"));
        EmployeeId stays = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000004"));
        heldSeparation();
        when(commands.findHold(HOLD)).thenReturn(java.util.Optional.of(SeparationHold.restore(HOLD, COMMAND, EMPLOYEE, HoldStatus.HELD, 0, null, null, null, null)));
        when(commands.heldEmployees()).thenReturn(Set.of(held));
        when(commands.excludedEmployees()).thenReturn(Set.of(held, waiting));
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(EMPLOYEE), true, 1, "", "", Set.of(held, waiting, stays)));

        service.release(new ReleaseSeparationHold(HOLD, "synthetic: confirmed"));

        verify(notice).remainingOne(any(), org.mockito.ArgumentMatchers.eq(Set.of(held, waiting, stays)), org.mockito.ArgumentMatchers.eq(Set.of(held)), org.mockito.ArgumentMatchers.eq(USER));
    }
}
