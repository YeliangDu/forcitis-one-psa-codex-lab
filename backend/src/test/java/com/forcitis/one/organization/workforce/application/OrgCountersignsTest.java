package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.EmployeeLegalEntityChange;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * #910 review rv910s1 M1, M2, M3 on the use case with its collaborators faked (the acceptance tests of #1041 stay untouched): the last approval judges the
 * structure rules again, a decider whose own department chain changes entity cannot approve, and a command that takes effect at the last approval
 * publishes its own event and one EmployeeLegalEntityChanged per employee of the basis.
 */
class OrgCountersignsTest {
    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final OrgUnitId UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));
    static final LegalEntityId BEFORE = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    static final LegalEntityId AFTER = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e2"));
    static final UserId DRAFTER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa"));
    static final UserId DECIDER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000bb"));
    static final AttributionCountersignId REQUEST = new AttributionCountersignId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
    static final List<OrgCountersignBasis> BASIS = List.of(
            new OrgCountersignBasis(new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000011")), BEFORE, AFTER),
            new OrgCountersignBasis(new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000012")), BEFORE, AFTER));

    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final PersonnelCommandRepository personnel = mock(PersonnelCommandRepository.class);
    final AttributionCountersignRepository countersigns = mock(AttributionCountersignRepository.class);
    final CountersignRequests requests = mock(CountersignRequests.class);
    final OrgCountersignBases bases = mock(OrgCountersignBases.class);
    final OrgStructureRules rules = mock(OrgStructureRules.class);
    final OrgCommandWrites writes = mock(OrgCommandWrites.class);
    final DomainEvents events = mock(DomainEvents.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final OrgCountersigns countersign = new OrgCountersigns(commands, personnel, countersigns, requests, mock(CountersignDeciders.class), bases, rules, writes, mock(AuditLog.class),
            events, currentUser, ids, Clock.fixed(NOW, ZoneOffset.UTC), new BusinessZone(ZoneOffset.UTC));

    private OrgStructureCommand waiting(LocalDate day) {
        OrgStructureCommand command = OrgStructureCommand.enter(new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")),
                new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.ASSIGN_LEGAL_ENTITY, UNIT, null, day, null, null, null, null, null, null, null,
                        null, null, AFTER),
                UUID.fromString("019a0000-0000-7000-8000-0000000000d1"), DRAFTER, NOW, TODAY);
        command.requireCountersign();
        return command;
    }

    /** One open CHANGE request: the decision of its head is the last one. */
    private AttributionCountersign decisionOn(OrgStructureCommand command) {
        AttributionCountersign row = AttributionCountersign.openForOrg(REQUEST, command, CountersignRequestKind.CHANGE, 1, AFTER, DRAFTER, NOW, TODAY.plusDays(3), null);
        when(currentUser.userId()).thenReturn(DECIDER);
        when(countersigns.find(REQUEST)).thenReturn(java.util.Optional.of(row));
        when(countersigns.ofOrgCommand(command.id())).thenReturn(List.of(row));
        when(commands.find(command.id())).thenReturn(java.util.Optional.of(command));
        when(commands.basisOf(command.id())).thenReturn(BASIS);
        when(bases.of(command)).thenReturn(BASIS);
        when(ids.next()).thenReturn(UUID.randomUUID());
        when(writes.planNow(any(), any())).thenReturn(VersionPlan.empty());
        when(requests.approved(any(AttributionCountersign.class), any(CountersignRequests.Parent.class), anyLong(), any(), any(), any(), any())).thenReturn(new AuditEntryId(UUID.randomUUID()));
        return row;
    }

    @Test
    @DisplayName("#910 rv910s1 M1: the last approval judges the structure rules again: a command that no longer holds is withdrawn with the reason and nothing is written")
    void theLastApprovalWithdrawsACommandThatNoLongerHolds() {
        OrgStructureCommand command = waiting(TODAY.plusDays(10));
        AttributionCountersign row = decisionOn(command);
        doThrow(new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED, ErrorKind.CONFLICT, "the unit was dissolved meanwhile"))
                .when(rules).checkStillHolds(any(), any());

        countersign.decide(REQUEST, null, true);

        assertThat(command.status()).isEqualTo(OrgCommandStatus.WITHDRAWN);
        assertThat(row.status()).isEqualTo(CountersignStatus.REJECTED);
        assertThat(row.decisionReason()).contains("dissolved");
        verify(requests, never()).approved(any(AttributionCountersign.class), any(CountersignRequests.Parent.class), anyLong(), any(), any(), any(), any());
        verify(writes, never()).applyAfterCountersign(any(), any(), any());
        verify(events, never()).publish(any());
    }

    @Test
    @DisplayName("#910 rv910s1 M2: a decider whose own department chain would change legal entity cannot approve: refused before the row changes")
    void aDeciderInsideTheChangedChainCannotApprove() {
        OrgStructureCommand command = waiting(TODAY.plusDays(10));
        AttributionCountersign row = decisionOn(command);
        doThrow(new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN, ErrorKind.FORBIDDEN, "own chain")).when(bases).requireUserUnaffected(command, DECIDER);

        assertThatThrownBy(() -> countersign.decide(REQUEST, null, true)).isInstanceOf(DomainException.class).hasMessageContaining("own chain");

        assertThat(row.status()).isEqualTo(CountersignStatus.OPEN);
        assertThat(command.status()).isEqualTo(OrgCommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN);
        verify(writes, never()).applyAfterCountersign(any(), any(), any());
    }

    @Test
    @DisplayName("#910 rv910s1 M3: a command that takes effect at the last approval publishes its own event and one EmployeeLegalEntityChanged per employee of the basis; a later-dated one publishes none yet")
    void takingEffectPublishesTheEvents() {
        OrgStructureCommand due = waiting(TODAY);
        decisionOn(due);
        when(personnel.eventSequence(any())).thenReturn(7L);

        countersign.decide(REQUEST, null, true);

        assertThat(due.status()).isEqualTo(OrgCommandStatus.EFFECTIVE);
        ArgumentCaptor<AggregateRoot> published = ArgumentCaptor.forClass(AggregateRoot.class);
        verify(events, times(1 + BASIS.size())).publish(published.capture());
        assertThat(published.getAllValues()).containsOnlyOnce(due);
        assertThat(published.getAllValues().stream().filter(EmployeeLegalEntityChange.class::isInstance)).hasSize(BASIS.size());
        verify(personnel, times(BASIS.size())).saveEventSequence(any(), anyLong());
    }

    @Test
    @DisplayName("#910 rv910s1 M3: a command dated after today goes to PENDING at the last approval and publishes nothing yet")
    void aLaterDatedCommandPublishesNothingYet() {
        OrgStructureCommand later = waiting(TODAY.plusDays(10));
        decisionOn(later);

        countersign.decide(REQUEST, null, true);

        assertThat(later.status()).isEqualTo(OrgCommandStatus.PENDING);
        verify(events, never()).publish(any());
    }
}
