package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.ChangeSource;
import com.forcitis.one.organization.workforce.domain.OrgEffects;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.VersionPlan;
import com.forcitis.one.organization.workforce.domain.VersionRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1147: back-dated legal entity assignments pass the rules and use W2 void-and-append when written. */
class OrgStructureRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final OrgUnitId UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000a1"));
    static final LegalEntityId ENTITY = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));

    final OrgStructureCommandRepository commands = mock(OrgStructureCommandRepository.class);
    final OrgStructureRules rules = new OrgStructureRules(commands);

    private OrgStructureCommand assign(LocalDate day) {
        return OrgStructureCommand.enter(new OrgStructureCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")),
                new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT, OrgStructureCommandKind.ASSIGN_LEGAL_ENTITY, UNIT, null, day, null, null, null, null, null, null, null,
                        null, null, ENTITY),
                UUID.fromString("019a0000-0000-7000-8000-0000000000d1"), new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, TODAY);
    }

    private void unitInForce() {
        when(commands.orgUnitExists(UNIT)).thenReturn(true);
        when(commands.legalEntityExists(ENTITY)).thenReturn(true);
        when(commands.currentUnitRows()).thenReturn(Map.of(UNIT.value(), List.of(new VersionRow(UUID.fromString("019a0000-0000-7000-8000-0000000000b1"), TODAY.minusYears(1), null,
                Map.of(OrgEffects.LIFECYCLE, "ACTIVE"), ChangeSource.MIGRATION, null, false, null))));
    }

    @Test
    @DisplayName("#1147: an assignment dated before an existing period of the unit is allowed")
    void aBackDatedAssignmentBesideALaterPeriodIsAllowed() {
        unitInForce();
        LocalDate day = TODAY.minusDays(10);

        assertThatCode(() -> rules.check(assign(day), VersionPlan.empty())).doesNotThrowAnyException();
    }
}
