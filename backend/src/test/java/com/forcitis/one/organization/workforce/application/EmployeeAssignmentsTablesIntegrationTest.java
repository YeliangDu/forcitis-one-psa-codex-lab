package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #711: the assignment root table refuses rewrites, and a merge counts the people whose primary post is in the unit, not those who hold a concurrent post there. */
class EmployeeAssignmentsTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String ROOTS = "organization_workforce.employee_assignments";

    @Autowired
    OrgStructureCommandRepository structure;

    @Test
    @DisplayName("#711 a root is never updated, never truncated, and deleted only by the table owner")
    void rootsAreAppendOnly() {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AP-1");
        assertThatThrownBy(() -> jdbc.sql("update " + ROOTS + " set updated_at = now() where employee_id = :e").param("e", person.employee()).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate table " + ROOTS + " cascade").update()).hasMessageContaining("append-only");
        assertThat(jdbc.sql("select count(*) from " + ROOTS + " where employee_id = :e").param("e", person.employee()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("#711 a merge counts the people whose primary post is in the merged unit; the holder of a concurrent post there is not moved by it")
    void aMergeCountsPrimaryHoldersOnly() {
        PersonnelSeedSupport.Person resident = PersonnelSeedSupport.employed(jdbc, clock, "AP-2");
        PersonnelSeedSupport.Person visitor = PersonnelSeedSupport.employed(jdbc, clock, "AP-3");
        UUID root = Uuids.timeOrdered(clock);
        jdbc.sql("insert into " + ROOTS + " (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (:root, :e, 'CONCURRENT', now(), now())")
                .param("root", root).param("e", visitor.employee()).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                  org_unit_id, revision_number, change_channel_kind, created_at, updated_at)
                values (:id, :e, :root, 'CONCURRENT', :since, :unit, 1, 'MIGRATION', now(), now())""")
                .param("id", Uuids.timeOrdered(clock)).param("e", visitor.employee()).param("root", root).param("since", PersonnelSeedSupport.SINCE)
                .param("unit", resident.orgUnit()).update();

        assertThat(structure.employedIn(new OrgUnitId(resident.orgUnit()), LocalDate.of(2026, 11, 3))).containsExactly(new EmployeeId(resident.employee()));
    }
}
