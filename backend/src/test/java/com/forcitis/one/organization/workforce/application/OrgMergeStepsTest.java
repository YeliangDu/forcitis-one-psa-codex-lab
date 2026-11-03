package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
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

/** #910 S2 design note section 5 rules 2 and 4: a merge that awaits its countersign does not start, and a person the merge already has a transfer for gets no second one. */
class OrgMergeStepsTest {
    static final Instant NOW = Instant.parse("2026-11-08T02:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 8);
    static final OrgStructureCommandId MERGE = new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final OrgUnitId MERGED = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));
    static final OrgUnitId RECEIVING = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a2"));
    static final EmployeeId A = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000011"));
    static final UUID ACTOR = UUID.fromString("019a0000-0000-7000-8000-0000000000d1");

    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final PersonnelCommandService personnel = mock(PersonnelCommandService.class);
    final OrgMergeSteps steps = new OrgMergeSteps(commands, mock(OrgStructureRules.class), mock(OrgMergeProgress.class), mock(OrgMergeCompletion.class), personnel,
            mock(IdGenerator.class), Clock.fixed(NOW, ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")));

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
        when(commands.employedIn(MERGED, DAY)).thenReturn(List.of(A));

        assertThatThrownBy(() -> steps.start(MERGE, ACTOR)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#910 S2 note section 5 rule 4: a person whose transfer from this merge exists, whatever became of it, gets no second transfer; one without gets it")
    void aHeldPersonIsNotMovedTwice() {
        stored(OrgCommandStatus.IN_PROGRESS);
        when(commands.employedIn(MERGED, DAY)).thenReturn(List.of(A));
        when(commands.hasGeneratedTransfer(MERGE, A)).thenReturn(true);

        steps.move(MERGE, A, ACTOR);

        verify(personnel, never()).enter(any(), any(), any());

        when(commands.hasGeneratedTransfer(MERGE, A)).thenReturn(false);

        steps.move(MERGE, A, ACTOR);

        verify(personnel).enter(any(), any(), any());
    }
}
