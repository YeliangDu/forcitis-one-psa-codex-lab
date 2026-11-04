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
 * #1147 and #910 review rv910s1r3: back-dated assignments void and append covered periods, preserve their sources and restore fully covered facts on withdrawal;
 * withdrawing an ordinary earlier assignment after a later one keeps the later one's start.
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
        return jdbc.sql("""
                select org_unit_legal_entity_id, legal_entity_id, effective_from_date, effective_to_date, closed_at, change_channel_kind,
                       change_org_structure_command_id, version_audit_entry_id, corrected_from_version_id
                  from organization_workforce.org_unit_legal_entities where org_unit_id = :unit and superseded_at is null
                 order by effective_from_date, org_unit_legal_entity_id
                """).param("unit", UNIT).query().listOfRows();
    }

    @Test
    @DisplayName("#1147: an assignment inside a period closed without a successor voids it, appends its front with the original source and ends the new period where the old one ended (one-design ruling a)")
    void aClosedPeriodWithoutASuccessorIsSplit() {
        assign(START, ENTITIES[0], command(START, ENTITIES[0]));
        Map<String, Object> original = current().getFirst();
        jdbc.sql("update organization_workforce.org_unit_legal_entities set effective_to_date = :end, closed_at = now() where org_unit_id = :unit").param("end", LocalDate.of(2026, 12, 1))
                .param("unit", UNIT).update();
        LocalDate back = LocalDate.of(2026, 11, 10);
        OrgStructureCommandId backdated = command(back, ENTITIES[1]);

        assign(back, ENTITIES[1], backdated);

        List<Map<String, Object>> rows = current();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("legal_entity_id", ENTITIES[0]).containsEntry("effective_from_date", java.sql.Date.valueOf(START))
                .containsEntry("effective_to_date", java.sql.Date.valueOf(back)).containsEntry("corrected_from_version_id", original.get("org_unit_legal_entity_id"))
                .containsEntry("change_channel_kind", original.get("change_channel_kind"))
                .containsEntry("change_org_structure_command_id", original.get("change_org_structure_command_id"))
                .containsEntry("version_audit_entry_id", original.get("version_audit_entry_id"));
        assertThat(rows.get(0).get("closed_at")).isNotNull();
        assertThat(rows.get(1)).containsEntry("legal_entity_id", ENTITIES[1]).containsEntry("effective_from_date", java.sql.Date.valueOf(back))
                .containsEntry("effective_to_date", java.sql.Date.valueOf("2026-12-01")).containsEntry("change_org_structure_command_id", backdated.value());
        assertThat(rows.get(1).get("closed_at")).isNotNull();
        Map<String, Object> voided = jdbc.sql("""
                select effective_to_date, superseded_at, superseding_org_structure_command_id
                  from organization_workforce.org_unit_legal_entities where org_unit_legal_entity_id = :id
                """).param("id", original.get("org_unit_legal_entity_id")).query().singleRow();
        assertThat(voided).containsEntry("effective_to_date", java.sql.Date.valueOf("2026-12-01")).containsEntry("superseding_org_structure_command_id", backdated.value());
        assertThat(voided.get("superseded_at")).isNotNull();
    }

    @Test
    @DisplayName("#1147: withdrawing a same-day replacement restores a fully covered command row with its original range and source and keeps the later row")
    void withdrawingASameDayReplacementRestoresTheCoveredCommandRow() {
        OrgStructureCommandId originalCommand = command(START, ENTITIES[0]);
        assign(START, ENTITIES[0], originalCommand);
        LocalDate later = START.plusDays(30);
        assign(later, ENTITIES[2], command(later, ENTITIES[2]));
        Map<String, Object> original = current().getFirst();
        Map<String, Object> successor = current().getLast();
        jdbc.sql("update organization_workforce.org_structure_commands set status = 'EFFECTIVE', applied_at = :at where org_structure_command_id = :id")
                .param("at", AT.atOffset(java.time.ZoneOffset.UTC)).param("id", originalCommand.value()).update();
        OrgStructureCommandId replacement = command(START, ENTITIES[1]);

        assign(START, ENTITIES[1], replacement);
        assertThat(current().getFirst()).containsEntry("legal_entity_id", ENTITIES[1]).containsEntry("effective_to_date", java.sql.Date.valueOf(later));
        commands.voidAttribution(new OrgUnitId(UNIT), replacement, new AuditEntryId(UUID.randomUUID()), AT);

        List<Map<String, Object>> rows = current();
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("legal_entity_id", ENTITIES[0]).containsEntry("effective_from_date", java.sql.Date.valueOf(START))
                .containsEntry("effective_to_date", java.sql.Date.valueOf(later)).containsEntry("change_channel_kind", original.get("change_channel_kind"))
                .containsEntry("change_org_structure_command_id", original.get("change_org_structure_command_id"))
                .containsEntry("version_audit_entry_id", original.get("version_audit_entry_id"))
                .containsEntry("corrected_from_version_id", original.get("org_unit_legal_entity_id"));
        assertThat(rows.getFirst().get("closed_at")).isNotNull();
        assertThat(rows.getLast()).isEqualTo(successor);
    }

    @Test
    @DisplayName("#1147: withdrawing a same-day replacement of an open migrated row restores its open range and nullable original source")
    void withdrawingASameDayReplacementRestoresTheCoveredMigratedRow() {
        UUID original = UUID.randomUUID();
        jdbc.sql("insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id, created_at, updated_at) values (:id, :unit, now(), now())")
                .param("id", UUID.randomUUID()).param("unit", UNIT).update();
        jdbc.sql("""
                insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, legal_entity_id, effective_from_date,
                       change_channel_kind, created_at, updated_at)
                values (:id, :unit, :entity, :from, 'MIGRATION', :at, :at)
                """).param("id", original).param("unit", UNIT).param("entity", ENTITIES[0]).param("from", START).param("at", AT.atOffset(java.time.ZoneOffset.UTC)).update();
        OrgStructureCommandId replacement = command(START, ENTITIES[1]);

        assign(START, ENTITIES[1], replacement);
        commands.voidAttribution(new OrgUnitId(UNIT), replacement, new AuditEntryId(UUID.randomUUID()), AT);

        assertThat(current()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("legal_entity_id", ENTITIES[0]).containsEntry("effective_from_date", java.sql.Date.valueOf(START)).containsEntry("effective_to_date", null)
                .containsEntry("closed_at", null).containsEntry("change_channel_kind", "MIGRATION").containsEntry("change_org_structure_command_id", null)
                .containsEntry("version_audit_entry_id", null).containsEntry("corrected_from_version_id", original));
    }

    @Test
    @DisplayName("#1147 ruling b: withdrawing a back-dated assignment that split a period brings the period back whole with its original source, and the later row stays")
    void withdrawingABackDatedAssignmentRestoresTheSplitPeriod() {
        OrgStructureCommandId originalCommand = command(START, ENTITIES[0]);
        assign(START, ENTITIES[0], originalCommand);
        LocalDate later = START.plusDays(30);
        assign(later, ENTITIES[2], command(later, ENTITIES[2]));
        Map<String, Object> original = current().getFirst();
        Map<String, Object> successor = current().getLast();
        LocalDate back = START.plusDays(10);
        OrgStructureCommandId backdated = command(back, ENTITIES[1]);

        assign(back, ENTITIES[1], backdated);
        assertThat(current()).hasSize(3);
        commands.voidAttribution(new OrgUnitId(UNIT), backdated, new AuditEntryId(UUID.randomUUID()), AT);

        List<Map<String, Object>> rows = current();
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("legal_entity_id", ENTITIES[0]).containsEntry("effective_from_date", java.sql.Date.valueOf(START))
                .containsEntry("effective_to_date", java.sql.Date.valueOf(later)).containsEntry("change_channel_kind", original.get("change_channel_kind"))
                .containsEntry("change_org_structure_command_id", originalCommand.value()).containsEntry("version_audit_entry_id", original.get("version_audit_entry_id"));
        assertThat(rows.getLast()).isEqualTo(successor);
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
