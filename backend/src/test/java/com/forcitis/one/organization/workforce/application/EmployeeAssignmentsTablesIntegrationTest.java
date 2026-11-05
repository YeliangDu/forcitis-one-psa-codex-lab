package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.AssignmentKind;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #711: the assignment root table refuses rewrites; #1040: a merge moves the concurrent posts of a unit too, and the people whose cost legal entity it can change are those with a primary post there. */
class EmployeeAssignmentsTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String ROOTS = "organization_workforce.employee_assignments";

    @Autowired
    OrgStructureCommandRepository structure;

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("#711 a role that does not own the table cannot delete a root either")
    void aNonOwnerCannotDeleteARoot() throws SQLException {
        PersonnelSeedSupport.employed(jdbc, clock, "AP-4");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'assignment_reader') then create role assignment_reader; end if; end $$");
            statement.execute("grant usage on schema organization_workforce to assignment_reader");
            statement.execute("grant select, delete on " + ROOTS + " to assignment_reader");
            statement.execute("set role assignment_reader");
            try {
                assertThatThrownBy(() -> statement.execute("delete from " + ROOTS)).hasMessageContaining("append-only");
            } finally {
                statement.execute("reset role");
            }
        }
        assertThat(jdbc.sql("select count(*) from " + ROOTS).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("#711 a root is never updated, never truncated, and deleted only by the table owner")
    void rootsAreAppendOnly() {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AP-1");
        assertThatThrownBy(() -> jdbc.sql("update " + ROOTS + " set updated_at = now() where employee_id = :e").param("e", person.employee()).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate table " + ROOTS + " cascade").update()).hasMessageContaining("append-only");
        assertThat(jdbc.sql("select count(*) from " + ROOTS + " where employee_id = :e").param("e", person.employee()).query(Integer.class).single()).isEqualTo(1);
    }

    private PersonnelSeedSupport.Person visitorWithConcurrentPostIn(PersonnelSeedSupport.Person resident, String code) {
        PersonnelSeedSupport.Person visitor = PersonnelSeedSupport.employed(jdbc, clock, code);
        UUID root = Uuids.timeOrdered(clock);
        jdbc.sql("insert into " + ROOTS + " (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (:root, :e, 'CONCURRENT', now(), now())")
                .param("root", root).param("e", visitor.employee()).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employment_relationship_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                  org_unit_id, revision_number, change_channel_kind, created_at, updated_at)
                values (:id, :relationship, :e, :root, 'CONCURRENT', :since, :unit, 1, 'MIGRATION', now(), now())""")
                .param("id", Uuids.timeOrdered(clock)).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, visitor.employee())).param("e", visitor.employee()).param("root", root).param("since", PersonnelSeedSupport.SINCE)
                .param("unit", resident.orgUnit()).update();
        return visitor;
    }

    @Test
    @DisplayName("#1040 a merge moves every post in the merged unit, primary and concurrent; only the primary holders are the people whose cost legal entity it can change")
    void aMergeMovesEveryPostButOnlyPrimaryHoldersCanChangeEntity() {
        PersonnelSeedSupport.Person resident = PersonnelSeedSupport.employed(jdbc, clock, "AP-2");
        PersonnelSeedSupport.Person visitor = visitorWithConcurrentPostIn(resident, "AP-3");
        OrgUnitId unit = new OrgUnitId(resident.orgUnit());
        LocalDate day = LocalDate.of(2026, 11, 3);

        assertThat(structure.employedIn(unit, day)).containsExactly(new EmployeeId(resident.employee()));
        assertThat(structure.postsIn(unit, day)).extracting(post -> post.employee(), post -> post.kind())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(new EmployeeId(resident.employee()), AssignmentKind.PRIMARY),
                        org.assertj.core.groups.Tuple.tuple(new EmployeeId(visitor.employee()), AssignmentKind.CONCURRENT));
    }

    @Test
    @DisplayName("#1040 a merge in progress holds the person who has only a concurrent post in its unit, so a command about them locks the unit first and counts them")
    void aMergeInProgressHoldsAConcurrentOnlyPerson() {
        PersonnelSeedSupport.Person resident = PersonnelSeedSupport.employed(jdbc, clock, "AP-6");
        PersonnelSeedSupport.Person visitor = visitorWithConcurrentPostIn(resident, "AP-7");
        UUID merge = Uuids.timeOrdered(clock);
        jdbc.sql("""
                insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_org_unit_id, merge_into_org_unit_id, effective_date,
                  status, affected_employee_count, transferred_employee_count, created_at, created_by, updated_at)
                values (:id, 'MERGE', 'ORG_UNIT', :unit, :into, date '2026-11-03', 'IN_PROGRESS', 2, 0, now(), gen_random_uuid(), now())""")
                .param("id", merge).param("unit", resident.orgUnit()).param("into", PersonnelSeedSupport.orgUnit(jdbc, clock)).update();

        assertThat(structure.mergesInProgressOf(new EmployeeId(visitor.employee()))).containsExactly(new OrgStructureCommandId(merge));
    }
}
