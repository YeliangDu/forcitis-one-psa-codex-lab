package com.forcitis.one.organization.workforce.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import com.forcitis.one.organization.workforce.domain.OrgEffects;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * #910 review rv910s1 M5: a waiting command locks the unit rows of its scope and then the employee rows of its basis, each in id order, the order a personnel entry takes them
 * (unit, then employee); with another order two entries in the same scope could deadlock, and with no lock the freeze check of a concurrent entry could miss the waiting command.
 */
class OrgCountersignFreezeTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final UUID TOP = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");
    static final UUID CHILD = UUID.fromString("019a0000-0000-7000-8000-0000000000a2");
    static final LegalEntityId ENTITY = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));

    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final OrgCountersignFreeze freeze = new OrgCountersignFreeze(commands);

    private VersionRow row(UUID parent) {
        return new VersionRow(UUID.randomUUID(), TODAY.minusYears(1), null, parent == null ? Map.of(OrgEffects.LIFECYCLE, "ACTIVE") : Map.of(OrgEffects.LIFECYCLE, "ACTIVE", OrgEffects.PARENT, parent),
                ChangeSource.MIGRATION, null, false, null);
    }

    @Test
    @DisplayName("#910 rv910s1 M5: holdScope locks the units of the scope in id order, then the employees of the basis in id order")
    void theScopeIsLockedInTheOrderOfAPersonnelEntry() {
        when(commands.currentUnitRows()).thenReturn(Map.of(TOP, List.of(row(null)), CHILD, List.of(row(TOP))));
        OrgStructureCommand waiting = OrgStructureCommand.enter(new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")),
                new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.ASSIGN_LEGAL_ENTITY, new OrgUnitId(TOP), null, TODAY.plusDays(5), null, null, null, null,
                        null, null, null, null, null, ENTITY),
                UUID.fromString("019a0000-0000-7000-8000-0000000000d1"), new UserId(UUID.randomUUID()), NOW, TODAY);
        EmployeeId low = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000011"));
        EmployeeId high = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000022"));

        freeze.holdScope(waiting, List.of(new OrgCountersignBasis(high, ENTITY, ENTITY), new OrgCountersignBasis(low, ENTITY, ENTITY)));

        InOrder order = inOrder(commands);
        order.verify(commands).lockOrgUnit(new OrgUnitId(TOP));
        order.verify(commands).lockOrgUnit(new OrgUnitId(CHILD));
        order.verify(commands).lockEmployee(low);
        order.verify(commands).lockEmployee(high);
        order.verifyNoMoreInteractions();
    }
}
