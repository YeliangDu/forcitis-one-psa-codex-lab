package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.Employee;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.RegisteredEmployee;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
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

    @Autowired
    OrgSnapshotRepository snapshots;

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
    @DisplayName("#711 a snapshot write for a person with a primary and a concurrent post closes the primary post only")
    void aSnapshotWriteClosesThePrimaryPostOnly() {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AP-5");
        UUID root = Uuids.timeOrdered(clock);
        UUID elsewhere = PersonnelSeedSupport.orgUnit(jdbc, clock);
        UUID other = PersonnelSeedSupport.orgUnit(jdbc, clock);
        jdbc.sql("insert into " + ROOTS + " (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (:root, :e, 'CONCURRENT', now(), now())")
                .param("root", root).param("e", person.employee()).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                  org_unit_id, revision_number, change_channel_kind, created_at, updated_at)
                values (:id, :e, :root, 'CONCURRENT', :since, :unit, 1, 'MIGRATION', now(), now())""")
                .param("id", Uuids.timeOrdered(clock)).param("e", person.employee()).param("root", root).param("since", PersonnelSeedSupport.SINCE).param("unit", elsewhere).update();
        EmployeeId id = new EmployeeId(person.employee());
        Employee employee = new Employee(id, "AP-5", "Synthetic person", null, null, null, null, EmploymentStatus.EMPLOYED, false);

        snapshots.saveRegisteredEmployee(RegisteredEmployee.onFirstLogin(new StaffEmployee(employee, new Assignment(id, new OrgUnitId(other), null, null)), 0, 0),
                java.time.Instant.parse("2026-11-03T02:00:00Z"));

        assertThat(jdbc.sql("select count(*) from organization_workforce.assignment_versions where assignment_id = :root and effective_to_date is null and superseded_at is null")
                .param("root", root).query(Integer.class).single()).as("the concurrent post stays open").isEqualTo(1);
        assertThat(jdbc.sql("select org_unit_id from organization_workforce.assignment_versions where employee_id = :e and assignment_kind = 'PRIMARY' and effective_to_date is null and superseded_at is null")
                .param("e", person.employee()).query(UUID.class).single()).as("the primary post moved").isEqualTo(other);
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
