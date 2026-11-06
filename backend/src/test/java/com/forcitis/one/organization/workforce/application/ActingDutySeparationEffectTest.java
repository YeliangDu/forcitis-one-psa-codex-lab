package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import com.forcitis.one.organization.workforce.domain.ActingDutyRepository;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.organization.workforce.events.EmployeeActingDutyEnded;
import com.forcitis.one.organization.workforce.events.EmployeeActingDutyStarted;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActingDutySeparationEffectTest {
    static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(1));
    static final EmploymentRelationshipId REL = new EmploymentRelationshipId(TestIds.uuid(2));
    static final UserId USER = new UserId(TestIds.uuid(3));
    static final LegalEntityId ENTITY = new LegalEntityId(TestIds.uuid(4));
    static final BusinessZone ZONE = new BusinessZone(ZoneId.of("Asia/Shanghai"));
    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final ActingDutyRepository duties = mock(ActingDutyRepository.class);
    final LegalEntityResolutionSources sources = mock(LegalEntityResolutionSources.class);
    final IdGenerator ids = () -> TestIds.uuid(30);
    final Clock clock = Clock.fixed(NOW, ZONE.zone());
    final List<DomainEvent> published = new ArrayList<>();
    final DomainEvents events = aggregate -> published.addAll(aggregate.drainEvents(TestEnvelopes.INSTANCE));
    final EffectPublisher effects = new EffectPublisher(commands, sources, events, duties, ids, clock);

    PersonnelCommand command(int id, PersonnelCommandKind kind, LocalDate day, CommandStatus status) {
        PersonnelCommand c = PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(id)), EMPLOYEE, kind, day, status,
                kind == PersonnelCommandKind.TRANSFER ? new OrgUnitId(TestIds.uuid(7)) : null,
                null, null, null, null, null, null, null, null, null, USER, NOW, status == CommandStatus.EFFECTIVE ? NOW : null, 0, 7);
        c.bindTo(REL);
        when(commands.find(c.id())).thenReturn(Optional.of(c));
        return c;
    }

    ActingDuty duty(int id, LocalDate start, LocalDate end, LocalDate closed) {
        return new ActingDuty(new ActingDutyId(TestIds.uuid(id)), EMPLOYEE, REL, new OrgUnitId(TestIds.uuid(6)), null, null,
                start, end, closed, "coverage", new PersonnelCommandId(TestIds.uuid(id + 100)), USER, NOW, 2);
    }

    PersonnelHistory history(List<PersonnelCommand> pending) {
        PersonnelHistory history = new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), pending, Map.of());
        when(commands.load(EMPLOYEE)).thenReturn(history);
        when(sources.employeeAttribution(EMPLOYEE, DAY.minusDays(1))).thenReturn(Optional.of(ENTITY));
        return history;
    }

    PersonnelCommand pendingStart(LocalDate day) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.ACTING_START, day, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                new OrgUnitId(TestIds.uuid(6)), null, null, DAY.plusDays(5), null, "coverage");
        PersonnelCommand start = PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(11)), EMPLOYEE, request, USER,
                NOW.minusSeconds(3 * 86400L), DAY.minusDays(3), 7);
        when(commands.find(start.id())).thenReturn(Optional.of(start));
        return start;
    }

    ApplyDueCommand due() {
        return new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), sources,
                mock(CountersignRequests.class), mock(AttributionCountersignRepository.class), mock(OrgMergeProgress.class), events, ids, clock, ZONE, effects);
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "1,true"})
    @DisplayName("#1566 a same-day or overdue start publishes Started then separation Ended exactly once, also when a reinstatement follows the separation")
    void dueStartClosesForEffectiveSeparation(int daysOverdue, boolean reinstatedAfter) {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        PersonnelCommand start = pendingStart(DAY.minusDays(daysOverdue));
        List<PersonnelCommand> all = new ArrayList<>(List.of(separation, start));
        if (reinstatedAfter) {
            all.add(command(12, PersonnelCommandKind.REINSTATE, DAY.plusDays(1), CommandStatus.EFFECTIVE));
        }
        history(all);
        // Repository reads carry the employee's current sequence, including the Started just published.
        when(commands.find(separation.id())).thenAnswer(ignored -> Optional.of(PersonnelCommand.restore(separation.id(), EMPLOYEE,
                PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE, null, null, null, null, null, null, null, null, null, null,
                USER, NOW, NOW, 0, published.isEmpty() ? 7 : published.getLast().envelope().aggregateVersion())));
        doAnswer(ignored -> {
            assertThat(published).singleElement().isInstanceOf(EmployeeActingDutyStarted.class);
            return null;
        }).when(duties).updateEnd(any(), any(), any(), any());

        ApplyDueCommand due = due();
        due.execute(start.id());
        due.execute(start.id());

        ActingDuty written = ActingDuty.start(new ActingDutyId(TestIds.uuid(30)), start, NOW);
        var order = inOrder(duties);
        order.verify(duties).insert(written);
        order.verify(duties).updateEnd(written, DAY, ActingDutyEndCause.SEPARATION, NOW);
        verify(duties, times(1)).insert(any());
        verify(duties, times(1)).updateEnd(any(), any(), any(), any());
        assertThat(start.status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(published).hasSize(2);
        assertThat(published.getFirst()).isInstanceOfSatisfying(EmployeeActingDutyStarted.class, event -> {
            assertThat(event.personnelCommandId()).isEqualTo(start.id());
            assertThat(event.actingDutyId()).isEqualTo(written.id());
            assertThat(event.envelope().aggregateVersion()).isEqualTo(8);
        });
        assertThat(published.getLast()).isInstanceOfSatisfying(EmployeeActingDutyEnded.class, event -> {
            assertThat(event.personnelCommandId()).isEqualTo(separation.id());
            assertThat(event.actingDutyId()).isEqualTo(written.id());
            assertThat(event.endedOn()).isEqualTo(DAY);
            assertThat(event.endCause()).isEqualTo(ActingDutyEndCause.SEPARATION);
            assertThat(event.envelope().businessDate()).isEqualTo(DAY);
            assertThat(event.envelope().legalEntityId()).isEqualTo(ENTITY);
            assertThat(event.envelope().aggregateVersion()).isEqualTo(9);
        });
        var sequenceOrder = inOrder(commands);
        sequenceOrder.verify(commands).saveEventSequence(EMPLOYEE, 8);
        sequenceOrder.verify(commands).saveEventSequence(EMPLOYEE, 9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "pending", "held", "withdrawn", "afterTerm"})
    @DisplayName("#1566 a due start stays open without an effective separation inside its term")
    void dueStartStaysOpen(String scenario) {
        PersonnelCommand start = pendingStart(DAY.minusDays(2));
        List<PersonnelCommand> all = new ArrayList<>(List.of(start));
        if (!scenario.equals("none")) {
            CommandStatus status = switch (scenario) {
                case "pending" -> CommandStatus.PENDING;
                case "held" -> CommandStatus.HELD;
                case "withdrawn" -> CommandStatus.WITHDRAWN;
                default -> CommandStatus.EFFECTIVE;
            };
            all.add(command(10, PersonnelCommandKind.SEPARATE, scenario.equals("afterTerm") ? DAY.plusDays(5) : DAY.minusDays(1), status));
        }
        history(all);

        due().execute(start.id());

        assertThat(start.status()).isEqualTo(CommandStatus.EFFECTIVE);
        verify(duties).insert(any());
        verify(duties, never()).updateEnd(any(), any(), any(), any());
        assertThat(published).singleElement().isInstanceOf(EmployeeActingDutyStarted.class);
    }

    @Test
    @DisplayName("#1566 entry path: a backdated start across a reinstated separation is closed at the separation day")
    void backdatedEntryAcrossReinstatementIsClosed() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY.minusDays(1), CommandStatus.EFFECTIVE);
        PersonnelCommand reinstatement = command(12, PersonnelCommandKind.REINSTATE, DAY, CommandStatus.EFFECTIVE);
        PersonnelCommand start = pendingStart(DAY.minusDays(3));
        PersonnelHistory history = history(List.of(separation, reinstatement, start));

        effects.publish(start, history, DAY);

        verify(duties).updateEnd(any(), eq(DAY.minusDays(1)), eq(ActingDutyEndCause.SEPARATION), any());
        assertThat(published).hasSize(2);
        assertThat(published.getLast()).isInstanceOfSatisfying(EmployeeActingDutyEnded.class,
                event -> assertThat(event.personnelCommandId()).isEqualTo(separation.id()));
    }

    @Test
    @DisplayName("#1566 separation closes all unexpired open duties, including a start today, and withdraws only later pending starts")
    void immediateSeparationEndsDutiesAndWithdrawsLaterStarts() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        PersonnelCommand future = command(11, PersonnelCommandKind.ACTING_START, DAY.plusDays(2), CommandStatus.PENDING);
        PersonnelCommand sameDay = command(12, PersonnelCommandKind.ACTING_START, DAY, CommandStatus.PENDING);
        PersonnelCommand pendingEnd = command(13, PersonnelCommandKind.ACTING_END, DAY.plusDays(3), CommandStatus.PENDING);
        ActingDuty earlier = duty(20, DAY.minusDays(2), DAY.plusDays(4), null);
        ActingDuty today = duty(21, DAY, DAY.plusDays(5), null);
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(earlier, today));

        effects.publish(separation, history(List.of(future, sameDay, pendingEnd)), DAY);

        verify(duties).updateEnd(earlier, DAY, ActingDutyEndCause.SEPARATION, NOW);
        verify(duties).updateEnd(today, DAY, ActingDutyEndCause.SEPARATION, NOW);
        assertThat(future.status()).isEqualTo(CommandStatus.WITHDRAWN);
        assertThat(future.appliedAt()).isNull();
        assertThat(future.withdrawnBy()).isEqualTo(USER);
        assertThat(sameDay.status()).isEqualTo(CommandStatus.PENDING);
        assertThat(pendingEnd.status()).isEqualTo(CommandStatus.PENDING);
        verify(commands).update(future, NOW, 0);
        List<EmployeeActingDutyEnded> ended = published.stream().filter(EmployeeActingDutyEnded.class::isInstance).map(EmployeeActingDutyEnded.class::cast).toList();
        assertThat(ended).hasSize(2).allSatisfy(event -> {
            assertThat(event.personnelCommandId()).isEqualTo(separation.id());
            assertThat(event.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(event.endedOn()).isEqualTo(DAY);
            assertThat(event.endCause()).isEqualTo(ActingDutyEndCause.SEPARATION);
            assertThat(event.employmentRelationshipId()).isEqualTo(REL);
            assertThat(event.envelope().businessDate()).isEqualTo(DAY);
            assertThat(event.envelope().legalEntityId()).isEqualTo(ENTITY);
        });
        assertThat(published).extracting(e -> e.envelope().aggregateVersion()).containsExactly(8L, 9L, 10L, 11L);
        verify(commands).saveEventSequence(EMPLOYEE, 11);
    }

    @Test
    @DisplayName("#1566 rule 6 backdated separation closes a later-starting open duty on its start day")
    void backdatedSeparationEndsLaterStartingDutyOnStartDay() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        ActingDuty later = duty(20, DAY.plusDays(2), DAY.plusDays(5), null);
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(later));

        effects.publish(separation, history(List.of()), DAY.plusDays(3));

        verify(duties).updateEnd(later, DAY.plusDays(2), ActingDutyEndCause.SEPARATION, NOW);
        List<EmployeeActingDutyEnded> ended = published.stream().filter(EmployeeActingDutyEnded.class::isInstance).map(EmployeeActingDutyEnded.class::cast).toList();
        assertThat(ended).singleElement().satisfies(event -> {
            assertThat(event.actingDutyId()).isEqualTo(later.id());
            assertThat(event.personnelCommandId()).isEqualTo(separation.id());
            assertThat(event.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(event.endedOn()).isEqualTo(DAY.plusDays(2));
            assertThat(event.endCause()).isEqualTo(ActingDutyEndCause.SEPARATION);
            assertThat(event.employmentRelationshipId()).isEqualTo(REL);
            assertThat(event.envelope().businessDate()).isEqualTo(DAY.plusDays(2));
            assertThat(event.envelope().legalEntityId()).isEqualTo(ENTITY);
        });
    }

    @Test
    @DisplayName("#1566 rule 6 separation ends mixed duty dates once and leaves already closed duties unchanged")
    void separationEndsMixedDutyDatesOnceAndSkipsClosedDuties() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        ActingDuty later = duty(20, DAY.plusDays(2), DAY.plusDays(5), null);
        ActingDuty earlier = duty(21, DAY.minusDays(2), DAY.plusDays(4), null);
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(later, earlier));
        PersonnelHistory history = history(List.of());

        effects.publish(separation, history, DAY.plusDays(3));

        List<EmployeeActingDutyEnded> ended = published.stream().filter(EmployeeActingDutyEnded.class::isInstance).map(EmployeeActingDutyEnded.class::cast).toList();
        assertThat(ended).extracting(EmployeeActingDutyEnded::actingDutyId).containsExactly(later.id(), earlier.id());
        assertThat(ended).extracting(EmployeeActingDutyEnded::endedOn).containsExactly(DAY.plusDays(2), DAY);
        assertThat(ended).allSatisfy(event -> {
            assertThat(event.personnelCommandId()).isEqualTo(separation.id());
            assertThat(event.endCause()).isEqualTo(ActingDutyEndCause.SEPARATION);
            assertThat(event.envelope().businessDate()).isEqualTo(event.endedOn());
        });

        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(
                duty(20, DAY.plusDays(2), DAY.plusDays(5), DAY.plusDays(2)),
                duty(21, DAY.minusDays(2), DAY.plusDays(4), DAY)));
        effects.publish(separation, history, DAY.plusDays(3));

        verify(duties).updateEnd(later, DAY.plusDays(2), ActingDutyEndCause.SEPARATION, NOW);
        verify(duties).updateEnd(earlier, DAY, ActingDutyEndCause.SEPARATION, NOW);
        assertThat(published.stream().filter(EmployeeActingDutyEnded.class::isInstance).toList()).containsExactlyElementsOf(ended);
        verify(duties, never()).updateEnd(
                duty(20, DAY.plusDays(2), DAY.plusDays(5), DAY.plusDays(2)), DAY.plusDays(2), ActingDutyEndCause.SEPARATION, NOW);
        verify(duties, never()).updateEnd(
                duty(21, DAY.minusDays(2), DAY.plusDays(4), DAY), DAY, ActingDutyEndCause.SEPARATION, NOW);
    }

    @Test
    @DisplayName("#1566 duties expiring by separation day are left to expiry, and covered people's duties are never selected")
    void leavesExpiredDutiesToExpiry() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(duty(20, DAY.minusDays(3), DAY, null), duty(21, DAY.minusDays(4), DAY.minusDays(1), null)));
        effects.publish(separation, history(List.of()), DAY);
        assertThat(published).noneMatch(EmployeeActingDutyEnded.class::isInstance);
        verify(duties, never()).updateEnd(any(), any(), any(), any());
    }

    @Test
    @DisplayName("#1566 a failed separation duty write propagates so the enclosing transaction rolls back")
    void closureFailurePropagates() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        ActingDuty duty = duty(20, DAY.minusDays(2), DAY.plusDays(4), null);
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(duty));
        RuntimeException failure = new IllegalStateException("duty write failed");
        doThrow(failure).when(duties).updateEnd(any(), any(), any(), any());
        PersonnelHistory history = history(List.of());
        assertThatThrownBy(() -> effects.publish(separation, history, DAY)).isSameAs(failure);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("#1566 due separation closes duties under the employee lock and replay publishes no second end")
    void dueSeparationClosesOnceUnderEmployeeLock() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.PENDING);
        ActingDuty duty = duty(20, DAY.minusDays(2), DAY.plusDays(4), null);
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(duty));
        history(List.of());
        ApplyDueCommand due = new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), sources,
                mock(CountersignRequests.class), mock(AttributionCountersignRepository.class), mock(OrgMergeProgress.class), events, ids, clock, ZONE, effects);
        due.execute(separation.id());
        due.execute(separation.id());
        assertThat(published.stream().filter(EmployeeActingDutyEnded.class::isInstance)).hasSize(1);
        var order = inOrder(commands, duties);
        order.verify(commands).lockSeparationDecisions();
        order.verify(commands).lockEmployee(EMPLOYEE);
        order.verify(duties).listOpen(EMPLOYEE);
        order.verify(duties).updateEnd(duty, DAY, ActingDutyEndCause.SEPARATION, NOW);
    }

    @Test
    @DisplayName("#1566 a pending early end overtaken by separation is withdrawn without a second Ended")
    void separationOvertakesPendingEnd() {
        PersonnelCommand separation = command(10, PersonnelCommandKind.SEPARATE, DAY, CommandStatus.EFFECTIVE);
        ActingDuty duty = duty(20, DAY.minusDays(2), DAY.plusDays(4), null);
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.ACTING_END, DAY.plusDays(2), null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                null, null, null, null, duty.id(), "end coverage");
        PersonnelCommand end = PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(11)), EMPLOYEE, request, USER, NOW, DAY, 7);
        when(commands.find(end.id())).thenReturn(Optional.of(end));
        when(duties.listOpen(EMPLOYEE)).thenReturn(List.of(duty));
        effects.publish(separation, history(List.of(end)), DAY);
        when(duties.find(duty.id())).thenReturn(Optional.of(duty(20, DAY.minusDays(2), DAY.plusDays(4), DAY)));
        Clock later = Clock.fixed(NOW.plusSeconds(2 * 86400L), ZONE.zone());
        ApplyDueCommand due = new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), sources,
                mock(CountersignRequests.class), mock(AttributionCountersignRepository.class), mock(OrgMergeProgress.class), events, ids, later, ZONE, effects);
        due.execute(end.id());
        due.execute(end.id());
        assertThat(end.status()).isEqualTo(CommandStatus.WITHDRAWN);
        assertThat(published.stream().filter(EmployeeActingDutyEnded.class::isInstance)).hasSize(1);
    }

    @Test
    @DisplayName("#1566 transfers leave the employee's acting duties and pending starts unchanged")
    void transferDoesNotEndDuties() {
        PersonnelCommand transfer = command(10, PersonnelCommandKind.TRANSFER, DAY, CommandStatus.EFFECTIVE);
        PersonnelCommand start = command(11, PersonnelCommandKind.ACTING_START, DAY.plusDays(2), CommandStatus.PENDING);
        effects.publish(transfer, history(List.of(start)), DAY);
        assertThat(start.status()).isEqualTo(CommandStatus.PENDING);
        assertThat(published).noneMatch(EmployeeActingDutyEnded.class::isInstance);
        verify(duties, never()).listOpen(EMPLOYEE);
        verify(duties, never()).updateEnd(any(), any(), any(), any());
    }
}
