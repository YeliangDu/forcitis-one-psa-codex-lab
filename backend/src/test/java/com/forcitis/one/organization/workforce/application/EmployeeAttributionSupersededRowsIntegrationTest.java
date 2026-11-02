package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** IMP-1030-FC-A (#892): the attribution repository reads and closes the current employee period only; a superseded row of the same day is neither a period nor touched. */
class EmployeeAttributionSupersededRowsIntegrationTest extends WorkforceIntegrationSupport {
    static final UUID EMPLOYEE = UUID.fromString("019a0000-0000-7000-8000-000000000001");
    static final UUID LEGAL_ENTITY = UUID.fromString("019a0000-0000-7000-8000-0000000000e2");
    static final UUID COMMAND = UUID.fromString("019a0000-0000-7000-8000-0000000000c1");
    static final UUID SUPERSEDED_ROW = UUID.fromString("019a0000-0000-7000-8000-0000000000d1");
    static final UUID CURRENT_ROW = UUID.fromString("019a0000-0000-7000-8000-0000000000d2");

    @Autowired
    LegalEntityAttributionRepository attributions;

    @Test
    @DisplayName("IMP-1030-FC-A a superseded period of the same day is not read as a period and is not closed with the current one")
    void supersededRowsAreLeftAlone() {
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, 'E-01', 'name', 'EMPLOYED')").param("id", EMPLOYEE).update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (:id, 'ZQA', 'entity', 'ACTIVE') on conflict (id) do nothing").param("id", LEGAL_ENTITY).update();
        jdbc.sql("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id, version, event_count) values (gen_random_uuid(), :id, 1, 1)").param("id", EMPLOYEE).update();
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)"
                + " values (:command, :id, 'REGULARIZE', date '2026-11-01', 'PENDING', now(), gen_random_uuid(), now())").param("command", COMMAND).param("id", EMPLOYEE).update();
        jdbc.sql("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id, superseded_at, superseding_command_id)"
                + " values (:row, :id, date '2026-01-01', :entity, now(), :command)").param("row", SUPERSEDED_ROW).param("id", EMPLOYEE).param("entity", LEGAL_ENTITY).param("command", COMMAND).update();
        jdbc.sql("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id)"
                + " values (:row, :id, date '2026-01-01', :entity)").param("row", CURRENT_ROW).param("id", EMPLOYEE).param("entity", LEGAL_ENTITY).update();

        LegalEntityAttribution attribution = attributions.find(AttributionSubject.of(new EmployeeId(EMPLOYEE))).orElseThrow();
        assertThat(attribution.periods()).hasSize(1);
        attribution.end(LocalDate.of(2026, 6, 1));
        attributions.save(attribution);

        assertThat(jdbc.sql("select count(*) from organization_workforce.employee_legal_entities where effective_to_date = date '2026-06-01' and closed_at is not null and employee_legal_entity_id = :row")
                .param("row", CURRENT_ROW).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from organization_workforce.employee_legal_entities where effective_to_date is null and closed_at is null and employee_legal_entity_id = :row")
                .param("row", SUPERSEDED_ROW).query(Integer.class).single()).isEqualTo(1);
    }
}
