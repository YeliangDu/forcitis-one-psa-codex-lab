package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.Row;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.Visible;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class JdbcEmployeeRosterQueriesIT {
    private static final LocalDate TODAY = com.forcitis.one.BusinessDay.today();
    private static final LocalDate SEPARATED_ON = LocalDate.of(2026, 10, 1);

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
    BusinessZone zone;

    @Autowired
    IdGenerator ids;

    @Test
    @DisplayName("#1171 a separated person stays in the roster of the unit of their last posts after the separation closed every post")
    void aSeparatedPersonBelongsToTheUnitOfTheirLastPosts() {
        OrgUnitId unitA = unit("RQ-A");
        OrgUnitId unitB = unit("RQ-B");
        UUID position = ids.next();
        jdbc.sql("insert into organization_workforce.positions (id, code, name, sort_order, status) values (:id, 'RQ-P', 'post', 1, 'ACTIVE')").param("id", position).update();
        EmployeeId gone = person("RQ-1", unitA, position, "SEPARATED", SEPARATED_ON);
        EmployeeId stays = person("RQ-2", unitB, position, "EMPLOYED", LocalDate.of(2025, 1, 1));
        JdbcEmployeeRosterQueries queries = new JdbcEmployeeRosterQueries(jdbc, Clock.fixed(TODAY.atTime(4, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), zone);

        Visible onlyA = new Visible(false, Set.of(unitA));
        assertThat(codes(queries.list(onlyA, null, EmploymentStatus.SEPARATED, null, 10))).containsExactly("RQ-1");
        assertThat(codes(queries.list(new Visible(false, Set.of(unitB)), null, EmploymentStatus.SEPARATED, null, 10))).isEmpty();
        assertThat(codes(queries.list(new Visible(true, Set.of()), unitA, EmploymentStatus.SEPARATED, null, 10))).containsExactly("RQ-1");
        Row row = queries.find(onlyA, gone).orElseThrow();
        assertThat(row.assignments()).extracting(a -> a.orgUnitId()).containsExactly(unitA);
        assertThat(queries.find(onlyA, stays)).isEmpty();
    }

    @Test
    @DisplayName("#1171 a superseded post or employee version never widens the scope or changes the status")
    void supersededRowsAreIgnored() {
        OrgUnitId current = unit("RS-C");
        OrgUnitId withdrawn = unit("RS-D");
        UUID position = ids.next();
        jdbc.sql("insert into organization_workforce.positions (id, code, name, sort_order, status) values (:id, 'RS-P', 'post', 1, 'ACTIVE')").param("id", position).update();
        EmployeeId person = person("RS-1", current, position, "EMPLOYED", LocalDate.of(2025, 1, 1));
        UUID command = ids.next();
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at, applied_at)
                values (:id, :employee, 'CORRECTION', date '2025-06-01', 'EFFECTIVE', now(), :actor, now(), now())""")
                .param("id", command).param("employee", person.value()).param("actor", ids.next()).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                    revision_number, org_unit_id, position_id, change_channel_kind, superseded_at, superseding_command_id, created_at, updated_at)
                select :version, employee_id, assignment_id, 'PRIMARY', date '2025-06-01', 1, :unit, :position, 'MIGRATION', now(), :command, now(), now()
                  from organization_workforce.assignment_versions where employee_id = :id""")
                .param("command", command).param("version", ids.next()).param("id", person.value()).param("unit", withdrawn.value()).param("position", position).update();
        jdbc.sql("""
                insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                    is_on_unpaid_leave, is_fee_earner, change_channel_kind, superseded_at, superseding_command_id, created_at, updated_at)
                values (:version, :id, date '2025-06-01', 1, 'SEPARATED', false, false, 'MIGRATION', now(), :command, now(), now())""")
                .param("command", command).param("version", ids.next()).param("id", person.value()).update();
        UUID positionCommand = ids.next();
        jdbc.sql("""
                insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_position_id, effective_date, status,
                    applied_at, created_at, created_by, updated_at)
                values (:id, 'DISABLE', 'POSITION', :position, date '2026-01-01', 'EFFECTIVE', now(), now(), :actor, now())""")
                .param("id", positionCommand).param("position", position).param("actor", ids.next()).update();
        jdbc.sql("""
                insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, revision_number, code, name, status,
                    change_channel_kind, superseded_at, superseding_org_structure_command_id)
                values (:id, :position, date '2025-06-01', 1, 'RS-P', 'withdrawn name', 'ACTIVE', 'MIGRATION', now(), :command)""")
                .param("id", ids.next()).param("position", position).param("command", positionCommand).update();
        JdbcEmployeeRosterQueries queries = new JdbcEmployeeRosterQueries(jdbc, Clock.fixed(TODAY.atTime(4, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), zone);

        assertThat(queries.find(new Visible(true, Set.of()), person).orElseThrow().assignments()).extracting(a -> a.positionName()).containsExactly("post");
        assertThat(codes(queries.list(new Visible(false, Set.of(withdrawn)), null, null, null, 10))).isEmpty();
        assertThat(queries.find(new Visible(false, Set.of(withdrawn)), person)).isEmpty();
        assertThat(codes(queries.list(new Visible(false, Set.of(current)), null, null, null, 10))).containsExactly("RS-1");
        assertThat(codes(queries.list(new Visible(true, Set.of()), null, EmploymentStatus.SEPARATED, null, 10))).doesNotContain("RS-1");
    }

    @Test
    @DisplayName("#1171 a cursor row that left the result between two pages is an invalid cursor, not the end of the list")
    void aCursorRowThatLeftTheResultIsRejected() {
        OrgUnitId unit = unit("RX-A");
        UUID position = ids.next();
        jdbc.sql("insert into organization_workforce.positions (id, code, name, sort_order, status) values (:id, 'RX-P', 'post', 1, 'ACTIVE')").param("id", position).update();
        EmployeeId first = person("RX-1", unit, position, "SEPARATED", SEPARATED_ON);
        person("RX-2", unit, position, "EMPLOYED", LocalDate.of(2025, 1, 1));
        EmployeeId last = person("RX-3", unit, position, "EMPLOYED", LocalDate.of(2025, 1, 1));
        JdbcEmployeeRosterQueries queries = new JdbcEmployeeRosterQueries(jdbc, Clock.fixed(TODAY.atTime(4, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), zone);
        Visible all = new Visible(true, Set.of());

        assertThat(codes(queries.list(all, unit, null, first, 10))).containsExactly("RX-2", "RX-3");
        assertThat(queries.list(all, unit, null, last, 10)).as("the last row as cursor is the true end").isEmpty();
        assertThat(queries.list(new Visible(false, Set.of(unit)), null, null, first, 10)).hasSize(2);
        assertThatThrownBy(() -> queries.list(new Visible(false, Set.of(unit("RX-B"))), null, null, first, 10)).isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> queries.list(all, unit, EmploymentStatus.EMPLOYED, first, 10)).isInstanceOf(InvalidCursorException.class);
    }

    private static List<String> codes(List<Row> rows) {
        return rows.stream().map(Row::employeeCode).toList();
    }

    private OrgUnitId unit(String code) {
        OrgUnitId unit = new OrgUnitId(ids.next());
        jdbc.sql("insert into organization_workforce.org_units (id, code, name, status, sort_order) values (:id, :code, :code, 'ACTIVE', 1)")
                .param("id", unit.value()).param("code", code).update();
        return unit;
    }

    /** A separated person has every post closed on the separation day, exactly as the separation command writes it. */
    private EmployeeId person(String code, OrgUnitId unit, UUID position, String status, LocalDate from) {
        UUID id = ids.next();
        boolean employed = status.equals("EMPLOYED");
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, employment_status, is_fee_earner)
                values (:id, :code, :code, :status, false)""").param("id", id).param("code", code).param("status", status).update();
        jdbc.sql("""
                insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                    employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (:version, :id, :from, 0, :status, :phase, false, false, :start, :basis, 'MIGRATION', now(), now())""")
                .param("version", ids.next()).param("id", id).param("from", from).param("status", status).param("phase", employed ? "REGULAR" : null)
                .param("start", employed ? from : null).param("basis", employed ? "DECLARED" : null).update();
        UUID root = ids.next();
        jdbc.sql("insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (:root, :id, 'PRIMARY', now(), now())")
                .param("root", root).param("id", id).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                    effective_to_date, closed_at, revision_number, org_unit_id, position_id, change_channel_kind, created_at, updated_at)
                values (:version, :id, :root, 'PRIMARY', :since, :until, case when cast(:until as date) is null then null else now() end, 0, :unit, :position, 'MIGRATION', now(), now())""")
                .param("version", ids.next()).param("id", id).param("root", root).param("since", LocalDate.of(2025, 1, 1))
                .param("until", employed ? null : from).param("unit", unit.value()).param("position", position).update();
        return new EmployeeId(id);
    }
}
