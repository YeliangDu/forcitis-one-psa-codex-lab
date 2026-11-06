package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Scope;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import com.forcitis.one.organization.workforce.application.EmploymentRelationshipSeed;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1172 ruling (ADR-0026 decision 1 item 9): a personnel command is scoped by the department of the command, so people without a current primary post stay visible to their unit. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class PersonnelCommandScopeIT {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    CommandReadQueries queries;

    @Test
    @DisplayName("#1172 a future-dated HIRE with no post yet is seen by the target unit's scope and not by another unit's")
    void futureHireIsScopedByItsTargetUnit() {
        UUID mine = unit();
        UUID other = unit();
        UUID command = command(employee(), "HIRE", "PENDING", "2027-06-01", mine);
        assertThat(visible(mine)).contains(command);
        assertThat(visible(other)).doesNotContain(command);
    }

    @Test
    @DisplayName("#1172 an effective SEPARATE stays with the unit that was left, not with a unit the person never held")
    void effectiveSeparationStaysWithTheUnitLeft() {
        UUID left = unit();
        UUID other = unit();
        UUID employee = employee();
        primary(employee, left, "2026-01-01", "2026-07-01");
        UUID command = command(employee, "SEPARATE", "EFFECTIVE", "2026-07-01", null);
        assertThat(visible(left)).contains(command);
        assertThat(visible(other)).doesNotContain(command);
    }

    @Test
    @DisplayName("#1172 an effective SEPARATE is also seen by the unit of a concurrent assignment that was in force the day before")
    void effectiveSeparationIsSeenByTheConcurrentUnitToo() {
        UUID primary = unit();
        UUID concurrent = unit();
        UUID employee = employee();
        primary(employee, primary, "2026-01-01", "2026-07-01");
        assignment(employee, concurrent, "2026-02-01", "2026-07-01", "CONCURRENT");
        UUID command = command(employee, "SEPARATE", "EFFECTIVE", "2026-07-01", null);
        assertThat(visible(concurrent)).contains(command);
    }

    @Test
    @DisplayName("#1172 a TRANSFER stays with the unit being left, not the target unit")
    void transferStaysWithTheUnitBeingLeft() {
        UUID left = unit();
        UUID target = unit();
        UUID employee = employee();
        primary(employee, left, "2026-01-01", null);
        UUID command = command(employee, "TRANSFER", "PENDING", "2027-06-01", target);
        assertThat(visible(left)).contains(command);
        assertThat(visible(target)).doesNotContain(command);
    }

    @Test
    @DisplayName("#1172 a unit the person was corrected away from (superseded assignment version) grants no scope")
    void supersededAssignmentVersionGrantsNoScope() {
        UUID wrong = unit();
        UUID right = unit();
        UUID employee = employee();
        UUID root = assignment(employee, right, "2026-01-01", "2026-07-01", "PRIMARY");
        UUID correcting = command(employee, "CORRECTION", "EFFECTIVE", "2026-02-01", null);
        UUID version = ids.next();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employment_relationship_id, employee_id, effective_from_date, effective_to_date, closed_at, superseded_at,
                  superseding_command_id, org_unit_id, assignment_id, assignment_kind, revision_number)
                values (:id, :relationship, :employee, date '2026-01-01', date '2026-07-01', now(), now(), :command, :unit, :root, 'PRIMARY', 1)
                """).param("id", version).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, employee)).param("employee", employee).param("command", correcting).param("unit", wrong).param("root", root).update();
        UUID command = command(employee, "SEPARATE", "EFFECTIVE", "2026-07-01", null);
        assertThat(visible(right)).contains(command);
        assertThat(visible(wrong)).doesNotContain(command);
    }

    @Test
    @DisplayName("#1172 a person who has versions but no assignment version does not revive the snapshot post")
    void versionedPersonDoesNotReviveTheSnapshotPost() {
        UUID snapshot = unit();
        UUID employee = employee();
        jdbc.sql("insert into organization_workforce.assignments (employee_id, org_unit_id) values (:employee, :unit)").param("employee", employee).param("unit", snapshot).update();
        UUID command = command(employee, "REGULARIZE", "PENDING", "2027-06-01", null);
        assertThat(visible(snapshot)).as("a snapshot-only person is scoped by the snapshot post").contains(command);
        jdbc.sql("""
                insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id, employee_id, effective_from_date, employment_status, employment_phase_kind,
                  is_on_unpaid_leave, is_fee_earner, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (:id, :relationship, :employee, date '2026-01-01', 'EMPLOYED', 'REGULAR', false, false, 'UNKNOWN', 'MIGRATION', now(), now())
                """).param("id", ids.next()).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, employee)).param("employee", employee).update();
        assertThat(visible(snapshot)).as("once versioned, no assignment version means no scope").doesNotContain(command);
    }

    @Test
    @DisplayName("#1172 a command whose department cannot be resolved is out of scope")
    void unresolvableDepartmentIsOutOfScope() {
        UUID unit = unit();
        UUID command = command(employee(), "REGULARIZE", "PENDING", "2027-06-01", null);
        assertThat(visible(unit)).doesNotContain(command);
        assertThat(queries.personnel(new Scope(false, Set.of()), null, null, null, null, 200)).isEmpty();
    }

    private List<UUID> visible(UUID unit) {
        return queries.personnel(new Scope(false, Set.of(new OrgUnitId(unit))), null, null, null, null, 200).stream()
                .map(row -> row.personnelCommandId().value()).toList();
    }

    private UUID unit() {
        UUID unit = ids.next();
        jdbc.sql("insert into organization_workforce.org_units (id, code, name, status) values (:id, :code, 'Example unit', 'ACTIVE')")
                .param("id", unit).param("code", "SC-" + unit).update();
        return unit;
    }

    private void primary(UUID employee, UUID unit, String from, String to) {
        assignment(employee, unit, from, to, "PRIMARY");
    }

    private UUID assignment(UUID employee, UUID unit, String from, String to, String kind) {
        UUID root = ids.next();
        jdbc.sql("insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (:id, :employee, :kind, now(), now())")
                .param("id", root).param("employee", employee).param("kind", kind).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employment_relationship_id, employee_id, effective_from_date, effective_to_date, closed_at, org_unit_id, assignment_id, assignment_kind)
                values (:id, :relationship, :employee, cast(:from as date), cast(:to as date), case when cast(:to as date) is null then null else now() end, :unit, :root, :kind)
                """).param("id", ids.next()).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, employee)).param("employee", employee).param("from", from).param("to", to, java.sql.Types.VARCHAR).param("unit", unit).param("root", root).param("kind", kind).update();
        return root;
    }

    private UUID position() {
        UUID position = ids.next();
        jdbc.sql("insert into organization_workforce.positions (id, code, name, status) values (:id, :code, 'Example position', 'ACTIVE')")
                .param("id", position).param("code", "SC-" + position).update();
        return position;
    }

    private UUID employee() {
        UUID employee = ids.next();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, :code, 'Example', 'EMPLOYED')")
                .param("id", employee).param("code", "SC-" + employee).update();
        return employee;
    }

    private UUID command(UUID employee, String kind, String status, String effective, UUID targetUnit) {
        UUID id = ids.next();
        UUID position = kind.equals("HIRE") ? position() : null;
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, target_org_unit_id, target_position_id, is_target_fee_earner, target_employment_phase_kind, target_assignment_id, target_assignment_kind, applied_at, created_at, created_by, updated_at)
                values (:id, :relationship, :employee, :kind, cast(:effective as date), :status, :unit, :position, case when cast(:kind as text) = 'HIRE' then false end, case when cast(:kind as text) = 'HIRE' then 'PROBATION' end, :root, case when cast(:root as uuid) is null then null else 'PRIMARY' end, case when cast(:status as text) = 'EFFECTIVE' then now() end, now(), :by, now())
                """).param("id", id).param("relationship", EmploymentRelationshipSeed.relationship(jdbc, employee)).param("employee", employee).param("kind", kind).param("effective", effective).param("status", status)
                .param("unit", targetUnit, java.sql.Types.OTHER).param("position", position, java.sql.Types.OTHER).param("root", kind.equals("TRANSFER") ? jdbc.sql("select assignment_id from organization_workforce.employee_assignments where employee_id = :e and assignment_kind = 'PRIMARY'").param("e", employee).query(UUID.class).single() : null, java.sql.Types.OTHER).param("by", ids.next()).update();
        return id;
    }
}
