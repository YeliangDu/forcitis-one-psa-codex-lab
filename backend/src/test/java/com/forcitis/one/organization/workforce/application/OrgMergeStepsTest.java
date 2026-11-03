package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.AssignmentKind;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.HeldPost;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** #910 S2 design note section 5 rules 2 and 4: a merge that awaits its countersign does not start, and a person the merge already has a transfer for gets no second one. */
class OrgMergeStepsTest {
    static final Instant NOW = Instant.parse("2026-11-08T02:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 8);
    static final OrgStructureCommandId MERGE = new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final OrgUnitId MERGED = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));
    static final OrgUnitId RECEIVING = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a2"));
    static final EmployeeId A = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000011"));
    static final AssignmentId ROOT = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-000000000021"));
    static final AssignmentId OTHER_ROOT = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-000000000022"));
    static final HeldPost PRIMARY = new HeldPost(A, ROOT, AssignmentKind.PRIMARY);
    static final HeldPost CONCURRENT = new HeldPost(A, OTHER_ROOT, AssignmentKind.CONCURRENT);
    static final UUID ACTOR = UUID.fromString("019a0000-0000-7000-8000-0000000000d1");

    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final PersonnelCommandService personnel = mock(PersonnelCommandService.class);
    final AttributionCountersignRepository countersigns = mock(AttributionCountersignRepository.class);
    final CountersignRequests requests = mock(CountersignRequests.class);
    final OrgMergeSteps steps = new OrgMergeSteps(commands, mock(OrgStructureRules.class), mock(OrgMergeProgress.class), mock(OrgMergeCompletion.class), personnel,
            countersigns, requests, mock(IdGenerator.class), Clock.fixed(NOW, ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")));

    private void stored(OrgCommandStatus status) {
        OrgStructureCommand merge = OrgStructureCommand.restore(MERGE, OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.MERGE, MERGED, null, DAY, null, null, null, null, null,
                null, null, null, RECEIVING, null, status, status == OrgCommandStatus.IN_PROGRESS ? 1 : null, status == OrgCommandStatus.IN_PROGRESS ? 0 : null,
                new UserId(UUID.randomUUID()), NOW, null, 3);
        when(commands.find(MERGE)).thenReturn(Optional.of(merge));
    }

    @Test
    @DisplayName("#910 S2 note section 5 rule 2: a merge awaiting its countersign is not started and nobody is listed to move")
    void anAwaitingMergeDoesNotStart() {
        stored(OrgCommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN);
        when(commands.postsIn(MERGED, DAY)).thenReturn(List.of(PRIMARY));

        assertThatThrownBy(() -> steps.start(MERGE, ACTOR)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#910 S2 note section 5 rule 4: a post whose transfer from this merge exists, whatever became of it, gets no second transfer; one without gets it")
    void aHeldPostIsNotMovedTwice() {
        stored(OrgCommandStatus.IN_PROGRESS);
        when(commands.postsIn(MERGED, DAY)).thenReturn(List.of(PRIMARY));
        when(commands.hasGeneratedTransfer(MERGE, ROOT)).thenReturn(true);

        steps.move(MERGE, PRIMARY, ACTOR);

        verify(personnel, never()).enter(any(), any(), any());

        when(commands.hasGeneratedTransfer(MERGE, ROOT)).thenReturn(false);

        steps.move(MERGE, PRIMARY, ACTOR);

        verify(personnel).enter(any(), any(), any());
    }

    @Test
    @DisplayName("#1040: one person's two posts get one transfer each; the transfer of a concurrent post names its root, that of the primary post names none")
    void eachPostGetsItsOwnTransfer() {
        stored(OrgCommandStatus.IN_PROGRESS);
        when(commands.postsIn(MERGED, DAY)).thenReturn(List.of(PRIMARY, CONCURRENT));
        when(commands.hasGeneratedTransfer(MERGE, ROOT)).thenReturn(true);

        steps.move(MERGE, PRIMARY, ACTOR);
        steps.move(MERGE, CONCURRENT, ACTOR);

        ArgumentCaptor<EnterPersonnelCommand> entered = ArgumentCaptor.forClass(EnterPersonnelCommand.class);
        verify(personnel).enter(entered.capture(), any(), any());
        assertThat(entered.getValue().targetAssignmentId()).isEqualTo(OTHER_ROOT);
    }

    @Test
    @DisplayName("#1146: the start of a due merge lapses the open requests it locked, the withdrawal set that nobody can decide any more; a merge already in progress lapses nothing")
    void startingTheMergeLapsesItsOpenRequests() {
        stored(OrgCommandStatus.PENDING);
        when(commands.postsIn(MERGED, DAY)).thenReturn(List.of(PRIMARY));
        AttributionCountersign open = mock(AttributionCountersign.class);
        when(countersigns.lockOfOrgCommand(MERGE)).thenReturn(List.of(open));

        steps.start(MERGE, ACTOR);

        verify(requests).lapse(any(CountersignRequests.Parent.class), eq(List.of(open)), eq(ActorType.SYSTEM), eq(ACTOR), any(), any(), any());

        stored(OrgCommandStatus.IN_PROGRESS);

        steps.start(MERGE, ACTOR);

        verify(requests, org.mockito.Mockito.times(1)).lapse(any(CountersignRequests.Parent.class), any(), any(), any(), any(), any(), any());
    }
}
