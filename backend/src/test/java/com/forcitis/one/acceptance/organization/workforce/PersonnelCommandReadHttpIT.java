package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * #1172 completion lines 1, 4-8; ADR-0068 sections 1-2, ADR-0026 section 1.7, ADR-0080 section 7.1.
 * Contract fixed here: page {@code items, nextCursor} (null at the end); list and detail rows
 * {@code personnelCommandId, commandKind, employeeId, displayName, employeeCode, effectiveDate, lastWorkingDay,
 * status, createdBy, createdAt, withdrawnBy, withdrawnAt, withdrawalReason, separationHoldId, holdStatus}.
 * Nullable withdrawal and hold fields may be absent. Dates are ISO dates; recorder/withdrawer are account ids.
 * Unresolvable scope is list 403/detail 404, as #730 and the sibling roster IT require, despite the card's zero-row wording.
 * Status/kind accept declared enum values; malformed employeeId is 400, an unknown well-formed employeeId gives no rows.
 */
class PersonnelCommandReadHttpIT extends CommandReadHttpSupport {
    static final TestDatabase postgres = TestPostgres.database(PersonnelCommandReadHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private String held;

    @Override String resource() { return "/api/v1/organization/personnel-commands"; }
    @Override String idField() { return "personnelCommandId"; }
    @Override String permission() { return "organization.workforce:personnel-command:create"; }
    @Override String denied() { return "organization.workforce.personnel-command.denied"; }
    @Override String mode() { return "SCOPED"; }

    @Override
    void seedCommands() throws Exception {
        for (String key : List.of("a", "child", "b")) {
            commands.put(key, enter(key, "REGULARIZE", "effectiveDate", DUE));
        }
        grantByOperations("heldAdmin", SUPER_ADMIN);
        held = enter("heldAdmin", "SEPARATE", "lastWorkingDay", DUE.minusDays(1));
        commands.put("heldAdmin", held);
    }

    /** The relationship of the person once the schema has it (setup only); nothing before. */
    private String relationshipField(String key) throws Exception {
        try (Connection db = schemaConnection("organization_workforce")) {
            UUID relationship = EmploymentRelationshipSeeds.of(db, stored(UUID.fromString(employeeIdOf(key))));
            return relationship == null ? "" : ",\"employmentRelationshipId\":\"" + relationship + "\"";
        }
    }

    private String enter(String key, String kind, String dateField, LocalDate date) throws Exception {
        MockHttpServletResponse answer = write(resource(), "{\"employeeId\":\"" + employeeIdOf(key) + "\",\"commandKind\":\"" + kind
                + "\",\"" + dateField + "\":\"" + date + "\"" + relationshipField(key) + (kind.equals("TRANSFER") ? ",\"targetOrgUnitId\":\"" + unitsByCode.get("READ-B") + "\"" : "") + "}");
        require(answer, 200, "entering a personnel command through HTTP");
        String id = JsonPath.read(answer.getContentAsString(), "$.personnelCommandId");
        commandUnits.put(id, key.equals("b") ? "READ-B" : key.equals("child") ? "READ-CHILD" : "READ-A");
        return id;
    }

    @Test
    @DisplayName("#1172 完成标准第 1、5 条：命令给出种类、员工、生效日或最后工作日、状态、录入人与暂停信息；待放行命令显示 HELD")
    void commandContentAndHeldStatus() throws Exception {
        for (Map<String, Object> row : List.of(row("all", commands.get("a")), listed("all", commands.get("a")))) {
            assertThat(row).containsEntry("commandKind", "REGULARIZE").containsEntry("employeeId", employeeIdOf("a"))
                    .containsEntry("displayName", "Synthetic a").containsEntry("employeeCode", employeeCode("a"))
                    .containsEntry("effectiveDate", DUE.toString()).containsEntry("status", "PENDING").containsEntry("createdBy", userId("writer"));
            assertThat(row.get("createdAt")).isNotNull();
        }
        for (Map<String, Object> row : List.of(row("all", held), listed("all", held))) {
            assertThat(row).containsEntry("commandKind", "SEPARATE").containsEntry("status", "HELD").containsEntry("holdStatus", "HELD")
                    .containsEntry("effectiveDate", DUE.toString()).containsEntry("lastWorkingDay", DUE.minusDays(1).toString());
            assertThat(row.get("separationHoldId")).isNotNull();
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 1 条：status、commandKind、employeeId 单独与组合过滤且不能扩大范围；不认识的值为 400")
    void filtersAreValidatedAndCannotWidenScope() throws Exception {
        assertThat(ids(all("all", "status=HELD"))).containsExactly(held);
        assertThat(ids(all("all", "commandKind=SEPARATE"))).containsExactly(held);
        assertThat(ids(all("all", "employeeId=" + employeeIdOf("b")))).containsExactly(commands.get("b"));
        assertThat(ids(all("all", "status=PENDING&commandKind=REGULARIZE&employeeId=" + employeeIdOf("b")))).containsExactly(commands.get("b"));
        assertThat(all("own", "employeeId=" + employeeIdOf("b"))).isEmpty();
        assertThat(all("own", "status=HELD&commandKind=SEPARATE&employeeId=" + employeeIdOf("b"))).isEmpty();
        assertThat(all("all", "employeeId=" + UUID.randomUUID())).isEmpty();
        for (String query : List.of("status=UNKNOWN", "commandKind=UNKNOWN", "employeeId=UNKNOWN")) {
            require(read("all", resource() + "?" + query), 400, "invalid filter");
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 5、6 条：HTTP 录入、列表找到 PENDING、按该 id 撤回，再列为 WITHDRAWN 并给撤回人、时刻、原因；审计不含工号")
    void enterListWithdrawAndListAgain() throws Exception {
        String id = enter("a", "TRANSFER", "effectiveDate", DUE.plusDays(2));
        assertThat(listed("all", id)).containsEntry("status", "PENDING");
        MockHttpServletResponse withdrawn = write(resource() + "/" + id + ":withdraw", "{\"reason\":\"Synthetic corrected date\"}");
        require(withdrawn, 200, "withdrawing the id found on the page");
        for (Map<String, Object> row : List.of(listed("all", id), row("all", id))) {
            assertThat(row).containsEntry("status", "WITHDRAWN").containsEntry("withdrawnBy", userId("writer"))
                    .containsEntry("withdrawalReason", "Synthetic corrected date");
            assertThat(row.get("withdrawnAt")).isNotNull();
        }
        assertThat(ids(all("all", "status=WITHDRAWN"))).contains(id);
        assertAuditHasNoEmployeeCodes("organization.workforce.personnel-command.");
    }

    @Test
    @DisplayName("#1172 完成标准第 1、2、4 条：本部门、子树、自选部门、全部与空范围；每个可见 id 的详情也可读，逐页无范围泄露")
    void scopeMatrixOnListsAndDetails() throws Exception {
        Map<String, List<String>> expected = Map.of("own", List.of("READ-A"), "subtree", List.of("READ-A", "READ-CHILD"),
                "custom", List.of("READ-B"), "all", List.of(), "empty", List.of());
        for (var entry : expected.entrySet()) {
            List<String> wanted = commandUnits.entrySet().stream().filter(c -> entry.getKey().equals("all") || c.getValue() != null && entry.getValue().contains(c.getValue())).map(Map.Entry::getKey).toList();
            List<String> actual = ids(all(entry.getKey(), ""));
            assertThat(actual).isSubsetOf(commandUnits.keySet());
            if (!entry.getKey().equals("all") && !entry.getKey().equals("empty")) actual = actual.stream().filter(id -> commandUnits.get(id) != null).toList();
            assertThat(actual).containsExactlyInAnyOrderElementsOf(wanted);
            for (String id : wanted) assertThat(row(entry.getKey(), id).get(idField())).isEqualTo(id);
        }
        require(read("empty", resource() + "/" + commands.get("b")), 404, "empty scope detail");
    }

    @Test
    @DisplayName("#1172 完成标准第 1、2、4 条（多角色）：一个角色范围为全部但只有 org-unit:read，另一个角色只含自选部门 B 且有录入权限；调用者只看到 B 的命令，A 的详情 404 并留 denied 审计，宽角色不放大范围")
    void aWideRoleWithoutThePermissionDoesNotWidenTheNarrowOne() throws Exception {
        List<String> inB = commandUnits.entrySet().stream().filter(e -> "READ-B".equals(e.getValue())).map(Map.Entry::getKey).toList();
        List<String> outside = commandUnits.entrySet().stream().filter(e -> e.getValue() != null && !"READ-B".equals(e.getValue())).map(Map.Entry::getKey).toList();
        assertThat(inB).isNotEmpty();
        assertThat(outside).isNotEmpty();
        List<String> seen = ids(all("multi", "")).stream().filter(commandUnits::containsKey).toList();
        assertThat(seen).containsExactlyInAnyOrderElementsOf(inB);
        int before = auditEntries(denied()).size();
        for (String id : outside) require(read("multi", resource() + "/" + id), 404, "a command outside the permitted role's scope");
        eventually(() -> assertThat(auditEntries(denied())).hasSize(before + outside.size()));
    }

    @Test
    @DisplayName("#1172 完成标准第 1、2、6 条：范围外 id 与不存在 id 同为 404 同错误码；只为存在的范围外 id 追加 N 条 denied，审计不含工号")
    void deniedIdsAreIndistinguishableAndAuditedExactly() throws Exception {
        int before = auditEntries(denied()).size();
        MockHttpServletResponse absent = read("custom", resource() + "/" + UUID.randomUUID());
        require(absent, 404, "unknown command");
        assertThat(auditEntries(denied())).hasSize(before);
        List<String> outside = commandUnits.entrySet().stream().filter(e -> e.getValue() != null && !"READ-B".equals(e.getValue())).map(Map.Entry::getKey).toList();
        for (String id : outside) {
            MockHttpServletResponse answer = read("custom", resource() + "/" + id);
            require(answer, 404, "out-of-scope command");
            assertThat(errorCode(answer)).isEqualTo(errorCode(absent));
        }
        eventually(() -> assertThat(auditEntries(denied())).hasSize(before + outside.size()));
        assertAuditHasNoEmployeeCodes(denied());
    }

    @Test
    @DisplayName("#1172 完成标准第 1、2 条（#730 A4）：无法解析范围时列表 403、已存在与不存在的详情 404")
    void unresolvedScopeFailsClosed() throws Exception {
        require(read("broken", CURRENT_SESSION), 200, "priming the cached principal");
        String employee = employeeIdOf("broken");
        doThrow(new IllegalStateException("Synthetic directory failure")).when(organisation).findEmployee(argThat(id -> id != null && employee.equals(id.value().toString())));
        require(read("broken", resource()), 403, "unresolvable list scope");
        require(read("broken", resource() + "/" + commands.get("b")), 404, "unresolvable detail scope");
        require(read("broken", resource() + "/" + UUID.randomUUID()), 404, "unknown detail with unresolvable scope");
    }
}

/** Shared HTTP assertions for the three command reads; the #730 fail-closed scope matrix applies to personnel commands only, org-structure commands are company-wide. */
abstract class CommandReadHttpSupport extends DataScopeSupport {
    static final LocalDate DUE = TODAY.plusDays(10);
    static final Pattern FORBIDDEN_KEY = Pattern.compile("(?i)id.?card|idnumber|identity.?(card|number)|ethnic|household|national.?id");
    final Map<String, String> unitsByCode = new LinkedHashMap<>();
    final Map<String, String> commands = new LinkedHashMap<>();
    final Map<String, String> commandUnits = new LinkedHashMap<>();
    UUID fixtureEntity;
    @MockitoSpyBean OrgDirectory organisation;

    abstract String resource();
    abstract String idField();
    abstract String permission();
    abstract String denied();
    abstract String mode();
    abstract void seedCommands() throws Exception;

    @BeforeAll
    void readWorld() throws Exception {
        unit("READ-A", null);
        unit("READ-CHILD", "READ-A");
        unit("READ-B", null);
        unit("READ-X", null);
        unit("READ-W", null);
        position("READ-P");
        for (String key : List.of("a", "heldAdmin", "own")) person(key, "READ-A", "READ-P");
        person("child", "READ-CHILD", "READ-P");
        person("b", "READ-B", "READ-P");
        for (String key : List.of("admin1", "admin2", "finance", "all", "subtree", "custom", "empty", "broken", "orgOnly", "receiver", "recorder", "multi")) person(key, "READ-X", "READ-P");
        for (String key : List.of("oda", "writer")) person(key, "READ-W", "READ-P");
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        grantByOperations("oda", ORG_DATA_ADMIN);
        grantByOperations("writer", ORG_DATA_ADMIN);
        grantByOperations("writer", "personnel_specialist");
        for (String code : List.of("READ-A", "READ-CHILD", "READ-B", "READ-X")) unitsByCode.put(code, orgUnitId(session("oda"), code));
        role("all", "ALL", null, permission());
        role("own", "OWN_ORG_UNIT", null, permission());
        role("broken", "OWN_ORG_UNIT", null, permission());
        role("subtree", "CUSTOM_ORG_UNITS", List.of(withDescendants(unitsByCode.get("READ-A"))), permission());
        role("custom", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitsByCode.get("READ-B"))), permission());
        role("empty", "CUSTOM_ORG_UNITS", List.of(onlyUnit(UUID.randomUUID().toString())), permission());
        role("orgOnly", "ALL", null, ORG_UNIT_READ);
        role("receiver", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitsByCode.get("READ-X"))), permission());
        role("recorder", "CUSTOM_ORG_UNITS", List.of(onlyUnit(orgUnitId(session("oda"), "READ-W"))), permission());
        grantOrdinaryRoles(administrator("admin1"), "multi", "read-orgOnly", "read-custom");
        seedVersions();
        fixtureEntity = createLegalEntity(signedIn("writer"), "RDA", "Synthetic read entity");
        for (String id : unitsByCode.values()) {
            require(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/organization/org-unit-legal-entities/" + id)
                    .with(csrf()).with(as(session("writer"))).header("If-Match", "\"0\"").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"legalEntityId\":\"" + fixtureEntity + "\",\"effectiveFrom\":\"" + TODAY.minusDays(30) + "\"}")).andReturn().getResponse(), 200, "attributing a fixture unit");
        }
        seedCommands();
    }

    /** The older helpers accept a protected record; adapt the cookie of the real DataScopeSupport login without signing in as a different WeCom id. */
    SignedIn signedIn(String key) throws Exception {
        var constructor = SignedIn.class.getDeclaredConstructor(UUID.class, UUID.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(UUID.fromString(userId(key)), UUID.fromString(employeeIdOf(key)), session(key));
    }

    private void role(String key, String scope, List<CustomOrgUnit> units, String permission) throws Exception {
        String code = "read-" + key;
        MockHttpServletResponse made = createRole(administrator("admin1"), roleBody(code, scope, units, permission));
        if (made.getStatus() == 202) {
            require(confirm(administrator("admin2"), JsonPath.read(made.getContentAsString(), "$.requestId")), 204, "confirming a sensitive role");
            eventually(() -> assertThat(roleIdOf(administrator("admin1"), code)).isPresent());
        } else require(made, 201, "creating a reader role");
        grantOrdinaryRoles(administrator("admin1"), key, code);
    }

    private void seedVersions() throws Exception {
        try (Connection db = schemaConnection("organization_workforce")) {
            for (var unit : unitsByCode.entrySet()) run(db, """
                    insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, parent_org_unit_id, status, region_kind, is_delivery_unit)
                    values (?, ?, ?, ?, ?, 'ACTIVE', 'HEADQUARTERS', false)""", Uuids.timeOrdered(NOW), UUID.fromString(unit.getValue()), LocalDate.of(2026, 1, 1),
                    unitName(unit.getKey()), unit.getKey().equals("READ-CHILD") ? UUID.fromString(unitsByCode.get("READ-A")) : null);
            for (String key : List.of("a", "child", "b", "heldAdmin")) {
                UUID employee = UUID.fromString(employeeIdOf(key));
                run(db, EmploymentRelationshipSeeds.employeeVersion("""
                        insert into employee_versions (employee_version_id, employee_id, effective_from_date, employment_status, employment_phase_kind,
                          is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                        values (?, ?, ?, 'EMPLOYED', 'PROBATION', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())""", EmploymentRelationshipSeeds.of(db, stored(employee))), EmploymentRelationshipSeeds.arguments(EmploymentRelationshipSeeds.of(db, stored(employee)), Uuids.timeOrdered(NOW), stored(employee), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)));
                run(db, EmploymentRelationshipSeeds.assignmentVersionSelect("""
                        insert into assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                          org_unit_id, position_id, change_channel_kind)
                        select ?, a.employee_id, ?, 'PRIMARY', ?, a.org_unit_id, a.position_id, 'MIGRATION' from assignments a where a.employee_id = ?""", EmploymentRelationshipSeeds.of(db, stored(employee))), EmploymentRelationshipSeeds.arguments(EmploymentRelationshipSeeds.of(db, stored(employee)), Uuids.timeOrdered(NOW), AssignmentRoots.primaryOf(db, stored(employee)), LocalDate.of(2026, 1, 1), stored(employee)));
            }
        }
    }

    static void run(Connection db, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            statement.executeUpdate();
        }
    }

    @AfterEach void restoreDirectory() { Mockito.reset(organisation); }

    MockHttpServletResponse read(String key, String url) throws Exception {
        return safe(mvc.perform(get(url).with(as(session(key)))).andReturn().getResponse());
    }

    MockHttpServletResponse write(String url, String body) throws Exception {
        return safe(mvc.perform(post(url).with(csrf()).with(as(session("writer"))).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse());
    }

    static MockHttpServletResponse safe(MockHttpServletResponse answer) throws Exception {
        if (!answer.getContentAsString().isBlank()) assertSafeKeys(JsonPath.parse(answer.getContentAsString()).read("$"));
        return answer;
    }

    private static void assertSafeKeys(Object node) {
        if (node instanceof Map<?, ?> map) map.forEach((key, value) -> {
            assertThat(FORBIDDEN_KEY.matcher(String.valueOf(key)).find()).as("forbidden response key %s", key).isFalse();
            assertSafeKeys(value);
        });
        else if (node instanceof List<?> list) list.forEach(CommandReadHttpSupport::assertSafeKeys);
    }

    List<Map<String, Object>> all(String key, String query) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        String cursor = null;
        for (int page = 0; page < 40; page++) {
            MockHttpServletResponse answer = read(key, resource() + "?" + query + (query.isEmpty() ? "" : "&") + "limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            require(answer, 200, "reading every cursor page");
            assertThat(items(answer)).hasSizeLessThanOrEqualTo(1);
            rows.addAll(items(answer));
            cursor = JsonPath.read(answer.getContentAsString(), "$.nextCursor");
            if (cursor == null) return rows;
            assertThat(seen.add(cursor)).as("the cursor advances").isTrue();
        }
        throw new AssertionError("more than 40 pages");
    }

    List<String> ids(List<Map<String, Object>> rows) { return field(rows, idField()); }
    Map<String, Object> listed(String key, String id) throws Exception { return all(key, "").stream().filter(row -> id.equals(row.get(idField()))).findFirst().orElseThrow(); }
    Map<String, Object> row(String key, String id) throws Exception {
        MockHttpServletResponse answer = read(key, resource() + "/" + id);
        require(answer, 200, "reading a command detail");
        return JsonPath.parse(answer.getContentAsString()).read("$");
    }

    void assertAuditHasNoEmployeeCodes(String action) throws Exception {
        for (Map<String, Object> entry : auditEntries(action)) for (String key : List.of("a", "child", "b", "heldAdmin"))
            assertThat(auditText(entry)).doesNotContain(employeeCode(key));
    }

    @Test
    @DisplayName("#1172 完成标准第 4、6 条：limit=1 遍历等于大页且没有重复；所有响应键不含敏感列名")
    void pagingHasNoMissingOrDuplicateRows() throws Exception {
        MockHttpServletResponse whole = read("all", resource() + "?limit=200");
        require(whole, 200, "one big page");
        assertThat(JsonPath.<String>read(whole.getContentAsString(), "$.nextCursor")).isNull();
        List<String> paged = ids(all("all", ""));
        assertThat(paged).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids(items(whole)));
    }

    @Test
    @DisplayName("#1172 完成标准第 7 条：只有 org-unit:read 的调用者列表与详情 403；匿名两者 401")
    void createPermissionIsRequiredAndAnonymousIsUnauthorized() throws Exception {
        for (String url : List.of(resource(), resource() + "/" + commands.get("b"))) {
            require(read("orgOnly", url), 403, "reader without the create permission");
            require(safe(mvc.perform(get(url)).andReturn().getResponse()), 401, "anonymous reader");
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 7 条（越权）：超级管理员（管理员模式）没有该命令的录入权限，列表与详情 403；limit=0 与 limit=201 是 400")
    void superAdministratorIsRefusedAndTheLimitHasBounds() throws Exception {
        String admin = administrator("admin1");
        for (String url : List.of(resource(), resource() + "/" + commands.get("b"))) {
            require(safe(mvc.perform(get(url).with(as(admin))).andReturn().getResponse()), 403, "a super administrator without the create permission");
        }
        for (String limit : List.of("0", "201")) {
            require(read("all", resource() + "?limit=" + limit), 400, "limit " + limit);
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 4、8 条：两个 GET 登记（人事命令 SCOPED、组织结构命令 NOT_SCOPE_BOUND）与理由，OpenAPI 包含两个 GET")
    void readsAreRegisteredAndPublished() throws Exception {
        assertRegistration(resource(), idField(), mode());
        MockHttpServletResponse openapi = mvc.perform(get("/v3/api-docs").with(as(session("writer")))).andReturn().getResponse();
        require(openapi, 200, "OpenAPI document");
        assertOpenApi(openapi, resource(), idField());
    }

    static void assertRegistration(String resource, String id, String mode) throws Exception {
        List<String> lines = Files.readAllLines(Path.of("architecture/data-scope-read-paths.txt"));
        for (String path : List.of(resource, resource + "/{" + id + "}")) {
            List<String> found = lines.stream().filter(line -> line.startsWith("GET " + path + " | ")).toList();
            assertThat(found).hasSize(1);
            String[] parts = found.getFirst().split("\\|", 3);
            assertThat(parts[1].trim()).isEqualTo(mode);
            assertThat(parts[2].trim()).isNotBlank();
        }
    }

    static void assertOpenApi(MockHttpServletResponse answer, String resource, String id) throws Exception {
        Map<String, Map<String, Object>> paths = JsonPath.read(answer.getContentAsString(), "$.paths");
        for (String path : List.of(resource, resource + "/{" + id + "}")) assertThat(paths.get(path)).isNotNull().containsKey("get");
    }
}
