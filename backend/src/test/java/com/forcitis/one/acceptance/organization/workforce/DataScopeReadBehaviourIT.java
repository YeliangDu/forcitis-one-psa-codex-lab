package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * #730 S4, acceptance A1 to A4 of the fail-closed read paths (issue #730 comment 6597359528, security-invariants I-10): one generic harness over the
 * {@code SCOPED} lines of backend/architecture/data-scope-read-paths.txt. Every SCOPED line needs an adapter below (a line without one fails
 * {@link #everyScopedLineHasAnAdapter()}; so does an adapter whose line left the register). The harness seeds rows in unit A and unit B and
 * compares what a caller whose scope covers B only gets with what a caller of the whole organisation gets, so the expected rows come from the data
 * and not from the handler.
 *
 * <p>A1 lists give B's rows and none of A's through every filter and every cursor page; A2 a detail in A answers 404 with the body of an id that
 * does not exist; A3 an empty scope (a custom unit that names nothing, an own-unit scope with no assignment) gives zero rows and 404, never all
 * rows; A4 a scope that cannot be worked out gives 403 on lists and 404 on details, never a wider answer. Ruling: design comment 6597359528 (A4 as
 * worded there); the SCOPED lines are those of the register.
 */
class DataScopeReadBehaviourIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(DataScopeReadBehaviourIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final Path REGISTER = Path.of("architecture/data-scope-read-paths.txt");
    static final String PERSONNEL_COMMAND_CREATE = "organization.workforce:personnel-command:create";
    static final String REQUESTS = "/api/v1/organization/employee-profile-change-requests";
    static final String ORG_UNIT_ENTITIES = "/api/v1/organization/org-unit-legal-entities/{orgUnitId}";
    static final String EMPLOYEE_ENTITIES = "/api/v1/organization/employee-legal-entities/{employeeId}";
    static final String ORG_UNIT = "/api/v1/organization/org-units/{orgUnitId}";
    static final String ADJUSTMENTS = "/api/v1/platform/permission-adjustments";
    static final String ADJUSTMENT = ADJUSTMENTS + "/{permissionAdjustmentId}";

    /**
     * A4 does not apply to these lines: their scope is a relation (permission-adjustment:read holders, else recipient = caller), the list is always 200
     * and never 403, and the reader never calls OrgDirectory, so there is nothing that can fail to resolve (one-design, #730 comment 6602368110 point 3;
     * the harness cannot force it, the register line names its own proof). A1 to A3 still run on them.
     */
    static final Set<String> A4_EXEMPT = Set.of("GET " + ADJUSTMENTS, "GET " + ADJUSTMENT);

    /** The org unit of the row, or null for a row of no unit the world knows. */
    private record ListPath(String register, String path, List<String> queries, boolean paged, String idField, Function<Map<String, Object>, String> unitOfRow) {
    }

    private record DetailPath(String register, String inA, String inB) {
    }

    private final Map<String, String> unitIds = new LinkedHashMap<>();
    private final Map<String, String> unitOfEmployee = new LinkedHashMap<>();
    /** Permission adjustments are scoped by relation, not by unit: "F1-B" stands for the items addressed to scopeB, "F1-A" for every other item. */
    private final Map<String, String> unitOfAdjustment = new LinkedHashMap<>();

    @MockitoSpyBean
    OrgDirectory organisation;

    @BeforeAll
    void theWorld() throws Exception {
        for (String code : List.of("F1-A", "F1-B", "F1-C")) {
            unit(code, null);
        }
        position("F1-P1");
        for (String key : List.of("admin1", "admin2", "oda", "everything", "scopeB", "dangling", "nohome", "broken", "a1", "a2", "b1", "b2")) {
            person(key, key.startsWith("a") && key.length() == 2 ? "F1-A" : key.startsWith("b") && key.length() == 2 ? "F1-B" : "F1-C", "F1-P1");
        }
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        grantByOperations("oda", ORG_DATA_ADMIN);
        for (String code : List.of("F1-A", "F1-B", "F1-C")) {
            unitIds.put(code, orgUnitId(session("oda"), code));
        }
        for (String key : List.of("a1", "a2")) {
            unitOfEmployee.put(employeeIdOf(key), "F1-A");
        }
        for (String key : List.of("b1", "b2")) {
            unitOfEmployee.put(employeeIdOf(key), "F1-B");
        }
        String[] perms = {ORG_UNIT_READ, PERSONNEL_COMMAND_CREATE, EMPLOYEE_READ};
        roleWith("f1-read-all", "ALL", null, perms);
        roleWith("f1-read-b", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("F1-B"))), perms);
        roleWith("f1-read-nothing", "CUSTOM_ORG_UNITS", List.of(onlyUnit(UUID.randomUUID().toString())), perms);
        roleWith("f1-read-own", "OWN_ORG_UNIT", null, perms);
        grantOrdinaryRoles(administrator("admin1"), "everything", "f1-read-all");
        grantByOperations("everything", ORG_DATA_ADMIN);
        grantOrdinaryRoles(administrator("admin1"), "scopeB", "f1-read-b");
        grantOrdinaryRoles(administrator("admin1"), "dangling", "f1-read-nothing");
        grantOrdinaryRoles(administrator("admin1"), "nohome", "f1-read-own");
        grantOrdinaryRoles(administrator("admin1"), "broken", "f1-read-own");
        seedAdjustments();
        try (Connection workforce = schemaConnection("organization_workforce")) {
            OffsetDateTime at = OffsetDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC);
            int minute = 0;
            for (String key : List.of("a1", "b1", "a2", "b2")) {
                OffsetDateTime requestedAt = at.plusMinutes(minute++);
                try (PreparedStatement statement = workforce.prepareStatement("""
                        insert into employee_profile_change_requests (employee_profile_change_request_id, employee_id, requested_by, requested_at,
                            base_display_name, display_name, wecom_user_id, request_status, version, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)""")) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, UUID.fromString(employeeIdOf(key)));
                    statement.setObject(3, UUID.randomUUID());
                    statement.setObject(4, requestedAt);
                    statement.setString(5, "Synthetic " + key);
                    statement.setString(6, "Synthetic renamed " + key);
                    statement.setString(7, "f1-moved-" + key);
                    statement.setObject(8, requestedAt);
                    statement.setObject(9, requestedAt);
                    statement.executeUpdate();
                }
            }
        }
    }

    /** Two scope reviews addressed to scopeB (the "B" rows) and, for the "A" rows, one addressed to another head and one sensitive review with no recipient. */
    private void seedAdjustments() throws Exception {
        OffsetDateTime at = OffsetDateTime.of(2026, 9, 1, 2, 0, 0, 0, ZoneOffset.UTC);
        UUID from = UUID.fromString(unitIds.get("F1-A"));
        UUID to = UUID.fromString(unitIds.get("F1-B"));
        int minute = 0;
        for (String[] item : new String[][] {{"a1", "a2", "F1-A"}, {"b1", "scopeB", "F1-B"}, {"b2", "scopeB", "F1-B"}, {"a2", null, "F1-A"}}) {
            UUID id = UUID.randomUUID();
            OffsetDateTime created = at.plusMinutes(minute++);
            String kind = item[1] == null ? "SENSITIVE_ROLE_REVIEW" : "SCOPE_REVIEW";
            try (Connection identity = schemaConnection("platform_identity"); PreparedStatement statement = identity.prepareStatement("""
                    insert into permission_adjustments (permission_adjustment_id, kind, origin_kind, status, escalation_due_at, created_at, updated_at, user_id,
                        trigger_personnel_command_id, trigger_field_kind, before_org_unit_id, after_org_unit_id, recipient_employee_id)
                    values (?, ?, 'ASSIGNMENT_CHANGE', 'OPEN', ?, ?, ?, ?, ?, 'ORG_UNIT', ?, ?, ?)""")) {
                statement.setObject(1, id);
                statement.setString(2, kind);
                statement.setObject(3, created.plusDays(7));
                statement.setObject(4, created);
                statement.setObject(5, created);
                statement.setObject(6, UUID.fromString(userId(item[0])));
                statement.setObject(7, UUID.randomUUID());
                statement.setObject(8, from);
                statement.setObject(9, to);
                statement.setObject(10, item[1] == null ? null : UUID.fromString(employeeIdOf(item[1])));
                statement.executeUpdate();
            }
            unitOfAdjustment.put(id.toString(), item[2]);
        }
    }

    @AfterEach
    void organisationWorksAgain() {
        Mockito.reset(organisation);
    }

    /** A role that needs the second person when its permissions are sensitive: created at once (201) or confirmed by the second administrator (202 then 204). */
    private void roleWith(String code, String scope, List<CustomOrgUnit> units, String... permissions) throws Exception {
        MockHttpServletResponse created = createRole(administrator("admin1"), roleBody(code, scope, units, permissions));
        if (created.getStatus() == 202) {
            String request = JsonPath.read(created.getContentAsString(), "$.requestId");
            require(confirm(administrator("admin2"), request), 204, "confirming role " + code);
            eventually(() -> assertThat(roleIdOf(administrator("admin1"), code)).isPresent());
        } else {
            require(created, 201, "creating role " + code);
        }
    }

    // ---- the adapters: one per SCOPED line ----------------------------------------------------------------------------------------

    private List<ListPath> lists() {
        return List.of(
                new ListPath("GET /api/v1/organization/org-units", ORG_UNITS, List.of("", "?includeRevoked=true", "?includeRevoked=false"), false, "orgUnitId",
                        row -> unitCodeOf(String.valueOf(row.get("orgUnitId")))),
                new ListPath("GET " + REQUESTS, REQUESTS, List.of("?status=PENDING"), true, "requestId", row -> unitOfEmployee.get(String.valueOf(row.get("employeeId")))),
                new ListPath("GET " + EMPLOYEES, EMPLOYEES, List.of("", "?employmentStatus=ALL"), true, "employeeId", row -> unitOfEmployee.get(String.valueOf(row.get("employeeId")))),
                new ListPath("GET " + ADJUSTMENTS, ADJUSTMENTS, List.of(""), true, "permissionAdjustmentId",
                        row -> unitOfAdjustment.get(String.valueOf(row.get("permissionAdjustmentId")))));
    }

    private List<DetailPath> details() {
        return List.of(
                new DetailPath("GET " + ORG_UNIT, unitIds.get("F1-A"), unitIds.get("F1-B")),
                new DetailPath("GET " + ORG_UNIT_ENTITIES, unitIds.get("F1-A"), unitIds.get("F1-B")),
                new DetailPath("GET " + EMPLOYEE_ENTITIES, employeeIdOf("a1"), employeeIdOf("b1")),
                new DetailPath("GET " + EMPLOYEES + "/{employeeId}", employeeIdOf("a1"), employeeIdOf("b1")),
                new DetailPath("GET " + ADJUSTMENT, adjustmentIn("F1-A"), adjustmentIn("F1-B")));
    }

    private String adjustmentIn(String unit) {
        return unitOfAdjustment.entrySet().stream().filter(item -> item.getValue().equals(unit)).map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    private String unitCodeOf(String unitId) {
        return unitIds.entrySet().stream().filter(unit -> unit.getValue().equals(unitId)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static String urlOf(String registerLine, String id) {
        return registerLine.substring("GET ".length()).replaceAll("\\{[A-Za-z]+}", id);
    }

    // ---- the register -------------------------------------------------------------------------------------------------------------

    private static Set<String> scopedLines() throws IOException {
        Set<String> lines = new TreeSet<>();
        for (String line : Files.readAllLines(REGISTER)) {
            if (line.startsWith("GET ")) {
                String[] parts = line.split("\\|");
                if (parts[1].trim().equals("SCOPED")) {
                    lines.add(parts[0].trim());
                }
            }
        }
        return lines;
    }

    @Test
    @DisplayName("#730 A1-A4: every SCOPED line of the register has an adapter here, and every adapter is a SCOPED line")
    void everyScopedLineHasAnAdapter() throws Exception {
        Set<String> adapted = new TreeSet<>();
        lists().forEach(list -> adapted.add(list.register()));
        details().forEach(detail -> adapted.add(detail.register()));
        assertThat(adapted).containsExactlyInAnyOrderElementsOf(scopedLines());
        assertThat(scopedLines()).as("an A4 exemption names a SCOPED line of the register").containsAll(A4_EXEMPT);
    }

    // ---- calls --------------------------------------------------------------------------------------------------------------------

    private MockHttpServletResponse read(String key, String url) throws Exception {
        return mvc.perform(get(url).with(as(session(key)))).andReturn().getResponse();
    }

    /** Every row of the list as the caller gets it: all cursor pages of a paged list, taken one row per page so that a filter applied after the page shows. */
    private List<Map<String, Object>> rowsOf(String key, ListPath list, String query) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 20; page++) {
            String url = list.path() + query;
            if (list.paged()) {
                url += (url.contains("?") ? "&" : "?") + "limit=1" + (cursor == null ? "" : "&cursor=" + cursor);
            }
            MockHttpServletResponse answer = read(key, url);
            require(answer, 200, key + " reads " + url);
            rows.addAll(items(answer));
            cursor = list.paged() ? JsonPath.read(answer.getContentAsString(), "$.nextCursor") : null;
            if (cursor == null) {
                return rows;
            }
        }
        throw new AssertionError(key + " reads " + list.path() + query + ": more than 20 pages of one row");
    }

    private static Set<String> idsOf(List<Map<String, Object>> rows, ListPath list) {
        Set<String> ids = new LinkedHashSet<>();
        rows.forEach(row -> ids.add(String.valueOf(row.get(list.idField()))));
        return ids;
    }

    /** The body of a 404 without the id the request named, so that a row that exists elsewhere cannot be told from one that does not exist. */
    private static String refusalBody(MockHttpServletResponse answer, String id) throws Exception {
        Map<String, Object> body = JsonPath.parse(answer.getContentAsString()).read("$");
        body = new LinkedHashMap<>(body);
        body.keySet().removeAll(Set.of("correlationId"));
        return body.toString().replace(id, "<id>");
    }

    private void assertAbsentAnswer(String key, DetailPath detail, String id, String what) throws Exception {
        String absent = UUID.randomUUID().toString();
        MockHttpServletResponse nothing = read(key, urlOf(detail.register(), absent));
        MockHttpServletResponse refused = read(key, urlOf(detail.register(), id));
        assertThat(nothing.getStatus()).as(key + " reads " + detail.register() + " for an id that does not exist").isEqualTo(404);
        assertThat(refused.getStatus()).as(what + ": " + refused.getContentAsString()).isEqualTo(404);
        assertThat(refusalBody(refused, id)).as(what + " answers like an id that does not exist").isEqualTo(refusalBody(nothing, absent));
    }

    // ---- A1 ------------------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 A1: a caller whose scope covers unit B only gets B's rows and none of A's from every SCOPED list, through every filter and cursor page")
    void listsGiveTheRowsOfUnitBOnly() throws Exception {
        for (ListPath list : lists()) {
            for (String query : list.queries()) {
                List<Map<String, Object>> everything = rowsOf("everything", list, query);
                Set<String> inB = new LinkedHashSet<>();
                Set<String> inA = new LinkedHashSet<>();
                for (Map<String, Object> row : everything) {
                    String unit = list.unitOfRow().apply(row);
                    (("F1-B".equals(unit)) ? inB : "F1-A".equals(unit) ? inA : new LinkedHashSet<String>()).add(String.valueOf(row.get(list.idField())));
                }
                assertThat(inA).as("the seed: " + list.register() + query + " has rows in A").isNotEmpty();
                assertThat(inB).as("the seed: " + list.register() + query + " has rows in B").isNotEmpty();
                assertThat(idsOf(rowsOf("scopeB", list, query), list)).as(list.register() + query + " for a caller of unit B alone").containsExactlyInAnyOrderElementsOf(inB);
            }
        }
    }

    // ---- A2 ------------------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 A2: a SCOPED detail of unit A is 404 for a caller of unit B alone, with the body of an id that does not exist; the detail of B is readable")
    void detailsOutOfScopeAreNotFound() throws Exception {
        for (DetailPath detail : details()) {
            assertThat(read("everything", urlOf(detail.register(), detail.inA())).getStatus()).as("the seed: " + detail.register() + " reads A for the whole organisation").isEqualTo(200);
            assertAbsentAnswer("scopeB", detail, detail.inA(), detail.register() + " for A");
            assertThat(read("scopeB", urlOf(detail.register(), detail.inB())).getStatus()).as(detail.register() + " for B").isEqualTo(200);
        }
    }

    // ---- A3 ------------------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 A3: a caller whose scope is empty (a custom unit that names nothing; an own-unit scope with no assignment) gets zero rows and 404, never all rows")
    void anEmptyScopeSeesNothing() throws Exception {
        require(read("nohome", ORG_UNITS), 200, "priming the session of the caller: a person without an assignment cannot sign in, so the assignment goes after the sign-in");
        removeAssignmentOf("nohome");
        for (String key : List.of("dangling", "nohome")) {
            for (ListPath list : lists()) {
                for (String query : list.queries()) {
                    assertThat(rowsOf(key, list, query)).as(key + " reads " + list.register() + query).isEmpty();
                }
            }
            for (DetailPath detail : details()) {
                assertAbsentAnswer(key, detail, detail.inA(), key + " reads " + detail.register() + " for A");
                assertAbsentAnswer(key, detail, detail.inB(), key + " reads " + detail.register() + " for B");
            }
        }
    }

    private void removeAssignmentOf(String key) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce");
                PreparedStatement statement = workforce.prepareStatement("delete from assignments where employee_id = ?")) {
            statement.setObject(1, UUID.fromString(employeeIdOf(key)));
            statement.executeUpdate();
        }
    }

    // ---- A4 ------------------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("#730 A4: a caller whose scope cannot be worked out (the organisation cannot answer who the person is) gets 403 on lists and 404 on details, never more")
    void anUnresolvableScopeIsDenied() throws Exception {
        String employee = employeeIdOf("broken");
        require(read("broken", ORG_UNITS), 200, "priming the principal of the caller before the organisation fails (the principal is cached for a minute)");
        doThrow(new IllegalStateException("organisation unavailable")).when(organisation).findEmployee(argThat(id -> id != null && id.value().toString().equals(employee)));
        for (ListPath list : lists()) {
            if (A4_EXEMPT.contains(list.register())) {
                continue;
            }
            for (String query : list.queries()) {
                String url = list.path() + query;
                MockHttpServletResponse refused = read("broken", url);
                assertThat(refused.getStatus()).as(url + ": " + refused.getContentAsString()).isEqualTo(403);
            }
        }
        for (DetailPath detail : details()) {
            if (A4_EXEMPT.contains(detail.register())) {
                continue;
            }
            for (String id : List.of(detail.inA(), detail.inB())) {
                MockHttpServletResponse refused = read("broken", urlOf(detail.register(), id));
                assertThat(refused.getStatus()).as(detail.register() + ": " + refused.getContentAsString()).isEqualTo(404);
            }
        }
    }
}
