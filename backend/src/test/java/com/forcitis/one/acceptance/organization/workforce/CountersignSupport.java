package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ground of the #894 tests (design note of #878; ADR-0073 item 3). Three legal entities (E1 is the world's), one unit attributed to each
 * (plus a second unit of E1), a finance head per entity, a second 人事专员 and an organisation data administrator. Employees either carry
 * their own attribution to E1 ({@link #employed}) or only inherit the legal entity of their unit ({@link #inheriting}); the cost legal
 * entity is the own attribution first, else the unit chain (ADR-0073 item 3.4). The 人事专员 {@code hr} drafts everything.
 */
abstract class CountersignSupport extends SeparationHoldSupport {

    @Autowired
    private ApplicationContext appContext;

    protected UUID e1;
    protected UUID e2;
    protected UUID e3;
    protected UUID unitE1;
    protected UUID unitE1b;
    protected UUID unitE2;
    protected UUID unitE3;
    protected Staff orgAdmin;
    protected Staff headE1;
    protected Staff headE2;
    protected Staff headE3;

    @BeforeEach
    void buildTheCountersignWorld() throws Exception {
        orgAdmin = account("org_data_admin");
        e1 = legalEntity;
        e2 = createLegalEntity(orgAdmin.session(), newLegalEntityCode("CB"), "Synthetic entity two");
        e3 = createLegalEntity(orgAdmin.session(), newLegalEntityCode("CC"), "Synthetic entity three");
        unitE1 = attributedUnit(e1);
        unitE1b = attributedUnit(e1);
        unitE2 = attributedUnit(e2);
        unitE3 = attributedUnit(e3);
        headE1 = head(e1);
        headE2 = head(e2);
        headE3 = head(e3);
        actIn(hr);
    }

    // ---- world ----

    protected UUID attributedUnit(UUID entity) throws Exception {
        UUID unitId = newUnit();
        MockHttpServletResponse attributed = mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + text(unitId)).header("If-Match", "\"0\"")
                .with(csrf()).with(session(orgAdmin.session())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":\"" + entity + "\",\"effectiveFrom\":\"" + TODAY.minusDays(30) + "\"}")).andReturn().getResponse();
        assertThat(attributed.getStatus()).as("attributing the unit: " + attributed.getContentAsString()).isEqualTo(200);
        return unitId;
    }

    /** An employed person without an own attribution: the cost legal entity comes from the unit chain. Same migrated versions as {@link #employed}. */
    protected UUID inheriting(UUID inUnit) throws Exception {
        UUID position = newPosition();
        UUID id = newEmployeeId();
        employee(id, inUnit, null);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'EMPLOYED', ?, false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, REGULAR, SINCE, at, at);
            if (!query(workforce, "select 1 as present from information_schema.columns where table_schema = 'organization_workforce' and table_name = ? and column_name = ?",
                    ASSIGNMENT_VERSIONS, "change_channel_kind").isEmpty()) {
                update(workforce, """
                        insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                          change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""",
                        Uuids.timeOrdered(NOW), stored(id), SINCE, stored(inUnit), position, at, at);
            }
        }
        return id;
    }

    /** A finance head: employed, holds the finance head role and is bound to the given legal entities (the binding is a 暂名, see CountersignNames). */
    protected Staff head(UUID... entities) throws Exception {
        Staff who = account(FINANCE_HEAD_ROLE);
        for (UUID entity : entities) {
            bind(who, entity);
        }
        return who;
    }

    /**
     * Makes the person the finance head of the entity from today on: the open version of the entity is closed today and a new one that
     * repeats code, name and status and names the person is appended (legal_entity_versions is append-only; ruling of 2026-10-30).
     */
    protected void bind(Staff who, UUID entity) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Map<String, Object> open = query(workforce, "select * from " + LEGAL_ENTITY_VERSIONS + " where legal_entity_id = ? and effective_to_date is null", entity).get(0);
            update(workforce, "update " + LEGAL_ENTITY_VERSIONS + " set effective_to_date = ? where legal_entity_version_id = ?", TODAY, open.get("legal_entity_version_id"));
            update(workforce, "insert into " + LEGAL_ENTITY_VERSIONS + " (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, code, name, status, "
                    + FINANCE_HEAD_COLUMN + ") values (?, ?, ?, ?, ?, ?, ?, ?)", Uuids.timeOrdered(NOW), entity, TODAY, ((Number) open.get("revision_number")).intValue() + 1,
                    open.get("code"), open.get("name"), open.get("status"), stored(who.employeeId()));
        }
    }

    /** Raises the escalation level of a request directly in the schema (the trigger allows increases; the reminder job is card E). */
    protected void escalateTo(UUID command, String kind, UUID entity, int level) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update " + COUNTERSIGNS + " set escalation_level = ? where attribution_countersign_id = ?", (short) level, requestId(command, kind, entity));
        }
    }

    /** Another 人事专员, employed and attributed (so that commands about this person are possible). */
    protected Staff specialist(String... moreRoles) throws Exception {
        List<String> roles = new java.util.ArrayList<>(List.of(PERSONNEL_SPECIALIST_ROLE));
        roles.addAll(List.of(moreRoles));
        return account(roles.toArray(String[]::new));
    }

    // ---- entering ----

    protected record Awaiting(UUID employee, UUID command, LocalDate date) {
    }

    protected UUID enterChange(UUID employee, String kind, LocalDate date, UUID target) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, employee);
        fields.put(COMMAND_KIND, kind);
        fields.put(EFFECTIVE_DATE, date);
        fields.put(TARGET_LEGAL_ENTITY_ID, target);
        return commands().enter(fields);
    }

    /** A TRANSFER of an inheriting employee of E1 to the unit of E2 by the drafter: waits for both heads. */
    protected Awaiting crossEntityTransfer(LocalDate date) throws Exception {
        UUID employee = inheriting(unitE1);
        actIn(hr);
        UUID command = transfer(employee, date, unitE2);
        assertThat(commandStatus(command)).as("a cross-entity transfer waits for the countersign").isEqualTo(AWAITING);
        return new Awaiting(employee, command, date);
    }

    /** A CHANGE_LEGAL_ENTITY of an employee with an own attribution to E1, to the target entity: waits for the heads of E1 and the target. */
    protected Awaiting changeTo(UUID target, LocalDate date) throws Exception {
        UUID employee = employed(REGULAR);
        actIn(hr);
        UUID command = enterChange(employee, CHANGE_LEGAL_ENTITY, date, target);
        assertThat(commandStatus(command)).as("a change of the cost legal entity waits for the countersign").isEqualTo(AWAITING);
        return new Awaiting(employee, command, date);
    }

    // ---- deciding ----

    /** The caller of the next countersign call is this person, in the production session of their login. */
    protected CountersignProbe countersignsAs(Staff who) throws Exception {
        actIn(who);
        return CountersignProbe.of(appContext, AccountsAcceptanceSupport::employeeIdOf);
    }

    protected void approve(Staff who, UUID command, String kind, UUID entity) throws Exception {
        UUID id = requestId(command, kind, entity);
        countersignsAs(who).approve(id, null);
    }

    protected void reject(Staff who, UUID command, String kind, UUID entity, String reason) throws Exception {
        UUID id = requestId(command, kind, entity);
        countersignsAs(who).reject(id, reason);
    }

    /** Both CHANGE requests of a command between E1 and E2 are approved by the two heads. */
    protected void approveBoth(UUID command) throws Exception {
        approve(headE1, command, KIND_CHANGE, e1);
        approve(headE2, command, KIND_CHANGE, e2);
    }

    /** The security context of the person, captured for worker threads (security contexts are per thread). */
    protected SecurityContext contextOf(Staff who) throws Exception {
        actIn(who);
        return SecurityContextHolder.getContext();
    }

    protected CountersignProbe probeWithoutContext() {
        return CountersignProbe.of(appContext, AccountsAcceptanceSupport::employeeIdOf);
    }

    // ---- reading ----

    protected List<Map<String, Object>> requests(UUID command, String kind) throws SQLException {
        return rows("select * from " + COUNTERSIGNS + " where personnel_command_id = ? and request_kind = ? order by request_sequence_number, legal_entity_id", command, kind);
    }

    /** The newest request of the kind for the legal entity (highest sequence number). */
    protected Map<String, Object> request(UUID command, String kind, UUID entity) throws SQLException {
        List<Map<String, Object>> found = rows("select * from " + COUNTERSIGNS + " where personnel_command_id = ? and request_kind = ? and legal_entity_id = ? order by request_sequence_number desc",
                command, kind, entity);
        assertThat(found).as("a row of " + COUNTERSIGNS + " for " + kind + " of command " + command + " and entity " + entity).isNotEmpty();
        return found.get(0);
    }

    protected UUID requestId(UUID command, String kind, UUID entity) throws SQLException {
        return (UUID) request(command, kind, entity).get("attribution_countersign_id");
    }

    protected String decision(UUID command, String kind, UUID entity) throws SQLException {
        return (String) request(command, kind, entity).get("decision_kind");
    }

    protected long openRequests(UUID command) throws SQLException {
        return (long) rows("select count(*) as n from " + COUNTERSIGNS + " where personnel_command_id = ? and decision_kind is null", command).get(0).get("n");
    }

    /** Rows of the version tables that name the command as the one that wrote them (superseded ones included). */
    protected long versionRowsWrittenBy(UUID command) throws SQLException {
        long total = 0;
        for (String table : List.of(EMPLOYEE_VERSIONS, ASSIGNMENT_VERSIONS, "employee_legal_entities")) {
            total += (long) rows("select count(*) as n from " + table + " where change_command_id = ?", command).get(0).get("n");
        }
        return total;
    }

    protected List<Map<String, Object>> currentLegalEntities(UUID employee) throws SQLException {
        return currentVersions("employee_legal_entities", employee);
    }

    /** An api call that must be refused: the failure, or null when nothing was thrown. */
    protected static Throwable refusal(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return org.assertj.core.api.Assertions.catchThrowable(call);
    }
}
