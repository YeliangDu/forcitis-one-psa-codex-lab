package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.OrgEffects;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #910 design ruling on the basis (note section 4.3, both sides NOT NULL): a first attribution (no entity before) is not a change and has no basis row; a command that would
 * leave someone without an entity they have is refused with its own error.
 */
class OrgCountersignBasesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final LocalDate DAY = TODAY.plusDays(10);
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final OrgUnitId UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));
    static final OrgUnitId OLD_PARENT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a2"));
    static final OrgUnitId NEW_PARENT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a3"));
    static final EmployeeId PERSON = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000011"));
    static final LegalEntityId ENTITY = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));

    final LegalEntityResolutionSources sources = mock(LegalEntityResolutionSources.class);
    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final OrgCountersignBases bases = new OrgCountersignBases(sources, commands, mock(AccountEmployees.class));

    private OrgStructureCommand reparentUnderNewParent() {
        return OrgStructureCommand.enter(new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")),
                new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.REPARENT, UNIT, null, DAY, null, null, null, null, null, NEW_PARENT, null, null, null,
                        null),
                UUID.fromString("019a0000-0000-7000-8000-0000000000d1"), new UserId(UUID.randomUUID()), NOW, TODAY);
    }

    /** The person sits in UNIT; UNIT has no entity of its own and is below OLD_PARENT (attributed to ENTITY or not); NEW_PARENT has none. */
    private void world(boolean oldParentAttributed) {
        when(commands.currentUnitRows()).thenReturn(Map.of(UNIT.value(), List.of(new VersionRow(UUID.randomUUID(), TODAY.minusYears(1), null,
                Map.of(OrgEffects.LIFECYCLE, "ACTIVE", OrgEffects.PARENT, OLD_PARENT.value()), ChangeSource.MIGRATION, null, false, null))));
        when(commands.employedIn(UNIT, DAY)).thenReturn(List.of(PERSON));
        when(sources.employeeAttribution(PERSON, DAY)).thenReturn(Optional.empty());
        when(sources.assignedUnit(PERSON, DAY)).thenReturn(Optional.of(UNIT));
        when(sources.orgUnitAttribution(UNIT, DAY)).thenReturn(Optional.empty());
        when(sources.parentUnit(UNIT, DAY)).thenReturn(Optional.of(OLD_PARENT));
        when(sources.orgUnitAttribution(OLD_PARENT, DAY)).thenReturn(oldParentAttributed ? Optional.of(ENTITY) : Optional.empty());
        when(sources.parentUnit(OLD_PARENT, DAY)).thenReturn(Optional.empty());
        when(sources.orgUnitAttribution(NEW_PARENT, DAY)).thenReturn(Optional.empty());
        when(sources.parentUnit(NEW_PARENT, DAY)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("#910 design ruling: a person who has an entity before and would have none after is refused with the legal entity conflict error, nothing is returned")
    void leavingSomeoneWithoutAnEntityIsRefused() {
        world(true);

        assertThatThrownBy(() -> bases.of(reparentUnderNewParent())).isInstanceOfSatisfying(DomainException.class,
                refused -> assertThat(refused.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ORG_LEGAL_ENTITY_CONFLICT));
    }

    @Test
    @DisplayName("#910 design ruling: a person who has no entity before (first attribution) is not a change: no basis row, no request")
    void aFirstAttributionHasNoBasisRow() {
        world(false);

        assertThat(bases.of(reparentUnderNewParent())).isEmpty();
    }
}
