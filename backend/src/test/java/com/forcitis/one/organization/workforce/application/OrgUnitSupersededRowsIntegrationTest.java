package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** IMP-1030-ORG-F0 (#904): the readers and the period writer of the org unit tables see the current rows only; a superseded row of the same day is neither read nor closed. */
class OrgUnitSupersededRowsIntegrationTest extends WorkforceIntegrationSupport {
    static final UUID UNIT = UUID.fromString("019a0000-0000-7000-8000-0000000000b1");
    static final UUID OLD_PARENT = UUID.fromString("019a0000-0000-7000-8000-0000000000b2");
    static final UUID NEW_PARENT = UUID.fromString("019a0000-0000-7000-8000-0000000000b3");
    static final UUID LEGAL_ENTITY = UUID.fromString("019a0000-0000-7000-8000-0000000000e4");
    static final UUID COMMAND = UUID.fromString("019a0000-0000-7000-8000-0000000000c1");
    static final UUID SUPERSEDED_ROW = UUID.fromString("019a0000-0000-7000-8000-0000000000d1");
    static final UUID CURRENT_ROW = UUID.fromString("019a0000-0000-7000-8000-0000000000d2");

    @Autowired
    LegalEntityAttributionRepository attributions;

    @Autowired
    LegalEntityResolutionSources sources;

    @BeforeEach
    void aUnitAndACommand() {
        for (UUID id : new UUID[] {UNIT, OLD_PARENT, NEW_PARENT}) {
            jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (:id, 'unit', 'ACTIVE')").param("id", id).update();
        }
        jdbc.sql("insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, effective_date, status,"
                + " created_at, created_by, updated_at) values (:command, 'DISSOLVE', 'ORG_UNIT', :unit, date '2026-11-01', 'PENDING', now(), gen_random_uuid(), now())")
                .param("command", COMMAND).param("unit", UNIT).update();
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 the parent of a unit on a day is read from the current version; a superseded version of the same day is ignored")
    void parentIsReadFromTheCurrentVersion() {
        insertVersion(SUPERSEDED_ROW, OLD_PARENT, 0, true);
        insertVersion(CURRENT_ROW, NEW_PARENT, 1, false);

        assertThat(sources.parentUnit(new OrgUnitId(UNIT), LocalDate.of(2026, 6, 1))).map(OrgUnitId::value).contains(NEW_PARENT);
    }

    @Test
    @DisplayName("IMP-1030-ORG-F0 a superseded period of the same day is not read as a period and is not closed with the current one")
    void supersededPeriodsAreLeftAlone() {
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (:id, 'ZQU', 'entity', 'ACTIVE') on conflict (id) do nothing").param("id", LEGAL_ENTITY).update();
        jdbc.sql("insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id, version, event_count) values (gen_random_uuid(), :id, 1, 1)").param("id", UNIT).update();
        jdbc.sql("insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, effective_from_date, legal_entity_id, superseded_at,"
                + " superseding_org_structure_command_id) values (:row, :unit, date '2026-01-01', :entity, now(), :command)")
                .param("row", SUPERSEDED_ROW).param("unit", UNIT).param("entity", LEGAL_ENTITY).param("command", COMMAND).update();
        jdbc.sql("insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, effective_from_date, legal_entity_id) values (:row, :unit, date '2026-01-01', :entity)")
                .param("row", CURRENT_ROW).param("unit", UNIT).param("entity", LEGAL_ENTITY).update();

        LegalEntityAttribution attribution = attributions.find(AttributionSubject.of(new OrgUnitId(UNIT))).orElseThrow();
        assertThat(attribution.periods()).hasSize(1);
        attribution.end(LocalDate.of(2026, 6, 1));
        attributions.save(attribution);

        assertThat(jdbc.sql("select count(*) from organization_workforce.org_unit_legal_entities where effective_to_date = date '2026-06-01' and closed_at is not null"
                + " and org_unit_legal_entity_id = :row").param("row", CURRENT_ROW).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from organization_workforce.org_unit_legal_entities where effective_to_date is null and closed_at is null"
                + " and org_unit_legal_entity_id = :row").param("row", SUPERSEDED_ROW).query(Integer.class).single()).isEqualTo(1);
    }

    private void insertVersion(UUID id, UUID parent, int revision, boolean superseded) {
        jdbc.sql("insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, is_delivery_unit, parent_org_unit_id,"
                + " revision_number, superseded_at, superseding_org_structure_command_id) values (:id, :unit, date '2026-01-01', 'unit', 'ACTIVE', false, :parent, :revision,"
                + " " + (superseded ? "now()" : "null") + ", " + (superseded ? ":command" : "null") + ")")
                .param("id", id).param("unit", UNIT).param("parent", parent).param("revision", revision)
                .params(superseded ? java.util.Map.of("command", COMMAND) : java.util.Map.of()).update();
    }
}
