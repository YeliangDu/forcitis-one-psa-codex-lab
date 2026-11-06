package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class ActingDutyEffectTest {
    static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    static final EmployeeId EMPLOYEE = new EmployeeId(TestIds.uuid(1));
    static final EmploymentRelationshipId REL = new EmploymentRelationshipId(TestIds.uuid(2));
    static final UserId RECORDER = new UserId(TestIds.uuid(3));
    static final OrgUnitId COVERED = new OrgUnitId(TestIds.uuid(4));
    static final LegalEntityId ENTITY = new LegalEntityId(TestIds.uuid(5));
    static final BusinessZone ZONE = new BusinessZone(ZoneId.of("Asia/Shanghai"));
    static final PersonnelHistory HISTORY = new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final ActingDutyRepository duties = mock(ActingDutyRepository.class);
    final LegalEntityResolutionSources sources = mock(LegalEntityResolutionSources.class);
    final IdGenerator ids = () -> TestIds.uuid(30);
    final Clock clock = Clock.fixed(NOW, ZONE.zone());
    final List<DomainEvent> published = new ArrayList<>();
    final DomainEvents events = aggregate -> published.addAll(aggregate.drainEvents(TestEnvelopes.INSTANCE));
    final EffectPublisher effects = new EffectPublisher(commands, sources, events, duties, ids, clock);

    PersonnelCommand command(LocalDate starts) {
        EnterPersonnelCommand request = new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.ACTING_START, starts, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, REL,
                COVERED, null, null, starts.plusDays(3), null, "temporary coverage");
        PersonnelCommand command = PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(6)), EMPLOYEE, request, RECORDER, NOW, DAY, 7);
        when(commands.find(command.id())).thenReturn(Optional.of(command));
        when(commands.load(EMPLOYEE)).thenReturn(HISTORY);
        when(sources.employeeAttribution(EMPLOYEE, starts)).thenReturn(Optional.of(ENTITY));
        return command;
    }

    ApplyDueCommand due(Clock dueClock) {
        return new ApplyDueCommand(commands, new PersonnelPlans(ids), mock(SeparationHoldCheck.class), sources, mock(CountersignRequests.class),
                mock(AttributionCountersignRepository.class), mock(OrgMergeProgress.class), events, ids, dueClock, ZONE, new EffectPublisher(commands, sources, events, duties, ids, dueClock));
    }

    @Test
    @DisplayName("#1566 immediate start writes its duty and publishes the Started payload on the employee stream")
    void immediateStartPublishesTheSavedDuty() {
        PersonnelCommand command = command(DAY);

        effects.publish(command, HISTORY, DAY);

        ArgumentCaptor<ActingDuty> stored = ArgumentCaptor.forClass(ActingDuty.class);
        verify(duties).insert(stored.capture());
        ActingDuty duty = stored.getValue();
        assertThat(duty.sourcePersonnelCommandId()).isEqualTo(command.id());
        assertThat(duty.employmentRelationshipId()).isEqualTo(REL);
        assertThat(duty.createdBy()).isEqualTo(RECORDER);
        assertThat(duty.createdAt()).isEqualTo(NOW);
        assertThat(duty.reason()).isEqualTo("temporary coverage");
        assertThat(duty.endedOn()).isNull();
        assertThat(published).singleElement().isInstanceOfSatisfying(EmployeeActingDutyStarted.class, event -> {
            assertThat(event.actingDutyId()).isEqualTo(duty.id());
            assertThat(event.personnelCommandId()).isEqualTo(command.id());
            assertThat(event.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(event.employmentRelationshipId()).isEqualTo(REL);
            assertThat(event.coveredOrgUnitId()).isEqualTo(COVERED);
            assertThat(event.coveredPositionId()).isNull();
            assertThat(event.coveredEmployeeId()).isNull();
            assertThat(event.startsOn()).isEqualTo(DAY);
            assertThat(event.endsBefore()).isEqualTo(DAY.plusDays(3));
            assertThat(event.envelope().businessDate()).isEqualTo(DAY);
            assertThat(event.envelope().aggregateId()).isEqualTo(EMPLOYEE.value());
            assertThat(event.envelope().legalEntityId()).isEqualTo(ENTITY);
            assertThat(event.envelope().aggregateVersion()).isEqualTo(8);
        });
        verify(commands).saveEventSequence(EMPLOYEE, 8);
    }

    @Test
    @DisplayName("#1566 the due step waits before the start, then starts once even when delivered twice")
    void dueStepWaitsAndIsIdempotent() {
        LocalDate starts = DAY.plusDays(2);
        PersonnelCommand command = command(starts);
        due(clock).execute(command.id());
        assertThat(command.status()).isEqualTo(CommandStatus.PENDING);
        verifyNoInteractions(duties);
        assertThat(published).isEmpty();

        Clock onStart = Clock.fixed(NOW.plusSeconds(2 * 86400L), ZONE.zone());
        ApplyDueCommand due = due(onStart);
        due.execute(command.id());
        due.execute(command.id());

        assertThat(command.status()).isEqualTo(CommandStatus.EFFECTIVE);
        verify(duties, times(1)).insert(any(ActingDuty.class));
        assertThat(published).singleElement().isInstanceOfSatisfying(EmployeeActingDutyStarted.class,
                event -> assertThat(event.envelope().businessDate()).isEqualTo(starts));
        verify(commands, times(1)).update(command, onStart.instant(), 0);
    }

    @Test
    @DisplayName("#1566 withdrawal prevents a due start from writing a duty or publishing")
    void withdrawnStartDoesNotTakeEffect() {
        PersonnelCommand command = command(DAY.plusDays(1));
        command.withdraw(RECORDER, "coverage no longer needed", NOW);

        due(Clock.fixed(NOW.plusSeconds(86400), ZONE.zone())).execute(command.id());

        verifyNoInteractions(duties);
        assertThat(published).isEmpty();
        assertThat(command.status()).isEqualTo(CommandStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("#1566 an exclusion violation propagates before publication so the use-case transaction rolls back")
    void exclusionViolationIsNotSwallowed() {
        PersonnelCommand command = command(DAY);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("coverage exclusion violated");
        doThrow(failure).when(duties).insert(any(ActingDuty.class));

        assertThatThrownBy(() -> effects.publish(command, HISTORY, DAY)).isSameAs(failure);
        assertThat(published).isEmpty();
    }
}
