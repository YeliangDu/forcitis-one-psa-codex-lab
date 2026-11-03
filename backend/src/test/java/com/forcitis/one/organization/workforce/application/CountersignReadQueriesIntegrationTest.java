package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries.BasisEntry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("#910 S3 the page of a command's basis lines is keyset-ordered and its cursor belongs to that command")
class CountersignReadQueriesIntegrationTest extends WorkforceIntegrationSupport {
    private static final String BASES = "organization_workforce.attribution_countersign_bases";
    private static final String OSC = "organization_workforce.org_structure_commands";
    private static final String ENTITY_A = "'019a0000-0000-7000-8000-00000000c1f1'";
    private static final String ENTITY_B = "'019a0000-0000-7000-8000-00000000c1f2'";
    private static final String UNIT = "'019a0000-0000-7000-8000-00000000c1b1'";
    private static final String OTHER_UNIT = "'019a0000-0000-7000-8000-00000000c1b2'";
    private static final String COMMAND_A = "019a0000-0000-7000-8000-00000000c1c1";
    private static final String COMMAND_B = "019a0000-0000-7000-8000-00000000c1c2";

    @Autowired
    CountersignReadQueries queries;

    @BeforeEach
    void rows() {
        jdbc.sql("delete from " + BASES).update();
        jdbc.sql("delete from " + OSC + " where target_org_unit_id = " + UNIT).update();
        for (String id : new String[] {UNIT, OTHER_UNIT}) {
            jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (" + id + ", 'unit', 'ACTIVE') on conflict do nothing").update();
        }
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY_A + ", 'QZFGA', 'entity a', 'ACTIVE') on conflict do nothing").update();
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY_B + ", 'QZFGB', 'entity b', 'ACTIVE') on conflict do nothing").update();
        for (String command : new String[] {COMMAND_A, COMMAND_B}) {
            jdbc.sql("insert into " + OSC + " (org_structure_command_id, command_kind, target_kind, target_org_unit_id, merge_into_org_unit_id, effective_date, status, created_at, created_by, updated_at)"
                    + " values ('" + command + "', 'MERGE', 'ORG_UNIT', " + UNIT + ", " + OTHER_UNIT + ", date '2026-12-0" + (command.equals(COMMAND_A) ? 1 : 2) + "', 'PENDING', now(), gen_random_uuid(), now())").update();
            for (int i = 0; i < 2; i++) {
                String employee = "'019a0000-0000-7000-8000-00000000c10" + i + "'";
                jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + employee + ", 'FCG-" + i + "', 'name', 'EMPLOYED') on conflict do nothing").update();
                jdbc.sql("insert into " + BASES + " (attribution_countersign_basis_id, org_structure_command_id, employee_id, previous_legal_entity_id, new_legal_entity_id, created_at, updated_at) values (gen_random_uuid(), '"
                        + command + "', " + employee + ", " + ENTITY_A + ", " + ENTITY_B + ", now(), now())").update();
            }
        }
    }

    private static OrgStructureCommandId command(String id) {
        return new OrgStructureCommandId(UUID.fromString(id));
    }

    @Test
    void theCursorOfOneCommandPagesThatCommandOnly() {
        List<BasisEntry> first = queries.basisPage(command(COMMAND_A), null, 1);
        assertThat(first).hasSize(1);
        UUID cursor = first.get(0).rowKey();

        List<BasisEntry> next = queries.basisPage(command(COMMAND_A), cursor, 10);
        assertThat(next).hasSize(1);
        assertThat(next.get(0).rowKey()).isNotEqualTo(cursor);
        assertThatThrownBy(() -> queries.basisPage(command(COMMAND_B), cursor, 10)).isInstanceOf(InvalidCursorException.class);
    }
}
