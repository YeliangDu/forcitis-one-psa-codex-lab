package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.InitialVersionPlan;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolution;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * #1372 review: the employer the initial-version migration picks (Java twin of the SQL backfill) is the one LegalEntityResolution gives, over a sample with
 * attribution inherited through a parent unit, an end date moved by a correction, and a person nobody attributes.
 */
class InitialVersionEmployerResolutionIT {

    static final TestDatabase postgres = TestPostgres.database(InitialVersionEmployerResolutionIT.class, TestPostgres.MIGRATED);

    private static final LocalDate FIRST_DAY = LocalDate.parse("2026-02-01");
    private final DriverManagerDataSource source = new DriverManagerDataSource(postgres.url(), postgres.username(), postgres.password());
    private final JdbcClient jdbc = JdbcClient.create(source);
    private final JdbcInitialVersionMigrationRepository repository = new JdbcInitialVersionMigrationRepository(jdbc, UUID::randomUUID, new BusinessZone(ZoneId.of("Asia/Shanghai")));
    private final JdbcLegalEntityResolutionSources sources = new JdbcLegalEntityResolutionSources(jdbc);
    private int counter;

    private void execute(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password()); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private UUID id() {
        return Uuids.timeOrdered(Instant.parse("2026-11-02T02:00:00Z").plusMillis(counter++));
    }

    private UUID entity(String code) throws SQLException {
        UUID id = id();
        execute("insert into organization_workforce.legal_entities (id, code, name, status) values ('" + id + "', '" + code + "', 'entity', 'ACTIVE')");
        return id;
    }

    private UUID unit(String code, UUID parent) throws SQLException {
        UUID id = id();
        String parentSql = parent == null ? "null" : "'" + parent + "'";
        execute("insert into organization_workforce.org_units (id, name, status, parent_org_unit_id) values ('" + id + "', '" + code + "', 'ACTIVE', " + parentSql + ")");
        execute("insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, parent_org_unit_id, status, is_delivery_unit)"
                + " values ('" + id() + "', '" + id + "', date '2025-01-01', '" + code + "', " + parentSql + ", 'ACTIVE', false)");
        return id;
    }

    private UUID person(UUID unit) throws SQLException {
        UUID id = id();
        execute("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values ('" + id + "', 'E-" + id + "', 'name', 'EMPLOYED')");
        execute("insert into organization_workforce.assignments (employee_id, org_unit_id) values ('" + id + "', '" + unit + "')");
        return id;
    }

    private void unitAttribution(UUID unit, UUID entity, String to) throws SQLException {
        execute("insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id) values ('" + id() + "', '" + unit + "')");
        execute("insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, effective_from_date, effective_to_date, closed_at, legal_entity_id)"
                + " values ('" + id() + "', '" + unit + "', date '2025-01-01', " + to + ", now(), '" + entity + "')");
    }

    private void employeeAttribution(UUID person, UUID entity) throws SQLException {
        execute("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values ('" + id() + "', '" + person + "')");
        execute("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id)"
                + " values ('" + id() + "', '" + person + "', date '2025-01-01', '" + entity + "')");
    }

    private UUID migratedEmployer(UUID person) {
        repository.insertEmployeeVersion(new EmployeeId(person), new InitialVersionPlan.Row(FIRST_DAY, null, true, false), Instant.parse("2026-11-05T02:00:00Z"));
        return jdbc.sql("select employer_legal_entity_id from organization_workforce.employment_relationships where employee_id = :p").param("p", person).query(UUID.class).single();
    }

    private java.util.Optional<UUID> resolved(UUID person, UUID unit) {
        return LegalEntityResolution.resolve(sources, new EmployeeId(person), FIRST_DAY, null, new OrgUnitId(unit)).map(e -> e.value());
    }

    @Test
    @DisplayName("#1372 评审必改6：迁入取的用人单位与 LegalEntityResolution 一致——经父部门继承、更正后的终止日、员工自己的归属优先、无归属者被跳过")
    void theMigrationEmployerIsTheResolvedOne() throws SQLException {
        UUID entityOne = entity("QAA");
        UUID entityTwo = entity("QAB");
        UUID parent = unit("P", null);
        UUID child = unit("C", parent);
        // The parent's attribution was written to end before the first day and then corrected to end after it.
        unitAttribution(parent, entityOne, "date '2026-01-01'");
        execute("insert into organization_workforce.legal_entity_attribution_corrections (correction_id, org_unit_id, period_effective_from_date, previous_effective_to_date,"
                + " new_effective_to_date, correction_reason, corrected_by_user_id) values ('" + id() + "', '" + parent + "', date '2025-01-01', date '2026-01-01', date '2026-03-01',"
                + " 'end date was wrong', '" + id() + "')");
        UUID inherited = person(child);
        UUID own = person(child);
        employeeAttribution(own, entityTwo);
        UUID loneUnit = unit("L", null);
        unitAttribution(loneUnit, entityTwo, "date '2026-01-01'");
        UUID unattributed = person(loneUnit);

        assertThat(resolved(inherited, child)).contains(entityOne);
        assertThat(resolved(own, child)).contains(entityTwo);
        assertThat(resolved(unattributed, loneUnit)).isEmpty();

        assertThat(repository.hasEmployerOn(new EmployeeId(inherited), FIRST_DAY)).isTrue();
        assertThat(repository.hasEmployerOn(new EmployeeId(own), FIRST_DAY)).isTrue();
        assertThat(repository.hasEmployerOn(new EmployeeId(unattributed), FIRST_DAY)).isFalse();
        assertThat(migratedEmployer(inherited)).isEqualTo(resolved(inherited, child).orElseThrow());
        assertThat(migratedEmployer(own)).isEqualTo(resolved(own, child).orElseThrow());
    }
}
