package com.forcitis.one.organization.workforce.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelHistory;
import com.forcitis.one.organization.workforce.domain.SeparationHold;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #910 QF1 (one-design ruling, rule 1): a released separation writes versions that may take a person out of a merged unit, so the merges holding them are recounted in that transaction. */
class HeldSeparationReleaseTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final UserId USER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa"));
    static final PersonnelCommandId COMMAND = new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final SeparationHoldId HOLD = new SeparationHoldId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    static final OrgStructureCommandId MERGE = new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000d1"));

    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final OrgMergeProgress mergeProgress = mock(OrgMergeProgress.class);
    final IdGenerator ids = mock(IdGenerator.class);

    @Test
    @DisplayName("#910 QF1: a released separation recounts the merges that hold the person, after its versions are written")
    void theReleaseRecountsTheHoldingMerges() {
        when(ids.next()).thenReturn(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
        when(commands.load(EMPLOYEE)).thenReturn(new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()));
        PersonnelCommand command = PersonnelCommand.restore(COMMAND, EMPLOYEE, PersonnelCommandKind.SEPARATE, LocalDate.of(2026, 11, 2), CommandStatus.HELD,
                null, null, null, null, null, null, null, null, null, null, USER, NOW, null, 0, 0);
        SeparationHold hold = SeparationHold.restore(HOLD, COMMAND, EMPLOYEE, HoldStatus.HELD, 0, null, null, null, null);
        hold.release(USER, "synthetic: confirmed", NOW);
        HeldSeparationRelease release = new HeldSeparationRelease(commands, new PersonnelPlans(ids), mock(LegalEntityResolutionSources.class), mock(AuditLog.class),
                mock(DomainEvents.class), ids, new BusinessZone(ZoneId.of("Asia/Shanghai")), mergeProgress, mock(EffectPublisher.class));
        List<OrgStructureCommandId> holding = List.of(MERGE);

        release.execute(command, 0, hold, 0, HeldSeparationRelease.Releaser.user(USER), NOW, "synthetic", holding);

        verify(mergeProgress).recount(holding);
    }
}
