package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
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
 * #1171, the employee roster: {@code GET /api/v1/organization/employees?orgUnitId=&employmentStatus=&limit=&cursor=} and {@code .../employees/{employeeId}}
 * for the holders of {@code organization.workforce:employee:read}. The code is held by the system role personnel_specialist only, not by super_admin,
 * org_data_admin or any other (user 2026-11-03, intent 214823); the list is filtered by the caller's data scope, fail-closed; default lists employed
 * people only, {@code employmentStatus=ALL} or {@code SEPARATED} include the separated (same intent); a person with several assignments is in scope when
 * any assignment's department is in scope and the row lists every assignment; the detail lists all assignments with the reports-to person (user
 * 2026-11-04, intent 055504); an out-of-scope id answers 404 and writes the audit entry organization.workforce.employee.denied; no ID-card number,
 * ethnicity or household registration anywhere (ADR-0079:124, ADR-0080 U-14).
 *
 * <p>Contract fixed here, because the card names no field. A row: {@code employeeId, employeeCode, displayName, employmentStatus, hireDate,
 * assignments[]}; an assignment: {@code assignmentKind (PRIMARY|CONCURRENT), orgUnitId, orgUnitName, positionId, positionName}; the detail adds
 * {@code reportsToEmployeeId, reportsToDisplayName} to each assignment (null on a concurrent one). A page is {@code items} and {@code nextCursor}. A scope
 * that cannot be worked out answers 403 on the list and 404 on the detail (one-design, #730 comment 6597359528, as DataScopeReadBehaviourIT). The
 * roster is read from the versions (employee versions for status and hire date, assignment versions for unit, position and reporting line; the
 * concurrent assignment exists only there). The snapshot rows are written too, only because sign-in and scope resolution need them; both agree on unit and position.
 */
class EmployeeRosterHttpIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(EmployeeRosterHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String DENIED = "organization.workforce.employee.denied";
    static final LocalDate STARTED = LocalDate.of(2025, 3, 1);
    static final LocalDate STARTED_SUB = LocalDate.of(2024, 7, 15);
    static final LocalDate SINCE = LocalDate.of(2026, 1, 1);
    static final List<String> WORLD = List.of("a", "sub", "a1", "b", "c", "multi", "gone");
    static final Set<String> ROW_KEYS = Set.of("employeeId", "employeeCode", "displayName", "employmentStatus", "hireDate", "assignments");
    static final Set<String> ASSIGNMENT_KEYS = Set.of("assignmentKind", "orgUnitId", "orgUnitName", "positionId", "positionName", "reportsToEmployeeId", "reportsToDisplayName");
    static final Pattern FORBIDDEN_KEY = Pattern.compile("(?i)id.?card|idnumber|identity.?(card|number)|ethnic|household|national.?id");

    private final Map<String, String> unitIds = new LinkedHashMap<>();

    @MockitoSpyBean
    OrgDirectory organisation;

    @BeforeAll
    void theWorld() throws Exception {
        unit("RS-A", null);
        unit("RS-A1", "RS-A");
        unit("RS-B", null);
        unit("RS-C", null);
        unit("RS-X", null);
        position("RS-P1");
        position("RS-P2");
        for (String key : List.of("a", "sub", "gone", "ownReader")) {
            person(key, "RS-A", "RS-P1");
        }
        person("a1", "RS-A1", "RS-P1");
        person("b", "RS-B", "RS-P1");
        person("c", "RS-C", "RS-P2");
        person("multi", "RS-C", "RS-P2");
        for (String key : List.of("admin1", "admin2", "hr", "oda", "allReader", "subtreeReader", "bReader", "emptyReader", "brokenReader", "orgOnly", "cReader")) {
            person(key, "RS-X", "RS-P1");
        }
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        grantByOperations("oda", ORG_DATA_ADMIN);
        grantByOperations("hr", "personnel_specialist");
        for (String code : List.of("RS-A", "RS-A1", "RS-B", "RS-C", "RS-X")) {
            unitIds.put(code, orgUnitId(session("oda"), code));
        }
        String[] perms = {EMPLOYEE_READ};
        roleWith("rs-all", "ALL", null, perms);
        roleWith("rs-subtree-a", "CUSTOM_ORG_UNITS", List.of(withDescendants(unitIds.get("RS-A"))), perms);
        roleWith("rs-only-b", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("RS-B"))), perms);
        roleWith("rs-only-c", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("RS-C"))), perms);
        roleWith("rs-nothing", "CUSTOM_ORG_UNITS", List.of(onlyUnit(UUID.randomUUID().toString())), perms);
        roleWith("rs-own", "OWN_ORG_UNIT", null, perms);
        roleWith("rs-org-unit-only", "ALL", null, ORG_UNIT_READ);
        grantOrdinaryRoles(administrator("admin1"), "allReader", "rs-all");
        grantOrdinaryRoles(administrator("admin1"), "subtreeReader", "rs-subtree-a");
        grantOrdinaryRoles(administrator("admin1"), "bReader", "rs-only-b");
        grantOrdinaryRoles(administrator("admin1"), "cReader", "rs-only-c");
        grantOrdinaryRoles(administrator("admin1"), "emptyReader", "rs-nothing");
        grantOrdinaryRoles(administrator("admin1"), "ownReader", "rs-own");
        grantOrdinaryRoles(administrator("admin1"), "brokenReader", "rs-own");
        grantOrdinaryRoles(administrator("admin1"), "orgOnly", "rs-org-unit-only");
        try (Connection workforce = schemaConnection("organization_workforce")) {
            for (String key : List.of("a", "gone", "a1", "b", "c", "multi")) {
                versions(workforce, key, unitOf(key), null, key.equals("gone") ? null : STARTED);
            }
            versions(workforce, "sub", "RS-A", "a", STARTED_SUB);
            UUID multi = UUID.fromString(employeeIdOf("multi"));
            UUID concurrent = AssignmentRoots.create(workforce, stored(multi), AssignmentRoots.CONCURRENT);
            assignmentVersion(workforce, multi, concurrent, AssignmentRoots.CONCURRENT, "RS-B", "RS-P2", null);
            execute(workforce, "update assignments set reports_to_employee_id = ? where employee_id = ?", stored(UUID.fromString(employeeIdOf("a"))), stored(UUID.fromString(employeeIdOf("sub"))));
            execute(workforce, "update employees set employment_status = 'SEPARATED', separated_at = now() where id = ?", stored(UUID.fromString(employeeIdOf("gone"))));
        }
    }

    private static String unitOf(String key) {
        return switch (key) {
            case "a", "gone" -> "RS-A";
            case "a1" -> "RS-A1";
            case "b" -> "RS-B";
            default -> "RS-C";
        };
    }

    /** The employee version and the primary assignment version of the person, both open from the same day; the separated person has no employment start. */
    private void versions(Connection workforce, String key, String unitCode, String reportsToKey, LocalDate started) throws Exception {
        UUID employee = UUID.fromString(employeeIdOf(key));
        boolean employed = started != null;
        execute(workforce, """
                insert into employment_relationship_versions (employment_relationship_id, employment_relationship_version_id, employee_id, effective_from_date, revision_number, employment_status, employment_phase_kind,
                    is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (?, ?, ?, ?, 0, ?, ?, false, false, ?, ?, 'MIGRATION', now(), now())""", EmploymentRelationshipSeeds.relationshipOf(workforce, stored(employee)), Uuids.timeOrdered(NOW), stored(employee), employed ? started : SINCE, employed ? "EMPLOYED" : "SEPARATED", employed ? "REGULAR" : null, started, employed ? "DECLARED" : null);
        UUID root = AssignmentRoots.primaryOf(workforce, stored(employee));
        assignmentVersion(workforce, employee, root, AssignmentRoots.PRIMARY, unitCode, key.equals("c") || key.equals("multi") ? "RS-P2" : "RS-P1", reportsToKey == null ? null : UUID.fromString(employeeIdOf(reportsToKey)));
    }

    private void assignmentVersion(Connection workforce, UUID employee, UUID root, String kind, String unitCode, String positionCode, UUID reportsTo) throws Exception {
        UUID position;
        try (PreparedStatement found = workforce.prepareStatement("select id from positions where code = ?")) {
            found.setString(1, positionCode);
            try (ResultSet rows = found.executeQuery()) {
                rows.next();
                position = rows.getObject(1, UUID.class);
            }
        }
        execute(workforce, """
                insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, revision_number,
                    org_unit_id, position_id, reports_to_employee_id, change_channel_kind, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MIGRATION', now(), now())""", EmploymentRelationshipSeeds.relationshipOf(workforce, stored(employee)), Uuids.timeOrdered(NOW), stored(employee), root, kind, SINCE, kind.equals(AssignmentRoots.PRIMARY) ? 0 : 1, UUID.fromString(unitIds.get(unitCode)), position, reportsTo == null ? null : stored(reportsTo));
    }

    private static void execute(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    @AfterEach
    void organisationWorksAgain() {
        Mockito.reset(organisation);
    }

    private void roleWith(String code, String scope, List<CustomOrgUnit> units, String... permissions) throws Exception {
        MockHttpServletResponse created = createRole(administrator("admin1"), roleBody(code, scope, units, permissions));
        if (created.getStatus() == 202) {
            String request = JsonPath.read(created.getContentAsString(), "$.requestId");
            require(confirm(administrator("admin2"), request), 204, "confirming role " + code);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleIdOf(administrator("admin1"), code)).isPresent());
        } else {
            require(created, 201, "creating role " + code);
        }
    }

    // ---- calls ----

    private MockHttpServletResponse read(String key, String url) throws Exception {
        return mvc.perform(get(url).with(as(session(key)))).andReturn().getResponse();
    }

    private List<Map<String, Object>> itemsOf(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.items");
    }

    /** Every row of the list as the caller gets it, one row per page. */
    private List<Map<String, Object>> all(String key, String query) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 60; page++) {
            MockHttpServletResponse answer = read(key, EMPLOYEES + "?" + query + (query.isEmpty() ? "" : "&") + "limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            require(answer, 200, key + " lists the employees");
            assertThat(itemsOf(answer)).hasSizeLessThanOrEqualTo(1);
            rows.addAll(itemsOf(answer));
            cursor = JsonPath.read(answer.getContentAsString(), "$.nextCursor");
            if (cursor == null) {
                return rows;
            }
        }
        throw new AssertionError("more than 60 pages of one row");
    }

    /** The keys of the people of the world among the rows (the callers are people of the organisation too and are left out). */
    private static Set<String> worldKeys(List<Map<String, Object>> rows) {
        Set<String> keys = new TreeSet<>();
        for (Map<String, Object> row : rows) {
            String code = String.valueOf(row.get("employeeCode"));
            for (String key : WORLD) {
                if (code.equals(employeeCode(key))) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }

    private static void collectKeys(Object node, Set<String> keys) {
        if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                keys.add(String.valueOf(key));
                collectKeys(value, keys);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collectKeys(item, keys));
        }
    }

    // ---- the list ----

    @Test
    @DisplayName("#1171 完成标准第 1 条（数据范围，A1-A3 风格）：本部门范围只看到本部门的人，含子部门范围看到部门及其下属，只含 B 的范围只看到 B（含在 B 有兼职的人），全公司范围看到所有在职的人，名单为空的范围一行也没有")
    void theListFollowsTheCallersDataScope() throws Exception {
        assertThat(worldKeys(all("ownReader", ""))).containsExactly("a", "sub");
        assertThat(worldKeys(all("subtreeReader", ""))).containsExactly("a", "a1", "sub");
        assertThat(worldKeys(all("bReader", ""))).containsExactly("b", "multi");
        assertThat(worldKeys(all("allReader", ""))).containsExactly("a", "a1", "b", "c", "multi", "sub");
        assertThat(all("emptyReader", "")).as("a scope that names no unit gives zero rows, not all rows").isEmpty();
        assertThat(all("emptyReader", "employmentStatus=ALL")).isEmpty();
    }

    @Test
    @DisplayName("#1171 完成标准第 1 条（默认只列在职）：不带 employmentStatus 只有在职的人；ALL 含离职的人；SEPARATED 只有离职的人；离职的人同样受数据范围约束；不认识的取值是 400")
    void theDefaultListsEmployedPeopleOnly() throws Exception {
        assertThat(worldKeys(all("allReader", ""))).doesNotContain("gone");
        assertThat(worldKeys(all("allReader", "employmentStatus=ALL"))).contains("gone", "a", "b");
        assertThat(worldKeys(all("allReader", "employmentStatus=SEPARATED"))).containsExactly("gone");
        assertThat(worldKeys(all("ownReader", "employmentStatus=SEPARATED"))).as("the separated person lives in A").containsExactly("gone");
        assertThat(all("bReader", "employmentStatus=SEPARATED")).isEmpty();
        assertThat(read("allReader", EMPLOYEES + "?employmentStatus=RETIRED").getStatus()).isEqualTo(400);
        Map<String, Object> gone = all("allReader", "employmentStatus=ALL").stream().filter(row -> employeeCode("gone").equals(row.get("employeeCode"))).findFirst().orElseThrow();
        assertThat(gone.get("employmentStatus")).isEqualTo("SEPARATED");
    }

    @Test
    @DisplayName("#1171 完成标准第 1 条（多任职）：一个人的主任职在 C、兼职在 B：只管 B 的人看得到他，只管 C 的范围也看得到他，管不到 B 也管不到 C 的人看不到；这一行列出他的每一个任职（主任职与兼职，各带部门与岗位）")
    void aPersonWithSeveralAssignmentsIsInScopeThroughAny() throws Exception {
        Map<String, Object> multi = all("bReader", "").stream().filter(row -> employeeCode("multi").equals(row.get("employeeCode"))).findFirst().orElseThrow();

        List<Map<String, Object>> assignments = JsonPath.parse(multi).read("$.assignments");
        assertThat(assignments).hasSize(2);
        assertThat(assignments).extracting(item -> item.get("assignmentKind")).containsExactlyInAnyOrder("PRIMARY", "CONCURRENT");
        assertThat(assignments).extracting(item -> item.get("orgUnitId")).containsExactlyInAnyOrder(unitIds.get("RS-C"), unitIds.get("RS-B"));
        assertThat(assignments).extracting(item -> item.get("orgUnitName")).containsExactlyInAnyOrder(unitName("RS-C"), unitName("RS-B"));
        assertThat(assignments).extracting(item -> item.get("positionName")).containsOnly(positionName("RS-P2"));
        assertThat(worldKeys(all("cReader", ""))).containsExactly("c", "multi");
        assertThat(worldKeys(all("subtreeReader", ""))).doesNotContain("multi");
    }

    @Test
    @DisplayName("#1171 完成标准第 1 条（分页与按部门筛选）：limit=1 逐页取遍与一次取全相同；orgUnitId 只留下该部门里有任职的人；每页不超过 limit")
    void pagingAndTheUnitFilter() throws Exception {
        MockHttpServletResponse whole = read("allReader", EMPLOYEES + "?limit=200");
        require(whole, 200, "allReader reads one big page");
        assertThat(worldKeys(itemsOf(whole))).isEqualTo(worldKeys(all("allReader", "")));
        assertThat(worldKeys(all("allReader", "orgUnitId=" + unitIds.get("RS-B")))).containsExactly("b", "multi");
        assertThat(worldKeys(all("allReader", "orgUnitId=" + unitIds.get("RS-A1")))).containsExactly("a1");
        assertThat(all("bReader", "orgUnitId=" + unitIds.get("RS-A"))).as("the filter cannot widen the data scope").isEmpty();
        assertThat(all("subtreeReader", "orgUnitId=" + unitIds.get("RS-C"))).as("the filter cannot widen the data scope").isEmpty();
    }

    // ---- the detail ----

    @Test
    @DisplayName("#1171 完成标准第 2 条（详情）：姓名、工号、状态、入职日期、每个任职的部门与岗位；有直属领导的人给出领导的员工 ID 和姓名，兼职任职没有汇报线；兼职的人详情里有两个任职")
    void theDetailShowsTheRosterAndTheReportsToPerson() throws Exception {
        MockHttpServletResponse sub = read("allReader", EMPLOYEES + "/" + employeeIdOf("sub"));
        require(sub, 200, "allReader reads one employee");
        Map<String, Object> body = JsonPath.parse(sub.getContentAsString()).read("$");
        assertThat(body).containsEntry("employeeId", employeeIdOf("sub")).containsEntry("employeeCode", employeeCode("sub")).containsEntry("displayName", "Synthetic sub")
                .containsEntry("employmentStatus", "EMPLOYED").containsEntry("hireDate", STARTED_SUB.toString());
        List<Map<String, Object>> subAssignments = JsonPath.parse(sub.getContentAsString()).read("$.assignments");
        assertThat(subAssignments).singleElement().satisfies(item -> {
            assertThat(item.get("assignmentKind")).isEqualTo("PRIMARY");
            assertThat(item.get("orgUnitId")).isEqualTo(unitIds.get("RS-A"));
            assertThat(item.get("reportsToEmployeeId")).isEqualTo(employeeIdOf("a"));
            assertThat(item.get("reportsToDisplayName")).isEqualTo("Synthetic a");
        });
        MockHttpServletResponse multi = read("allReader", EMPLOYEES + "/" + employeeIdOf("multi"));
        require(multi, 200, "allReader reads the person with two assignments");
        List<Map<String, Object>> multiAssignments = JsonPath.parse(multi.getContentAsString()).read("$.assignments");
        assertThat(multiAssignments).hasSize(2);
        assertThat(multiAssignments).filteredOn(item -> "CONCURRENT".equals(item.get("assignmentKind"))).singleElement().satisfies(item -> {
            assertThat(item.get("orgUnitId")).isEqualTo(unitIds.get("RS-B"));
            assertThat(item.get("reportsToEmployeeId")).isNull();
        });
    }

    @Test
    @DisplayName("#1171 完成标准第 2 条（范围外的详情）：范围外的员工 ID 是 404（错误码以 _NOT_FOUND 结尾），回答与根本不存在的 ID 一样；每次被拒的读写一条审计 organization.workforce.employee.denied，审计里没有工号")
    void aDetailOutOfScopeIsNotFoundAndAudited() throws Exception {
        int before = auditedActions(DENIED).size();
        String absent = UUID.randomUUID().toString();

        MockHttpServletResponse refused = read("bReader", EMPLOYEES + "/" + employeeIdOf("a"));
        MockHttpServletResponse nothing = read("bReader", EMPLOYEES + "/" + absent);

        assertThat(refused.getStatus()).isEqualTo(404);
        assertThat(nothing.getStatus()).isEqualTo(404);
        assertThat(String.valueOf(JsonPath.<String>read(refused.getContentAsString(), "$.errorCode"))).endsWith("_NOT_FOUND");
        assertThat(JsonPath.<String>read(refused.getContentAsString(), "$.errorCode")).isEqualTo(JsonPath.<String>read(nothing.getContentAsString(), "$.errorCode"));
        assertThat(refused.getContentAsString().replace(employeeIdOf("a"), "<id>")).doesNotContain(employeeCode("a"));
        read("bReader", EMPLOYEES + "/" + employeeIdOf("c"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditedActions(DENIED)).as("one denied entry per out-of-scope read of an existing employee").hasSize(before + 2));
        assertThat(read("bReader", EMPLOYEES + "/" + employeeIdOf("b")).getStatus()).as("an employee in scope is readable").isEqualTo(200);
        assertThat(auditedActions(DENIED)).hasSize(before + 2);
        assertThat(auditEntries(DENIED).stream().map(DataScopeSupport::auditText).collect(java.util.stream.Collectors.joining(" "))).as("no employee code in the audit text")
                .doesNotContain(employeeCode("a"), employeeCode("c"));
    }

    // ---- what is never shown, who may read ----

    @Test
    @DisplayName("#1171 完成标准第 3 条（不出现的字段）：名册行与详情只有约定的键；任何层级的键都不像身份证号、民族、户籍；返回内容里没有邮箱、登录名")
    void noIdentityCardEthnicityOrHouseholdAnywhere() throws Exception {
        MockHttpServletResponse list = read("allReader", EMPLOYEES + "?limit=200&employmentStatus=ALL");
        MockHttpServletResponse detail = read("allReader", EMPLOYEES + "/" + employeeIdOf("sub"));
        require(list, 200, "the roster");
        require(detail, 200, "the detail");

        for (Map<String, Object> row : itemsOf(list)) {
            assertThat(row.keySet()).as("a roster row carries only the agreed keys").isSubsetOf(ROW_KEYS).contains("employeeId", "employeeCode", "displayName", "employmentStatus", "assignments");
        }
        Set<String> keys = new TreeSet<>();
        collectKeys(JsonPath.parse(list.getContentAsString()).read("$"), keys);
        collectKeys(JsonPath.parse(detail.getContentAsString()).read("$"), keys);
        assertThat(keys).noneMatch(key -> FORBIDDEN_KEY.matcher(key).find());
        Map<String, Object> detailBody = JsonPath.parse(detail.getContentAsString()).read("$");
        assertThat(detailBody.keySet()).isSubsetOf(ROW_KEYS);
        List<Map<String, Object>> assignments = JsonPath.parse(detail.getContentAsString()).read("$.assignments");
        assertThat(assignments).allSatisfy(item -> assertThat(item.keySet()).isSubsetOf(ASSIGNMENT_KEYS));
        assertThat(list.getContentAsString()).doesNotContain("@example.test").doesNotContain("f1-login-");
    }

    @Test
    @DisplayName("#1171 完成标准第 6、7 条（谁能读）：持有 employee:read 的角色可读；人事专员（系统角色）可读；超级管理员、组织数据管理员、只有 org-unit:read 的人在名册列表和详情上都是 403；未登录是 401")
    void onlyTheHolderOfTheRosterPermissionMayRead() throws Exception {
        String url = EMPLOYEES + "/" + employeeIdOf("a");
        assertThat(read("hr", EMPLOYEES).getStatus()).as("personnel_specialist holds the code").isEqualTo(200);
        assertThat(read("hr", url).getStatus()).isEqualTo(200);
        assertThat(worldKeys(all("hr", ""))).containsExactly("a", "a1", "b", "c", "multi", "sub");
        for (String key : List.of("oda", "orgOnly")) {
            assertThat(read(key, EMPLOYEES).getStatus()).as(key + " lists").isEqualTo(403);
            assertThat(read(key, url).getStatus()).as(key + " reads one").isEqualTo(403);
        }
        MockHttpServletResponse superList = mvc.perform(get(EMPLOYEES).with(as(administrator("admin1")))).andReturn().getResponse();
        MockHttpServletResponse superDetail = mvc.perform(get(url).with(as(administrator("admin1")))).andReturn().getResponse();
        assertThat(superList.getStatus()).as("super_admin does not hold the roster code: " + superList.getContentAsString()).isEqualTo(403);
        assertThat(superDetail.getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(EMPLOYEES)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get(url)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("#1171 完成标准第 1 条（范围算不出来）：本部门范围的人其所在单位查不出来时，列表是 403、详情是 404，绝不给出更宽的回答")
    void anUnresolvableScopeIsDenied() throws Exception {
        String employee = employeeIdOf("brokenReader");
        require(read("brokenReader", EMPLOYEES), 200, "priming the principal of the caller before the organisation fails");
        doThrow(new IllegalStateException("organisation unavailable")).when(organisation).findEmployee(argThat(id -> id != null && id.value().toString().equals(employee)));

        assertThat(read("brokenReader", EMPLOYEES).getStatus()).isEqualTo(403);
        assertThat(read("brokenReader", EMPLOYEES + "?employmentStatus=ALL").getStatus()).isEqualTo(403);
        assertThat(read("brokenReader", EMPLOYEES + "/" + employeeIdOf("a")).getStatus()).isEqualTo(404);
    }
}
