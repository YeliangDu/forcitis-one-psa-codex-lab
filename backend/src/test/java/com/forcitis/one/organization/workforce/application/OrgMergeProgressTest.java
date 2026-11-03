package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** #910 S2: the exemption test of a merge's transfer, the progress count judged on who is left in the merged unit, and the lock order of a command about a held person. */
class OrgMergeProgressTest {
    static final Instant NOW = Instant.parse("2026-11-08T02:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 8);
    static final OrgStructureCommandId MERGE = new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final OrgUnitId MERGED = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));
    static final OrgUnitId RECEIVING = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a2"));
    static final EmployeeId A = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000011"));
    static final EmployeeId B = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000022"));
    static final LegalEntityId E1 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    static final LegalEntityId E2 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e2"));
    static final LegalEntityId E3 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e3"));

    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final OrgMergeCompletion completion = mock(OrgMergeCompletion.class);
    final OrgMergeProgress progress = new OrgMergeProgress(commands, completion, Clock.fixed(NOW, ZoneOffset.UTC));

    private OrgStructureCommand merge(int affected, int transferred) {
        return OrgStructureCommand.restore(MERGE, OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.MERGE, MERGED, null, DAY, null, null, null, null, null, null, null, null,
                RECEIVING, null, OrgCommandStatus.IN_PROGRESS, affected, transferred, new UserId(UUID.randomUUID()), NOW, null, 3);
    }

    @Test
    @DisplayName("#910 S2 ADR-0073 guard 1: a transfer is exempt only when the person's entity before and after are the basis row's; another change, or no basis row, waits")
    void onlyTheApprovedChangeIsExempt() {
        when(commands.basisOf(MERGE)).thenReturn(List.of(new OrgCountersignBasis(A, E1, E2)));

        assertThat(progress.approved(MERGE, A, new CountersignBases.Basis(E1, E2))).isTrue();
        assertThat(progress.approved(MERGE, A, new CountersignBases.Basis(E1, E3))).isFalse();
        assertThat(progress.approved(MERGE, A, new CountersignBases.Basis(E3, E2))).isFalse();
        assertThat(progress.approved(MERGE, B, new CountersignBases.Basis(E1, E2))).isFalse();
    }

    @Test
    @DisplayName("#910 S2 one-design QF1 (b): the count is the people of the start who are out of the unit now; somebody left means the merge is not completed")
    void whoIsLeftIsNotCounted() {
        OrgStructureCommand merge = merge(3, 1);
        when(commands.employedIn(MERGED, DAY)).thenReturn(List.of(A));

        progress.recount(merge);

        assertThat(merge.transferredEmployeeCount()).isEqualTo(2);
        verify(commands).update(merge, NOW, 3);
        verify(completion, never()).finish(any(), any());
    }

    @Test
    @DisplayName("#910 S2 one-design QF1 (b): the count never falls, and an unchanged count writes nothing")
    void theCountOnlyRises() {
        OrgStructureCommand merge = merge(3, 2);
        when(commands.employedIn(MERGED, DAY)).thenReturn(List.of(A, B));

        progress.recount(merge);

        assertThat(merge.transferredEmployeeCount()).isEqualTo(2);
        verify(commands, never()).update(any(), any(), anyLong());
    }

    @Test
    @DisplayName("#910 S2 one-design QF1 (b): when nobody is left from the merge day on, the merge ends; when only a later arrival is, it does not")
    void anEmptyUnitEndsTheMerge() {
        OrgStructureCommand merge = merge(3, 2);
        OrgStructureCommand stored = merge(3, 3);
        when(commands.employedIn(MERGED, DAY)).thenReturn(List.of());
        when(commands.find(MERGE)).thenReturn(Optional.of(stored));
        when(completion.unitIsEmpty(merge)).thenReturn(true);

        progress.recount(merge);

        verify(completion).finish(org.mockito.ArgumentMatchers.eq(stored), any());

        OrgStructureCommand other = merge(3, 2);
        when(completion.unitIsEmpty(other)).thenReturn(false);
        org.mockito.Mockito.clearInvocations(completion);
        progress.recount(other);
        verify(completion, never()).finish(any(), any());
    }

    @Test
    @DisplayName("#910 S2 one-design 6589166321 rule 1: the units of the merges holding the employee are locked, in the order the repository lists them, before the caller locks the employee")
    void theHoldingUnitsAreLockedFirst() {
        when(commands.mergesInProgressOf(A)).thenReturn(List.of(MERGE));
        when(commands.find(MERGE)).thenReturn(Optional.of(merge(3, 1)));

        assertThat(progress.lockHolding(A)).containsExactly(MERGE);

        InOrder order = inOrder(commands);
        order.verify(commands).mergesInProgressOf(A);
        order.verify(commands).lockOrgUnit(MERGED);
    }
}
