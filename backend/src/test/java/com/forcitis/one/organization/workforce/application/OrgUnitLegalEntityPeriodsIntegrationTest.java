package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * #910 review rv910s1r3 M1 and M2 on the real SQL of the unit's legal entity periods: an assignment dated inside a period that was closed without a successor is seen (the exclusion
 * constraint would refuse the new period), and withdrawing an earlier assignment after a later one keeps the later one's start.
 */
class OrgUnitLegalEntityPeriodsIntegrationTest extends WorkforceIntegrationSupport {
    static final UUID UNIT = UUID.fromString("019a0000-0000-7000-8000-0000000000b1");
    static final UUID USER = UUID.fromString("019a0000-0000-7000-8000-0000000000aa");
    static final UUID[] ENTITIES = {UUID.fromString("019a0000-0000-7000-8000-0000000000f1"), UUID.fromString("019a0000-0000-7000-8000-0000000000f2"),
            UUID.fromString("019a0000-0000-7000-8000-0000000000f3")};
    static final Instant AT = Instant.parse("2026-11-02T10:00:00Z");
    static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Autowired
    OrgStructureCommandRepository commands;

    @BeforeEach
    void world() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.state_transitions");
        jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (:id, 'unit', 'ACTIVE')").param("id", UNIT).update();
        for (int i = 0; i < ENTITIES.length; i++) {
            jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (:id, :code, 'entity', 'ACTIVE') on conflict (id) do nothing")
                    .param("id", ENTITIES[i]).param("code", "ZP" + "ABC".charAt(i)).update();
        }
    }

    /** An ASSIGN_LEGAL_ENTITY command row (the periods reference it), dated apart from the others so the one-open-per-day key holds. */
    private OrgStructureCommandId command(LocalDate day, UUID entity) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,
                       created_at, created_by, updated_at, target_legal_entity_id)
                values (:id, 'ASSIGN_LEGAL_ENTITY', 'ORG_UNIT', :unit, :day, 'PENDING', now(), :user, now(), :entity)
                """).param("id", id).param("unit", UNIT).param("day", day).param("user", USER).param("entity", entity).update();
        return new OrgStructureCommandId(id);
    }

    private void assign(LocalDate day, UUID entity, OrgStructureCommandId command) {
        commands.attributeUnit(new OrgUnitId(UNIT), new LegalEntityId(entity), day, command, new AuditEntryId(UUID.randomUUID()), AT);
    }

    private List<Map<String, Object>> current() {
        return jdbc.sql("select legal_entity_id, effective_from_date, effective_to_date from organization_workforce.org_unit_legal_entities where org_unit_id = :unit and superseded_at is null"
                + " order by effective_from_date").param("unit", UNIT).query().listOfRows();
    }

    @Test
    @DisplayName("#910 rv910s1r3 M1: a period closed without a successor that ends after the day is seen by the back-dating check; a period that ends on or before the day is not")
    void aClosedPeriodAfterTheDayIsSeen() {
        assign(START, ENTITIES[0], command(START, ENTITIES[0]));
        jdbc.sql("update organization_workforce.org_unit_legal_entities set effective_to_date = :end, closed_at = now() where org_unit_id = :unit").param("end", LocalDate.of(2026, 12, 1))
                .param("unit", UNIT).update();

        assertThat(commands.hasAttributionAfter(new OrgUnitId(UNIT), LocalDate.of(2026, 11, 10))).isTrue();
        assertThat(commands.hasAttributionAfter(new OrgUnitId(UNIT), LocalDate.of(2026, 12, 1))).isFalse();
    }

    @Test
    @DisplayName("#910 rv910s1r3 M2: withdrawing an earlier assignment after a later one on the unit puts the earlier period back up to where its own period ended, no overlap")
    void withdrawingAnEarlierAssignmentKeepsTheLaterOne() {
        assign(START, ENTITIES[0], command(START, ENTITIES[0]));
        OrgStructureCommandId first = command(LocalDate.of(2026, 11, 10), ENTITIES[1]);
        assign(LocalDate.of(2026, 11, 10), ENTITIES[1], first);
        assign(LocalDate.of(2026, 11, 20), ENTITIES[2], command(LocalDate.of(2026, 11, 20), ENTITIES[2]));

        commands.voidAttribution(new OrgUnitId(UNIT), first, new AuditEntryId(UUID.randomUUID()), AT);

        List<Map<String, Object>> rows = current();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("legal_entity_id", ENTITIES[0]).containsEntry("effective_to_date", java.sql.Date.valueOf("2026-11-20"));
        assertThat(rows.get(1)).containsEntry("legal_entity_id", ENTITIES[2]).containsEntry("effective_from_date", java.sql.Date.valueOf("2026-11-20"));
    }
}
