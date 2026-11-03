package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.events.OrgUnitAttributesSet;
import com.forcitis.one.organization.workforce.events.OrgUnitCreated;
import com.forcitis.one.organization.workforce.events.OrgUnitDissolved;
import com.forcitis.one.organization.workforce.events.OrgUnitHeadAssigned;
import com.forcitis.one.organization.workforce.events.OrgUnitMerged;
import com.forcitis.one.organization.workforce.events.OrgUnitReceivablesManagerAssigned;
import com.forcitis.one.organization.workforce.events.OrgUnitReparented;
import com.forcitis.one.organization.workforce.events.PositionCreated;
import com.forcitis.one.organization.workforce.events.PositionDisabled;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IMP-1030-ORG-F2b (ADR-0068 item 4): the publisher test of the nine org-structure events. A command that takes effect records exactly its event, about the
 * command (sequence 1), dated by the effective date; a pending command records none until it is told to, and a merge only when its last person has moved.
 */
class OrgCommandEventsTest {
    static final Instant NOW = Instant.parse("2026-11-03T02:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    static final OrgStructureCommandId ID = new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
    static final OrgUnitId UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b1"));
    static final OrgUnitId OTHER = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b2"));
    static final PositionId POSITION = new PositionId(UUID.fromString("019a0000-0000-7000-8000-0000000000b3"));
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final UUID NEW_TARGET = UUID.fromString("019a0000-0000-7000-8000-0000000000b9");

    private static DomainEvent published(OrgStructureTargetKind target, OrgStructureCommandKind kind, OrgUnitId unit, PositionId position, String name, Boolean delivery,
                                         OrgUnitId parent, EmployeeId head, EmployeeId manager) {
        OrgStructureCommand command = OrgStructureCommand.enter(ID, new EnterOrgStructureCommand(target, kind, unit, position, DAY, null, name, null, null, delivery, parent, head, manager,
                null, null), NEW_TARGET, new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, DAY);
        command.recordEffect();
        List<DomainEvent> events = command.drainEvents(TestEnvelopes.INSTANCE);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().envelope().aggregateType()).isEqualTo("OrgStructureCommand");
        assertThat(events.getFirst().envelope().aggregateId()).isEqualTo(ID.value());
        assertThat(events.getFirst().envelope().aggregateVersion()).isEqualTo(1);
        assertThat(events.getFirst().envelope().businessDate()).isEqualTo(DAY);
        return events.getFirst();
    }

    @Test
    @DisplayName("IMP-1030-ORG-F2b each kind of org-structure command records exactly its own event, with the ids of what it changed")
    void eachKindRecordsItsEvent() {
        OrgUnitCreated created = (OrgUnitCreated) published(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.CREATE, null, null, "n", false, OTHER, null, null);
        assertThat(created.orgUnitId().value()).isEqualTo(NEW_TARGET);
        assertThat(created.parentOrgUnitId()).isEqualTo(OTHER);
        assertThat(((OrgUnitDissolved) published(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.DISSOLVE, UNIT, null, null, null, null, null, null)).orgUnitId()).isEqualTo(UNIT);
        assertThat(published(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.SET_ATTRIBUTES, UNIT, null, "n", null, null, null, null)).isInstanceOf(OrgUnitAttributesSet.class);
        assertThat(((OrgUnitReparented) published(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.REPARENT, UNIT, null, null, null, OTHER, null, null)).newParentOrgUnitId())
                .isEqualTo(OTHER);
        assertThat(((OrgUnitHeadAssigned) published(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.ASSIGN_HEAD, UNIT, null, null, null, null, EMPLOYEE, null)).headEmployeeId())
                .isEqualTo(EMPLOYEE);
        assertThat(((OrgUnitReceivablesManagerAssigned) published(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.ASSIGN_RECEIVABLES_MANAGER, UNIT, null, null, null, null, null,
                EMPLOYEE)).receivablesManagerEmployeeId()).isEqualTo(EMPLOYEE);
        assertThat(((PositionCreated) published(OrgStructureTargetKind.POSITION, OrgStructureCommandKind.CREATE, null, null, "p", null, null, null, null)).positionId().value())
                .isEqualTo(NEW_TARGET);
        assertThat(((PositionDisabled) published(OrgStructureTargetKind.POSITION, OrgStructureCommandKind.DISABLE, null, POSITION, null, null, null, null, null)).positionId())
                .isEqualTo(POSITION);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F2b a merge is pending when entered, records no event then, and OrgUnitMerged when its last person has moved")
    void aMergePublishesWhenItIsDone() {
        OrgStructureCommand merge = OrgStructureCommand.enter(ID, new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.MERGE, UNIT, null, DAY, null, null,
                null, null, null, null, null, null, OTHER, null), NEW_TARGET, new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, DAY);
        assertThat(merge.status()).isEqualTo(OrgCommandStatus.PENDING);
        assertThat(merge.takesEffectAtEntry()).isFalse();
        assertThat(merge.hasPendingEvents()).isFalse();
        merge.startMerge(0);
        merge.completeMerge(NOW);
        merge.recordEffect();
        List<DomainEvent> events = merge.drainEvents(TestEnvelopes.INSTANCE);
        assertThat(events).hasSize(1);
        assertThat(((OrgUnitMerged) events.getFirst()).mergedIntoOrgUnitId()).isEqualTo(OTHER);
    }
}
