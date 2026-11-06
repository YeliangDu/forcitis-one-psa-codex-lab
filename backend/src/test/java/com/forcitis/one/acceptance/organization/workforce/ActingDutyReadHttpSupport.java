package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.COMMAND_KIND;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE_DATE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EMPLOYEE_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.forcitis.one.platform.identity.api.SessionRoles;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.ClassUtils;

/** #1579: HTTP commands create every duty; SQL prepares only the organisation and employment fixtures. */
abstract class ActingDutyReadHttpSupport extends DataScopeSupport {
    static final String DUTIES = "/api/v1/organization/acting-duties/";
    static final String COVERING = "/api/v1/organization/covered-acting-duties/";
    static final String COMMANDS = "/api/v1/organization/personnel-commands";
    static final String CREATE = "organization.workforce:personnel-command:create";
    static final String DENIED = "organization.workforce.employee.denied";
    static final String REASON_TEXT = "synthetic: temporary coverage";
    static final Set<String> ITEM_KEYS = Set.of("actingDutyId", "employeeId", "coveredOrgUnitId", "coveredPositionId",
            "coveredEmployeeId", "coveredEmployeeName", "firstDay", "lastDay", "state", "endCause", "reason");
    static final List<String> ACTORS = List.of("active", "head", "oneDay", "expired", "early", "extended", "separated",
            "zero", "ordered", "cover1", "cover2", "cover3", "named", "roster", "none", "scoped", "unionActor");
    static final List<String> COVERED_PEOPLE = List.of("covered", "orderedCovered", "nobodyCovered", "scopeCovered", "unionCovered");
    final Map<String, String> unitIds = new LinkedHashMap<>();
    final Map<String, UUID> relationships = new LinkedHashMap<>();
    String positionId;
    @MockitoSpyBean OrgDirectory organisation;
    @Autowired ApplicationContext beans;

