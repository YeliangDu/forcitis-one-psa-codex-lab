package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommands;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.RegionKind;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
@Import(OrgUnitAttributesIT.Configuration.class)
class OrgUnitAttributesIT {
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 12, 1);

    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        Clock clock() {
            return on(EFFECTIVE);
        }
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BusinessZone zone;

    @Autowired
    IdGenerator ids;

    @Autowired
    OrgStructureCommands commands;

    @Test
    @DisplayName("#1178 org unit attributes follow the effective command, fall back only without versions and ignore superseded rows")
    void attributesFollowTheReadDay() {
        UUID actor = ids.next();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor.toString(), null, "organization.workforce:org-structure-command:create"));
        try (WorkContext.Scope acting = WorkContext.enter(ids.next(), null, "test.organization.workforce")) {
            OrgUnitId unit = unit("before", "old", "HEADQUARTERS", false);
            OrgUnitId fallback = unit("snapshot", "snap", "REGIONAL", true);
            jdbc.sql("""
                    insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date,
                           name, short_name, status, region_kind, is_delivery_unit)
                    values (:version, :unit, :since, 'before', 'old', 'ACTIVE', 'HEADQUARTERS', false)
                    """).param("version", ids.next()).param("unit", unit.value()).param("since", EFFECTIVE.minusYears(1)).update();

            OrgStructureCommandId command = commands.enter(new EnterOrgStructureCommand(OrgStructureTargetKind.ORG_UNIT,
                    OrgStructureCommandKind.SET_ATTRIBUTES, unit, null, EFFECTIVE, null, "after", "new", RegionKind.REGIONAL, true,
                    null, null, null, null, null));
            assertThat(jdbc.sql("""
                    select status from organization_workforce.org_structure_commands where org_structure_command_id = :id
                    """).param("id", command.value()).query(String.class).single()).isEqualTo("EFFECTIVE");

            jdbc.sql("""
                    insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date,
                           revision_number, name, short_name, status, region_kind, is_delivery_unit, superseded_at, superseding_org_structure_command_id)
                    values (:version, :unit, :since, 9, 'superseded', 'superseded', 'ACTIVE', 'HEADQUARTERS', false, :at, :command)
                    """).param("version", ids.next()).param("unit", unit.value()).param("since", EFFECTIVE.minusYears(1))
                    .param("at", EFFECTIVE.atStartOfDay().atOffset(ZoneOffset.UTC)).param("command", command.value()).update();

            List<AttributionCountersignId> countersigns = List.of(countersign(unit, fallback, actor, "QATTRA"), countersign(fallback, unit, actor, "QATTRB"));
            assertReads(unit, fallback, countersigns, EFFECTIVE.minusDays(1), "before", "old", RegionCategory.HEADQUARTERS, false);
            assertReads(unit, fallback, countersigns, EFFECTIVE, "after", "new", RegionCategory.REGIONAL, true);
            assertReads(unit, fallback, countersigns, EFFECTIVE.plusDays(30), "after", "new", RegionCategory.REGIONAL, true);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private OrgUnitId unit(String name, String shortName, String region, boolean delivery) {
        OrgUnitId unit = new OrgUnitId(ids.next());
        jdbc.sql("""
                insert into organization_workforce.org_units (id, name, short_name, status, region_category, is_delivery_unit, sort_order)
                values (:id, :name, :shortName, 'ACTIVE', :region, :delivery, 7)
                """).param("id", unit.value()).param("name", name).param("shortName", shortName).param("region", region).param("delivery", delivery).update();
        return unit;
    }

    @Test
    @DisplayName("#1178 a versioned unit reads the snapshot outside its version dates and nullable attributes do not fall back")
    void versionedUnitsFallBackOnlyOutsideTheirDates() {
        OrgUnitId unit = unit("snapshot", "snap", "REGIONAL", true);
        jdbc.sql("""
                insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date,
                       effective_to_date, closed_at, name, short_name, status, region_kind, is_delivery_unit)
                values (:version, :unit, :since, :until, :at, 'version', null, 'ACTIVE', null, false)
                """).param("version", ids.next()).param("unit", unit.value()).param("since", EFFECTIVE).param("until", EFFECTIVE.plusDays(1))
                .param("at", EFFECTIVE.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)).update();
        JdbcOrgSnapshotQueries snapshots = new JdbcOrgSnapshotQueries(jdbc, on(EFFECTIVE), zone);
        assertAttributes(snapshots.findOrgUnit(unit).orElseThrow(), "version", null, null, false);
        assertAttributes(snapshots.findAllOrgUnits().stream().filter(u -> u.orgUnitId().equals(unit)).findFirst().orElseThrow(), "version", null, null, false);
        for (LocalDate day : List.of(EFFECTIVE.minusDays(1), EFFECTIVE.plusDays(1))) {
            JdbcOrgSnapshotQueries absent = new JdbcOrgSnapshotQueries(jdbc, on(day), zone);
            assertAttributes(absent.findOrgUnit(unit).orElseThrow(), "snapshot", "snap", RegionCategory.REGIONAL, true);
            assertAttributes(absent.findAllOrgUnits().stream().filter(u -> u.orgUnitId().equals(unit)).findFirst().orElseThrow(), "snapshot", "snap", RegionCategory.REGIONAL, true);
            assertThat(new JdbcCountersignReadQueries(jdbc, on(day), zone).orgUnitName(unit)).contains("snapshot");
        }
    }

    private AttributionCountersignId countersign(OrgUnitId previous, OrgUnitId target, UUID actor, String code) {
        UUID employee = ids.next();
        UUID assignment = ids.next();
        UUID command = ids.next();
        UUID entity = ids.next();
        AttributionCountersignId request = new AttributionCountersignId(ids.next());
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, employment_status)
                values (:id, :code, 'employee', 'EMPLOYED')
                """).param("id", employee).param("code", employee.toString()).update();
        jdbc.sql("""
                insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)
                values (:id, :employee, 'PRIMARY', :at, :at)
                """).param("id", assignment).param("employee", employee).param("at", EFFECTIVE.atStartOfDay().atOffset(ZoneOffset.UTC)).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, assignment_id, assignment_kind, employee_id,
                       org_unit_id, effective_from_date)
                values (:id, :assignment, 'PRIMARY', :employee, :unit, :since)
                """).param("id", ids.next()).param("assignment", assignment).param("employee", employee)
                .param("unit", previous.value()).param("since", EFFECTIVE.minusYears(1)).update();
        jdbc.sql("""
                insert into organization_workforce.legal_entities (id, code, name, status)
                values (:id, :code, 'entity', 'ACTIVE')
                """).param("id", entity).param("code", code).update();
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status,
                       target_org_unit_id, target_assignment_id, target_assignment_kind, created_at, created_by, updated_at)
                values (:id, :employee, 'TRANSFER', :day, 'PENDING', :unit, :assignment, 'PRIMARY', :at, :actor, :at)
                """).param("id", command).param("employee", employee).param("day", EFFECTIVE.plusDays(10)).param("unit", target.value())
                .param("assignment", assignment).param("at", EFFECTIVE.atStartOfDay().atOffset(ZoneOffset.UTC)).param("actor", actor).update();
        jdbc.sql("""
                insert into organization_workforce.attribution_countersigns (attribution_countersign_id, personnel_command_id, employee_id,
                       drafted_by, request_kind, request_sequence_number, legal_entity_id, requested_at, requested_by, due_on_date,
                       escalation_level_number, created_at, updated_at)
                values (:id, :command, :employee, :actor, 'CHANGE', 1, :entity, :at, :actor, :due, 2, :at, :at)
                """).param("id", request.value()).param("command", command).param("employee", employee).param("actor", actor)
                .param("entity", entity).param("at", EFFECTIVE.atStartOfDay().atOffset(ZoneOffset.UTC)).param("due", EFFECTIVE.plusDays(5)).update();
        return request;
    }

    private void assertReads(OrgUnitId unit, OrgUnitId fallback, List<AttributionCountersignId> requests, LocalDate day,
                             String name, String shortName, RegionCategory region, boolean delivery) {
        JdbcOrgSnapshotQueries snapshots = new JdbcOrgSnapshotQueries(jdbc, on(day), zone);
        assertAttributes(snapshots.findOrgUnit(unit).orElseThrow(), name, shortName, region, delivery);
        assertAttributes(snapshots.findAllOrgUnits().stream().filter(u -> u.orgUnitId().equals(unit)).findFirst().orElseThrow(), name, shortName, region, delivery);
        assertAttributes(snapshots.findOrgUnit(fallback).orElseThrow(), "snapshot", "snap", RegionCategory.REGIONAL, true);
        assertAttributes(snapshots.findAllOrgUnits().stream().filter(u -> u.orgUnitId().equals(fallback)).findFirst().orElseThrow(), "snapshot", "snap", RegionCategory.REGIONAL, true);
        JdbcCountersignReadQueries names = new JdbcCountersignReadQueries(jdbc, on(day), zone);
        assertThat(names.orgUnitName(unit)).contains(name);
        assertThat(names.orgUnitName(fallback)).contains("snapshot");
        JdbcAttributionCountersignQueries attribution = new JdbcAttributionCountersignQueries(jdbc, on(day), zone);
        AttributionCountersignQueries.Visible visible = new AttributionCountersignQueries.Visible(new EmployeeId(ids.next()), true, day);
        for (AttributionCountersignId request : requests) {
            assertNames(attribution.find(visible, request).orElseThrow(), unit, name);
            assertNames(attribution.sameCommand(request).getFirst(), unit, name);
        }
        assertThat(attribution.list(visible, CountersignStatus.OPEN, null, 10)).hasSize(2).allSatisfy(row -> assertNames(row, unit, name));
    }

    private static void assertAttributes(OrgUnit unit, String name, String shortName, RegionCategory region, boolean delivery) {
        assertThat(unit.name()).isEqualTo(name);
        assertThat(unit.shortName()).isEqualTo(shortName);
        assertThat(unit.regionCategory()).isEqualTo(region);
        assertThat(unit.deliveryUnit()).isEqualTo(delivery);
        assertThat(unit.parentOrgUnitId()).isNull();
        assertThat(unit.sortOrder()).isEqualTo(7);
        assertThat(unit.status()).hasToString("ACTIVE");
        assertThat(unit.headEmployeeId()).isNull();
    }

    private static void assertNames(AttributionCountersignQueries.Row row, OrgUnitId unit, String name) {
        assertThat(row.previousOrgUnitName()).isEqualTo(unit.equals(row.previousOrgUnitId()) ? name : "snapshot");
        assertThat(row.newOrgUnitName()).isEqualTo(unit.equals(row.newOrgUnitId()) ? name : "snapshot");
    }

    private static Clock on(LocalDate day) {
        return Clock.fixed(day.atTime(4, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
