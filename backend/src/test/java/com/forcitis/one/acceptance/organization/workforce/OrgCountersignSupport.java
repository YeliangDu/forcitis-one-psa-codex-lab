package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.MERGE;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.MERGE_INTO_ORG_UNIT_ID;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.ORG_UNIT_LEGAL_ENTITIES;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.REPARENT;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.TARGET_LEGAL_ENTITY_ID;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.TARGET_PARENT_ORG_UNIT_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.ASSIGNMENT_VERSIONS;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Ground of the #910 org countersign tests (note org-structure-commands.md section 5; DD amendment D on #910; amendment E moved to #1040), on the world of
 * {@link OrgStructureCommandSupport}: three legal entities (E1 is the world's), a top unit attributed to each, and a finance head per entity
 * bound by legal_entity_versions.finance_head_employee_id (#893 DD amendment 2). People either inherit the legal entity of their unit chain
 * ({@link #inheritingIn}) or carry their own attribution ({@link #employedIn}); the cost legal entity on a day is the own attribution first,
 * else the chain from the unit of the primary assignment (amendment D). Every unit, entity code and person is new per test (#1038).
 */
abstract class OrgCountersignSupport extends OrgStructureCommandSupport {

    @Autowired
    protected ApplicationContext springContext;

    protected UUID e1;
    protected UUID e2;
    protected UUID e3;
    protected UUID topE1;
    protected UUID topE2;
    protected UUID topE3;
    protected Staff headE1;
    protected Staff headE2;
    protected Staff headE3;

    @BeforeEach
    void buildTheOrgCountersignWorld() throws Exception {
        e1 = legalEntity;
        e2 = createLegalEntity(orgAdmin.session(), newLegalEntityCode("OB"), "Synthetic org countersign entity two");
        e3 = createLegalEntity(orgAdmin.session(), newLegalEntityCode("OC"), "Synthetic org countersign entity three");
        topE1 = attributedUnit(null, e1);
        topE2 = attributedUnit(null, e2);
        topE3 = attributedUnit(null, e3);
        headE1 = head(e1);
        headE2 = head(e2);
        headE3 = head(e3);
        actIn(orgAdmin);
    }

    // ---- world ----

    /** A seeded unit (migrated version from SINCE) with its own legal-entity attribution from 30 days ago, set through the attribution endpoint. */
    protected UUID attributedUnit(UUID parent, UUID entity) throws Exception {
        UUID id = seedUnit(parent);
        MockHttpServletResponse attributed = mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + text(id)).header("If-Match", "\"0\"")
                .with(csrf()).with(session(orgAdmin.session())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":\"" + entity + "\",\"effectiveFrom\":\"" + TODAY.minusDays(30) + "\"}")).andReturn().getResponse();
        assertThat(attributed.getStatus()).as("attributing the unit: " + attributed.getContentAsString()).isEqualTo(200);
        return id;
    }

    /** An employed person without an own attribution: the cost legal entity comes from the unit chain. Migrated versions from SINCE. */
    protected UUID inheritingIn(UUID orgUnit) throws Exception {
        UUID id = newEmployeeId();
        employee(id, orgUnit, null);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'EMPLOYED', ?, false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, REGULAR, SINCE, at, at);
            update(workforce, """
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, stored(orgUnit), newPosition(), at, at);
        }
        return id;
    }

    /** An employed, signed-in person holding the roles. */
    protected Staff person(String... roles) throws Exception {
        UUID id = employedIn(topE1, REGULAR, true);
        for (String role : roles) {
            grantByOperations(id, role);
        }
        return new Staff(id, signIn(id));
    }

    /** A finance head: holds the finance head role and is the finance head of the entities in the version valid from today. */
    protected Staff head(UUID... entities) throws Exception {
        Staff who = person(FINANCE_HEAD_ROLE);
        for (UUID entity : entities) {
            bind(who, entity);
        }
        return who;
    }

    /** legal_entity_versions is close-and-append: the open version is closed today and a copy naming the person is appended. */
    protected void bind(Staff who, UUID entity) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Map<String, Object> open = query(workforce, "select * from " + LEGAL_ENTITY_VERSIONS + " where legal_entity_id = ? and effective_to_date is null", entity).get(0);
            update(workforce, "update " + LEGAL_ENTITY_VERSIONS + " set effective_to_date = ? where legal_entity_version_id = ?", TODAY, open.get("legal_entity_version_id"));
            update(workforce, "insert into " + LEGAL_ENTITY_VERSIONS + " (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, code, name, status, "
                    + FINANCE_HEAD_COLUMN + ") values (?, ?, ?, ?, ?, ?, ?, ?)", Uuids.timeOrdered(NOW), entity, TODAY, ((Number) open.get("revision_number")).intValue() + 1,
                    open.get("code"), open.get("name"), open.get("status"), stored(who.employeeId()));
        }
    }

    // ---- entering ----

    protected UUID assignLegalEntity(UUID target, LocalDate day, UUID entity) {
        return unitCommand(ASSIGN_LEGAL_ENTITY, target, day, TARGET_LEGAL_ENTITY_ID, entity);
    }

    protected UUID reparent(UUID target, LocalDate day, UUID parent) {
        return unitCommand(REPARENT, target, day, TARGET_PARENT_ORG_UNIT_ID, parent);
    }

    protected UUID merge(UUID merged, UUID receiving, LocalDate day) {
        return unitCommand(MERGE, merged, day, MERGE_INTO_ORG_UNIT_ID, receiving);
    }

    protected String statusOf(UUID orgCommand) throws SQLException {
        return (String) orgCommand(orgCommand).get("status");
    }

    // ---- deciding ----

    protected OrgCountersignProbe countersignsAs(Staff who) throws Exception {
        actIn(who);
        restoreActing();
        return OrgCountersignProbe.of(springContext);
    }

    protected void approve(Staff who, UUID orgCommand, String kind, UUID entity) throws Exception {
        UUID id = requestId(orgCommand, kind, entity);
        countersignsAs(who).approve(id);
        actIn(orgAdmin);
    }

    protected void reject(Staff who, UUID orgCommand, String kind, UUID entity, String reason) throws Exception {
        UUID id = requestId(orgCommand, kind, entity);
        countersignsAs(who).reject(id, reason);
        actIn(orgAdmin);
    }

    /** Raises the escalation level of a request directly (the trigger allows increases; the reminder job is card E). */
    protected void escalateTo(UUID orgCommand, String kind, UUID entity, int level) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update " + COUNTERSIGNS + " set escalation_level_number = ? where attribution_countersign_id = ?", (short) level, requestId(orgCommand, kind, entity));
        }
    }

    // ---- reading ----

    /** The request rows of the org command (rows with the org parent column, #908 DD section 2). */
    protected List<Map<String, Object>> requests(UUID orgCommand, String kind) throws SQLException {
        return rows("select * from " + COUNTERSIGNS + " where " + ORG_PARENT_COLUMN + " = ? and request_kind = ? order by request_sequence_number, legal_entity_id", orgCommand, kind);
    }

    protected Map<String, Object> request(UUID orgCommand, String kind, UUID entity) throws SQLException {
        List<Map<String, Object>> found = rows("select * from " + COUNTERSIGNS + " where " + ORG_PARENT_COLUMN + " = ? and request_kind = ? and legal_entity_id = ?"
                + " order by request_sequence_number desc", orgCommand, kind, entity);
        assertThat(found).as("a row of " + COUNTERSIGNS + " for " + kind + " of org command " + orgCommand + " and entity " + entity).isNotEmpty();
        return found.get(0);
    }

    protected UUID requestId(UUID orgCommand, String kind, UUID entity) throws SQLException {
        return (UUID) request(orgCommand, kind, entity).get("attribution_countersign_id");
    }

    protected String decision(UUID orgCommand, String kind, UUID entity) throws SQLException {
        return (String) request(orgCommand, kind, entity).get("decision_kind");
    }

    /** The legal entities that have a request of the kind on the org command. */
    protected Set<UUID> requestedEntities(UUID orgCommand, String kind) throws SQLException {
        Set<UUID> entities = new HashSet<>();
        for (Map<String, Object> row : requests(orgCommand, kind)) {
            entities.add((UUID) row.get("legal_entity_id"));
        }
        return entities;
    }

    protected long allRequestRows() throws SQLException {
        return (long) rows("select count(*) as n from " + COUNTERSIGNS).get(0).get("n");
    }

    /** Basis rows of the command: employee id to {previous, new} legal entity. */
    protected Map<UUID, List<UUID>> basis(UUID orgCommand) throws SQLException {
        Map<UUID, List<UUID>> basis = new HashMap<>();
        for (Map<String, Object> row : rows("select * from " + BASES + " where org_structure_command_id = ?", orgCommand)) {
            basis.put(fromStored(row.get("employee_id")), List.of((UUID) row.get("previous_legal_entity_id"), (UUID) row.get("new_legal_entity_id")));
        }
        return basis;
    }

    /** Current attribution rows of the unit (own attribution, W2 common set: not superseded). */
    protected List<Map<String, Object>> unitAttributions(UUID unitId) throws SQLException {
        return rows("select * from " + ORG_UNIT_LEGAL_ENTITIES + " where org_unit_id = ? and superseded_at is null order by effective_from_date", stored(unitId));
    }

    protected long unitAttributionRowsWrittenBy(UUID orgCommand) throws SQLException {
        return (long) rows("select count(*) as n from " + ORG_UNIT_LEGAL_ENTITIES + " where change_org_structure_command_id = ?", orgCommand).get(0).get("n");
    }

    protected long unitVersionRowsWrittenBy(UUID orgCommand) throws SQLException {
        return (long) rows("select count(*) as n from org_unit_versions where change_org_structure_command_id = ?", orgCommand).get(0).get("n");
    }

    /** Distinct EmployeeLegalEntityChanged events that mention the employee. */
    protected int legalEntityChangesOf(UUID employee) throws SQLException {
        return eventsOf(EVENT_LEGAL_ENTITY_CHANGED, employee);
    }

    /** Personnel commands generated by the merge, by employee. */
    protected Map<UUID, Map<String, Object>> generatedTransfers(UUID merge) throws SQLException {
        Map<UUID, Map<String, Object>> transfers = new HashMap<>();
        for (Map<String, Object> row : rows("select * from personnel_commands where org_structure_command_id = ?", merge)) {
            transfers.put(fromStored(row.get("employee_id")), row);
        }
        return transfers;
    }

    protected UUID unitOf(UUID employee, LocalDate day) throws SQLException {
        return fromStored(inForceOn(currentVersions(ASSIGNMENT_VERSIONS, employee), day).get("org_unit_id"));
    }
}