    @BeforeAll
    void world() throws Exception {
        for (String code : List.of("ADR-A", "ADR-B", "ADR-X", "ADR-W")) unit(code, null);
        position("ADR-P");
        for (String key : ACTORS) person(key, "ADR-A", "ADR-P");
        for (String key : COVERED_PEOPLE) person(key, "ADR-B", "ADR-P");
        for (String key : List.of("admin1", "admin2", "all", "onlyA", "onlyB", "union", "empty", "nohome", "broken", "plain", "createOnly"))
            person(key, "ADR-X", "ADR-P");
        // The writer attributes every fixture unit to an employer; a person may not attribute their own unit (OWN_ATTRIBUTION_FORBIDDEN), so they sit in a unit that is not attributed.
        for (String key : List.of("oda", "writer")) person(key, "ADR-W", "ADR-P");
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        grantByOperations("oda", ORG_DATA_ADMIN);
        grantByOperations("writer", ORG_DATA_ADMIN);
        grantByOperations("writer", "personnel_specialist");
        for (String code : List.of("ADR-A", "ADR-B", "ADR-X")) unitIds.put(code, orgUnitId(session("oda"), code));
        readerRole("adr-all", "ALL", null, EMPLOYEE_READ);
        readerRole("adr-a", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("ADR-A"))), EMPLOYEE_READ);
        readerRole("adr-b", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("ADR-B"))), EMPLOYEE_READ);
        readerRole("adr-empty", "CUSTOM_ORG_UNITS", List.of(onlyUnit(UUID.randomUUID().toString())), EMPLOYEE_READ);
        readerRole("adr-own", "OWN_ORG_UNIT", null, EMPLOYEE_READ);
        readerRole("adr-create", "ALL", null, CREATE);
        for (var grant : Map.of("all", "adr-all", "onlyA", "adr-a", "onlyB", "adr-b", "empty", "adr-empty",
                "nohome", "adr-own", "broken", "adr-own", "createOnly", "adr-create").entrySet())
            grantOrdinaryRoles(administrator("admin1"), grant.getKey(), grant.getValue());
        grantOrdinaryRoles(administrator("admin1"), "union", "adr-a", "adr-b");
        seedEmployment();
        attributeUnits();
    }

    private void readerRole(String code, String scope, List<CustomOrgUnit> units, String permission) throws Exception {
        MockHttpServletResponse made = createRole(administrator("admin1"), roleBody(code, scope, units, permission));
        if (made.getStatus() == 202) {
            require(confirm(administrator("admin2"), JsonPath.read(made.getContentAsString(), "$.requestId")), 204, "confirming synthetic role");
            eventually(() -> assertThat(roleIdOf(administrator("admin1"), code)).isPresent());
        } else require(made, 201, "creating synthetic role");
    }

    private void seedEmployment() throws Exception {
        try (Connection db = schemaConnection("organization_workforce")) {
            try (var found = db.prepareStatement("select id from positions where code = ?")) {
                found.setString(1, "ADR-P");
                try (var rows = found.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    positionId = rows.getObject(1).toString();
                }
            }
            for (var unit : unitIds.entrySet()) sql(db, """
                    insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, region_kind, is_delivery_unit)
                    values (?, ?, ?, ?, 'ACTIVE', 'HEADQUARTERS', false)
                    """, Uuids.timeOrdered(NOW), UUID.fromString(unit.getValue()), TODAY.minusDays(30), unitName(unit.getKey()));
            List<String> subjects = new ArrayList<>(ACTORS);
            subjects.addAll(COVERED_PEOPLE);
            for (String key : subjects) seedPerson(db, key);
        }
    }

    private void seedPerson(Connection db, String key) throws Exception {
        Object employee = stored(UUID.fromString(employeeIdOf(key)));
        UUID relationship = EmploymentRelationshipSeeds.relationshipOf(db, employee);
        relationships.put(key, relationship);
        sql(db, """
                insert into employment_relationship_versions (employment_relationship_id, employment_relationship_version_id, employee_id,
                    effective_from_date, employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner,
                    employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (?, ?, ?, ?, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())
                """, relationship, Uuids.timeOrdered(NOW), employee, TODAY.minusDays(30), TODAY.minusDays(30));
        sql(db, """
                insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, assignment_id,
                    assignment_kind, effective_from_date, org_unit_id, position_id, change_channel_kind)
                select ?, ?, a.employee_id, ?, 'PRIMARY', ?, a.org_unit_id, a.position_id, 'MIGRATION'
                from assignments a where a.employee_id = ?
                """, relationship, Uuids.timeOrdered(NOW), AssignmentRoots.primaryOf(db, employee), TODAY.minusDays(30), employee);
    }

    private void attributeUnits() throws Exception {
        MockHttpServletResponse created = write(LEGAL_ENTITIES, Map.of("code", "ADR", "name", "synthetic: acting read entity"));
        require(created, 201, "creating synthetic employer");
        String entity = JsonPath.read(created.getContentAsString(), "$.legalEntityId");
        for (String id : unitIds.values()) require(mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + id)
                .with(csrf()).with(as(session("writer"))).header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("legalEntityId", entity, "effectiveFrom", TODAY.minusDays(30).toString()))))
                .andReturn().getResponse(), 200, "attributing synthetic unit");
    }

    static void sql(Connection db, String query, Object... args) throws Exception {
        try (var statement = db.prepareStatement(query)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }

    @AfterEach
    void restoreClockAndDirectory() {
        Mockito.reset(organisation);
        advanceTo(NOW);
        SecurityContextHolder.clearContext();
    }

    void on(LocalDate day) throws Exception {
        ensureAuditPartition(day.withDayOfMonth(1));
        advanceTo(day.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
    }

    void dueJob() throws Exception {
        SecurityContextHolder.clearContext();
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name);
            if (type != null && ClassUtils.getUserClass(type).getSimpleName().equals(PersonnelCommandNames.DUE_JOB_TYPE)) {
                ClassUtils.getUserClass(type).getMethod(PersonnelCommandNames.DUE_JOB_METHOD).invoke(beans.getBean(name));
                return;
            }
        }
        throw new AssertionError("missing PersonnelCommandDueJob bean");
    }

    void start(String key, String covered, boolean withPosition, LocalDate first, LocalDate end) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(EFFECTIVE_DATE, first);
        fields.put(ACTING_COVERED_ORG_UNIT_ID, UUID.fromString(unitIds.get("ADR-B")));
        if (withPosition) fields.put(ACTING_COVERED_POSITION_ID, UUID.fromString(positionId));
        if (covered != null) fields.put(ACTING_COVERED_EMPLOYEE_ID, UUID.fromString(employeeIdOf(covered)));
        fields.put(ACTING_ENDS_BEFORE_DATE, end);
        enter(key, ACTING_START, fields);
    }

    /**
     * The personnel command endpoint of this phase has no acting-duty fields (its request record names none), so duties are entered through
     * the production use case in process, as the writer, the way ActingDutySupport does; the reads under test are HTTP.
     */
    void enter(String key, String kind, Map<String, Object> fields) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(fields);
        body.put(EMPLOYEE_ID, UUID.fromString(employeeIdOf(key)));
        body.put(COMMAND_KIND, kind);
        body.put(REASON, REASON_TEXT);
        actAsWriter();
        assertThat(PersonnelCommandsProbe.of(beans, AccountsAcceptanceSupport::employeeIdOf).enter(body)).isNotNull();
    }

    private void actAsWriter() throws Exception {
        var constructor = SignedIn.class.getDeclaredConstructor(UUID.class, UUID.class, String.class);
        constructor.setAccessible(true);
        SignedIn writer = constructor.newInstance(UUID.fromString(userId("writer")), UUID.fromString(employeeIdOf("writer")), session("writer"));
        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").with(session(writer))).andReturn().getResponse();
        require(current, 200, "the writer's own profile");
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : JsonPath.<List<String>>read(current.getContentAsString(), "$.permissionCodes")) authorities.add(new SimpleGrantedAuthority(code));
        if ("ORDINARY".equals(JsonPath.<String>read(current.getContentAsString(), "$.mode"))) authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        actInSession(writer);
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        Method refresh = login.getClass().getDeclaredMethod("withAuthorities", java.util.Collection.class, boolean.class);
        refresh.setAccessible(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication((Authentication) refresh.invoke(login, authorities, true));
        SecurityContextHolder.setContext(context);
    }

    void change(String key, String kind, String duty, LocalDate effective, LocalDate end) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ACTING_DUTY_ID, UUID.fromString(duty));
        if (end != null) fields.put(ACTING_ENDS_BEFORE_DATE, end);
        enter(key, kind, fields);
    }

    MockHttpServletResponse write(String url, Map<String, String> body) throws Exception {
        return mvc.perform(post(url).with(csrf()).with(as(session("writer"))).contentType(MediaType.APPLICATION_JSON)
                .content(json(body))).andReturn().getResponse();
    }

    static String json(Object value) {
        return Configuration.defaultConfiguration().jsonProvider().toJson(value);
    }

    MockHttpServletResponse read(String reader, String url) throws Exception {
        return CommandReadHttpSupport.safe(mvc.perform(get(url).with(as(session(reader)))).andReturn().getResponse());
    }

    List<Map<String, Object>> duties(String reader, String endpoint, String key) throws Exception {
        MockHttpServletResponse answer = read(reader, endpoint + employeeIdOf(key));
        require(answer, 200, "reading acting duties");
        Map<String, Object> root = JsonPath.read(answer.getContentAsString(), "$");
        assertThat(root).containsOnlyKeys("items");
        List<Map<String, Object>> rows = items(answer);
        for (var row : rows) {
            assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(ITEM_KEYS);
            assertThat(UUID.fromString((String) row.get("actingDutyId"))).isNotNull();
        }
        assertThat(field(rows, "actingDutyId")).doesNotHaveDuplicates();
        return rows;
    }

    Map<String, Object> only(String key) throws Exception {
        List<Map<String, Object>> rows = duties("all", DUTIES, key);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    static Map<String, Object> refusalBody(MockHttpServletResponse answer, String id) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(JsonPath.read(answer.getContentAsString(), "$"));
        body.remove("correlationId");
        return JsonPath.read(json(body).replace(id, "<id>"), "$");
    }

    Map<String, Object> rosterSnapshot() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String unit : List.of("", unitIds.get("ADR-A"), unitIds.get("ADR-B"))) {
            for (String status : List.of("", "ALL")) {
                String query = (unit.isEmpty() ? "" : "&orgUnitId=" + unit) + (status.isEmpty() ? "" : "&employmentStatus=" + status);
                List<Map<String, Object>> rows = rosterPages(query);
                if (unit.equals(unitIds.get("ADR-B"))) assertThat(field(rows, "employeeId")).doesNotContain(employeeIdOf("roster"));
                result.put(query, rows);
            }
        }
        for (String key : List.of("roster", "covered")) {
            for (String endpoint : List.of(EMPLOYEES + "/", "/api/v1/organization/primary-assignments/")) {
                MockHttpServletResponse answer = read("all", endpoint + employeeIdOf(key));
                require(answer, 200, "reading unchanged employee and assignments");
                result.put(endpoint + key, JsonPath.read(answer.getContentAsString(), "$"));
            }
        }
        return result;
    }

    private List<Map<String, Object>> rosterPages(String query) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        String cursor = null;
        for (int page = 0; page < 100; page++) {
            MockHttpServletResponse answer = read("all", EMPLOYEES + "?limit=2" + query + (cursor == null ? "" : "&cursor=" + cursor));
            require(answer, 200, "reading every roster page");
            assertThat(items(answer)).hasSizeLessThanOrEqualTo(2);
            rows.addAll(items(answer));
            cursor = JsonPath.read(answer.getContentAsString(), "$.nextCursor");
            if (cursor == null) return rows;
            assertThat(seen.add(cursor)).isTrue();
        }
        throw new AssertionError("roster cursor did not terminate");
    }
}
