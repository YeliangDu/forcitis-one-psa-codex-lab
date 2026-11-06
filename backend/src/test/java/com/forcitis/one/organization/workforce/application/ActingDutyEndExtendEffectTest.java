package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
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
import com.forcitis.one.organization.workforce.api.WorkforceError;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;

class ActingDutyEndExtendEffectTest {
    static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    static final BusinessZone ZONE = new BusinessZone(ZoneId.of("Asia/Shanghai"));
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(1));
    static final EmploymentRelationshipId REL = new EmploymentRelationshipId(TestIds.uuid(2));
    static final ActingDutyId DUTY = new ActingDutyId(TestIds.uuid(3));
    static final UserId RECORDER = new UserId(TestIds.uuid(4));
    static final OrgUnitId COVERED = new OrgUnitId(TestIds.uuid(5));
    static final LegalEntityId ENTITY = new LegalEntityId(TestIds.uuid(6));
    static final PersonnelCommandId START = new PersonnelCommandId(TestIds.uuid(7));
    static final PersonnelHistory HISTORY = new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    final Clock clock = Clock.fixed(NOW, ZONE.zone());
    final IdGenerator ids = () -> TestIds.uuid(30);
    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final ActingDutyRepository duties = mock(ActingDutyRepository.class);
    final LegalEntityResolutionSources sources = mock(LegalEntityResolutionSources.class);
    final List<DomainEvent> published = new ArrayList<>();
    final DomainEvents events = aggregate -> published.addAll(aggregate.drainEvents(TestEnvelopes.INSTANCE));
    final EffectPublisher effects = new EffectPublisher(commands, sources, events, duties, ids, clock);

    ActingDuty duty(LocalDate end, LocalDate closed) {
        return new ActingDuty(DUTY, EMPLOYEE, REL, COVERED, null, null, DAY.minusDays(3), end, closed,
                "coverage", START, RECORDER, NOW.minusSeconds(3 * 86400L), 2);
    }

    PersonnelCommand change(PersonnelCommandKind kind, LocalDate day, LocalDate end) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, kind, day, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                null, null, null, end, DUTY, "adjust cover");
        PersonnelCommand command = PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(8)), EMPLOYEE, request, RECORDER, NOW, DAY, 7);
        when(commands.find(command.id())).thenReturn(Optional.of(command));
        when(commands.load(EMPLOYEE)).thenReturn(HISTORY);
        when(sources.employeeAttribution(EMPLOYEE, day)).thenReturn(Optional.of(ENTITY));
        return command;
    }

    ApplyDueCommand due(Clock at) {
        return new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), sources, mock(CountersignRequests.class),
                mock(AttributionCountersignRepository.class), mock(OrgMergeProgress.class), events, ids, at, ZONE,
                new EffectPublisher(commands, sources, events, duties, ids, at));
    }

    @Test
    @DisplayName("#1566 early end stores the first non-holding day and publishes one employee-stream event")
    void immediateEndPublishesEnded() {
        ActingDuty target = duty(DAY.plusDays(5), null);
        PersonnelCommand command = change(PersonnelCommandKind.ACTING_END, DAY, null);
        effects.changeActingDuty(command, target);
        verify(duties).updateEnd(target, DAY, ActingDutyEndCause.ENDED_EARLY, NOW);
        assertThat(published).singleElement().isInstanceOfSatisfying(EmployeeActingDutyEnded.class, event -> {
            assertThat(event.personnelCommandId()).isEqualTo(command.id());
            assertThat(event.actingDutyId()).isEqualTo(DUTY);
            assertThat(event.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(event.employmentRelationshipId()).isEqualTo(REL);
            assertThat(event.coveredOrgUnitId()).isEqualTo(COVERED);
            assertThat(event.coveredPositionId()).isNull();
            assertThat(event.coveredEmployeeId()).isNull();
            assertThat(event.endedOn()).isEqualTo(DAY);
            assertThat(event.endCause()).isEqualTo(ActingDutyEndCause.ENDED_EARLY);
            assertThat(event.envelope().businessDate()).isEqualTo(DAY);
            assertThat(event.envelope().aggregateId()).isEqualTo(EMPLOYEE.value());
            assertThat(event.envelope().aggregateVersion()).isEqualTo(8);
            assertThat(event.envelope().legalEntityId()).isEqualTo(ENTITY);
        });
        verify(commands).saveEventSequence(EMPLOYEE, 8);
    }

    @Test
    @DisplayName("#1566 a pending early end waits for its day and replay publishes nothing")
    void dueEndIsAppliedOnce() {
        LocalDate day = DAY.plusDays(2);
        PersonnelCommand command = change(PersonnelCommandKind.ACTING_END, day, null);
        when(duties.find(DUTY)).thenReturn(Optional.of(duty(DAY.plusDays(5), null)));
        due(clock).execute(command.id());
        assertThat(command.status()).isEqualTo(CommandStatus.PENDING);
        assertThat(published).isEmpty();
        ApplyDueCommand due = due(Clock.fixed(NOW.plusSeconds(2 * 86400L), ZONE.zone()));
        due.execute(command.id());
        due.execute(command.id());
        assertThat(command.status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(published).singleElement().isInstanceOfSatisfying(EmployeeActingDutyEnded.class,
                event -> assertThat(event.endedOn()).isEqualTo(day));
        verify(duties, times(1)).updateEnd(any(), eq(day), eq(ActingDutyEndCause.ENDED_EARLY), any());
        verify(commands, times(1)).load(EMPLOYEE);
    }

    @Test
    @DisplayName("#1566 a due early end whose duty was already closed is withdrawn without publication")
    void staleEndIsWithdrawn() {
        PersonnelCommand command = change(PersonnelCommandKind.ACTING_END, DAY.plusDays(2), null);
        when(duties.find(DUTY)).thenReturn(Optional.of(duty(DAY.plusDays(5), DAY.plusDays(1))));
        ApplyDueCommand due = due(Clock.fixed(NOW.plusSeconds(2 * 86400L), ZONE.zone()));
        due.execute(command.id());
        due.execute(command.id());
        assertThat(command.status()).isEqualTo(CommandStatus.WITHDRAWN);
        assertThat(command.withdrawalReason()).contains("already closed");
        assertThat(command.withdrawnBy()).isNotNull();
        assertThat(command.appliedAt()).isNull();
        assertThat(published).isEmpty();
        verify(commands, times(1)).update(command, NOW.plusSeconds(2 * 86400L), 0);
    }

    @Test
    @DisplayName("#1566 extension updates its loaded version and publishes no event")
    void extensionHasNoEvent() {
        ActingDuty target = duty(DAY.plusDays(3), null);
        PersonnelCommand command = change(PersonnelCommandKind.ACTING_EXTEND, DAY, DAY.plusDays(7));
        effects.changeActingDuty(command, target);
        verify(duties).updateEndsBefore(target, DAY.plusDays(7), NOW);
        assertThat(command.status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("#1566 extension propagates exclusion and optimistic conflicts to roll the entry transaction back")
    void extensionConflictsAreNotSwallowed() {
        ActingDuty target = duty(DAY.plusDays(3), null);
        PersonnelCommand command = change(PersonnelCommandKind.ACTING_EXTEND, DAY, DAY.plusDays(7));
        for (RuntimeException failure : List.of(new DataIntegrityViolationException("overlap exclusion"),
                new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "concurrent extension"))) {
            doThrow(failure).when(duties).updateEndsBefore(target, DAY.plusDays(7), NOW);
            assertThatThrownBy(() -> effects.changeActingDuty(command, target)).isSameAs(failure);
        }
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("#1566 late expiry uses the exclusive end, no command id, and does nothing on replay")
    void lateExpiryIsCommandlessAndIdempotent() {
        ActingDuty target = duty(DAY.minusDays(1), null);
        when(duties.find(DUTY)).thenReturn(Optional.of(target), Optional.of(target), Optional.of(duty(DAY.minusDays(1), DAY.minusDays(1))));
        PersonnelCommand start = PersonnelCommand.restore(START, EMPLOYEE, PersonnelCommandKind.ACTING_START, DAY.minusDays(3), CommandStatus.EFFECTIVE,
                null, null, null, null, null, null, null, null, null, null, RECORDER, NOW.minusSeconds(3 * 86400L), NOW, 0, 12);
        when(commands.find(START)).thenReturn(Optional.of(start));
        when(sources.employeeAttribution(EMPLOYEE, DAY.minusDays(1))).thenReturn(Optional.of(ENTITY));
        ExpireActingDuty expiry = new ExpireActingDuty(duties, commands, sources, events, clock, ZONE);
        expiry.execute(DUTY);
        expiry.execute(DUTY);
        verify(duties, times(1)).updateEnd(target, DAY.minusDays(1), ActingDutyEndCause.EXPIRED, NOW);
        assertThat(published).singleElement().isInstanceOfSatisfying(EmployeeActingDutyEnded.class, event -> {
            assertThat(event.personnelCommandId()).isNull();
            assertThat(event.endedOn()).isEqualTo(DAY.minusDays(1));
            assertThat(event.envelope().businessDate()).isEqualTo(DAY.minusDays(1));
            assertThat(event.envelope().aggregateId()).isEqualTo(EMPLOYEE.value());
            assertThat(event.envelope().aggregateVersion()).isEqualTo(13);
            assertThat(event.envelope().legalEntityId()).isEqualTo(ENTITY);
            assertThat(event.endCause()).isEqualTo(ActingDutyEndCause.EXPIRED);
        });
        verify(duties).saveEventSequence(EMPLOYEE, 13);
        assertThat(start.status()).isEqualTo(CommandStatus.EFFECTIVE);
    }

    @Test
    @DisplayName("#1566 expiry rereads under the employee lock and respects an extension committed meanwhile")
    void expiryRespectsNewEnd() {
        when(duties.find(DUTY)).thenReturn(Optional.of(duty(DAY, null)), Optional.of(duty(DAY.plusDays(3), null)));
        new ExpireActingDuty(duties, commands, sources, events, clock, ZONE).execute(DUTY);
        assertThat(published).isEmpty();
        verify(duties).lockEmployee(EMPLOYEE);
    }

    @Test
    @DisplayName("#1566 the bounded due job continues past one failure and retries it on its next run")
    void jobContinuesAndRetries() {
        ApplyDueCommand apply = mock(ApplyDueCommand.class);
        ExpireActingDuty expiry = mock(ExpireActingDuty.class);
        ActingDutyId other = new ActingDutyId(TestIds.uuid(9));
        when(duties.openDue(DAY, PersonnelCommandRepository.DUE_BATCH_LIMIT)).thenReturn(List.of(DUTY, other), List.of(DUTY));
        org.mockito.Mockito.doThrow(new IllegalStateException("retry this duty")).doNothing().when(expiry).execute(DUTY);
        PersonnelCommandDueJob job = new PersonnelCommandDueJob(commands, apply, clock, ZONE, duties, expiry);
        job.run();
        job.run();
        verify(expiry, times(2)).execute(DUTY);
        verify(expiry, times(1)).execute(other);
    }

    @ParameterizedTest
    @EnumSource(value = CommandStatus.class, names = {"HELD", "EFFECTIVE"})
    @DisplayName("#1566 a due start dated after a held or effective separation is withdrawn without a duty or event")
    void startAfterSeparationIsWithdrawn(CommandStatus separationStatus) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.ACTING_START, DAY.plusDays(2), null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                COVERED, null, null, DAY.plusDays(9), null, "cover");
        PersonnelCommand start = PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(9)), EMPLOYEE, request, RECORDER, NOW, DAY, 7);
        PersonnelCommand separation = PersonnelCommand.restore(new PersonnelCommandId(TestIds.uuid(10)), EMPLOYEE, PersonnelCommandKind.SEPARATE, DAY,
                separationStatus, null, null, null, null, null, null, null, null, null, null, RECORDER, NOW, null, 0, 7);
        when(commands.find(start.id())).thenReturn(Optional.of(start));
        when(commands.load(EMPLOYEE)).thenReturn(new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(separation), Map.of()));
        due(Clock.fixed(NOW.plusSeconds(2 * 86400L), ZONE.zone())).execute(start.id());
        assertThat(start.status()).isEqualTo(CommandStatus.WITHDRAWN);
        assertThat(start.withdrawalReason()).contains("separates before");
        verify(duties, never()).insert(any());
        assertThat(published).isEmpty();
    }
}
