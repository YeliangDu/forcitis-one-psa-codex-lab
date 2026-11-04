package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1172 completion lines 3-8; ADR-0068 section 2.3 and ADR-0080 section 7.1.
 * Contract fixed here: page {@code items, nextCursor} (null at the end); list/detail rows
 * {@code separationHoldId, personnelCommandId, employeeId, displayName, employeeCode, orgUnitId, orgUnitName,
 * originalEffectiveDate, remainingAvailableSuperAdministratorCount, status}. The department is the primary assignment's
 * department; originalEffectiveDate is the command's effective date (last working day + 1), as an ISO date.
 * The count excludes all held people and future released separations, as the real release review does; it is a number.
 * No new read permission, no scope filter on this company-wide queue. The HELD filter excludes released/withdrawn rows.
 * The inherited FutureHoldSupport builds every hold through the real application path, never by inserting command/hold rows.
 */
class SeparationHoldReadHttpIT extends FutureHoldSupport {
    static final TestDatabase postgres = TestPostgres.database(SeparationHoldReadHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String HOLDS = "/api/v1/organization/separation-holds";
    static final String APPROVE = "organization.workforce:separation-hold:approve";
    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String COUNT = "remainingAvailableSuperAdministratorCount";
    static final Pattern RELEASE_COUNT = Pattern.compile("(?i)(?:remaining|剩余)[^;\\n]{0,50}?([0-9]+)");

    private static void require(MockHttpServletResponse answer, int expected, String why) throws Exception {
        assertThat(answer.getStatus()).as(why + ": " + answer.getContentAsString()).isEqualTo(expected);
    }

    private MockHttpServletResponse read(Staff who, String url) throws Exception {
        return CommandReadHttpSupport.safe(mvc.perform(get(url).with(session(who.session()))).andReturn().getResponse());
    }

    private List<Map<String, Object>> all(Staff who) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        String cursor = null;
        for (int page = 0; page < 80; page++) {
            MockHttpServletResponse answer = read(who, HOLDS + "?status=HELD&limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            require(answer, 200, "paging the held separations");
            List<Map<String, Object>> items = JsonPath.read(answer.getContentAsString(), "$.items");
            assertThat(items).hasSizeLessThanOrEqualTo(1);
            rows.addAll(items);
            cursor = JsonPath.read(answer.getContentAsString(), "$.nextCursor");
            if (cursor == null) return rows;
            assertThat(seen.add(cursor)).as("the hold cursor advances").isTrue();
        }
        throw new AssertionError("more than 80 hold pages");
    }

    private Map<String, Object> listed(Staff who, Held held) throws Exception {
        return all(who).stream().filter(row -> held.hold().toString().equals(row.get("separationHoldId"))).findFirst().orElseThrow();
    }

    private Map<String, Object> detail(Staff who, Held held) throws Exception {
        MockHttpServletResponse answer = read(who, HOLDS + "/" + held.hold());
        require(answer, 200, "reading the held separation");
        return JsonPath.parse(answer.getContentAsString()).read("$");
    }

    private MockHttpServletResponse releaseHttp(Staff who, Held held) throws Exception {
        return CommandReadHttpSupport.safe(mvc.perform(post(HOLDS + "/" + held.hold() + ":release").with(csrf()).with(session(who.session()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Synthetic release comparison\"}")).andReturn().getResponse());
    }

    @Test
    @DisplayName("#1172 完成标准第 3、6 条：列表与详情显示命令、姓名、工号、部门、原生效日与放行所需剩余超管数，任何响应无敏感字段名")
    void heldRowsShowTheReleaseDecisionFacts() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        Map<String, Object> person = rows("select display_name, employee_code from employees where id = ?", stored(scenario.held().employee())).getFirst();
        String unitName = (String) rows("select name from org_units where id = ?", stored(unit)).getFirst().get("name");
        for (Map<String, Object> row : List.of(listed(scenario.releaser(), scenario.held()), detail(scenario.releaser(), scenario.held()))) {
            assertThat(row).containsEntry("separationHoldId", scenario.held().hold().toString()).containsEntry("personnelCommandId", scenario.held().command().toString())
                    .containsEntry("employeeId", scenario.held().employee().toString()).containsEntry("displayName", person.get("display_name"))
                    .containsEntry("employeeCode", person.get("employee_code")).containsEntry("orgUnitId", unit.toString()).containsEntry("orgUnitName", unitName)
                    .containsEntry("originalEffectiveDate", DUE.toString()).containsEntry("status", "HELD").containsEntry(COUNT, 2);
        }
        assertNoEmployeeCodeInAudit(scenario.held());
    }

    @Test
    @DisplayName("#1172 完成标准第 3、5、6 条：同一状态列表计数等于真实 release 审计；停用一名超管前为 2，之后为 1 且放行告警；未来已放行者不重复计入")
    void listCountMatchesReleaseBeforeAndAfterAnAdministratorIsDisabled() throws Exception {
        Staff first = administrator();
        Staff second = administrator();
        Staff stays = administrator();
        Staff extra = administrator();
        Staff releaser = auditor();
        Held one = heldSeparationOn(first, DUE);
        Held two = heldSeparationOn(second, DUE);
        int before = ((Number) listed(releaser, one).get(COUNT)).intValue();
        assertThat(before).isEqualTo(2);
        require(releaseHttp(releaser, one), 200, "release with two remaining");
        assertThat(releaseCount(one)).isEqualTo(before);
        assertThat(all(releaser)).noneMatch(row -> one.hold().toString().equals(row.get("separationHoldId")));

        Staff maintainer = inAdministratorMode(stays);
        MockHttpServletResponse account = read(maintainer, ACCOUNTS + extra.userId());
        require(account, 200, "reading the account to disable");
        MockHttpServletResponse disabled = mvc.perform(post(ACCOUNTS + extra.userId() + ":disable").with(csrf()).with(session(maintainer.session()))
                .header("If-Match", account.getHeader("ETag"))).andReturn().getResponse();
        require(disabled, 204, "disabling one available administrator through HTTP");
        require(read(maintainer, ACCOUNTS + extra.userId()), 200, "reading back the disabled account");
        assertThat(JsonPath.<String>read(read(maintainer, ACCOUNTS + extra.userId()).getContentAsString(), "$.status")).isEqualTo("DISABLED");
        int after = ((Number) listed(releaser, two).get(COUNT)).intValue();
        assertThat(after).isEqualTo(before - 1).isEqualTo(1);
        assertThat(detail(releaser, two)).containsEntry(COUNT, after);
        int mark = logMark();
        require(releaseHttp(releaser, two), 200, "release with one remaining");
        assertThat(releaseCount(two)).isEqualTo(after);
        assertThat(alertsSince(mark)).anySatisfy(text -> assertThat(text).contains(two.hold().toString()).containsPattern("(?i)(only|remaining|剩余).*1|1.*(remain|可用)"));
        assertNoEmployeeCodeInAudit(one);
        assertNoEmployeeCodeInAudit(two);
    }

    private int releaseCount(Held held) throws Exception {
        List<Map<String, Object>> matching = auditQuery(null, null, PersonnelCommandNames.AUDIT_HOLD_RELEASED).stream()
                .filter(entry -> String.valueOf(entry.get("detail")).contains(held.hold().toString())).toList();
        assertThat(matching).hasSize(1);
        var found = RELEASE_COUNT.matcher(String.valueOf(matching.getFirst().get("detail")));
        assertThat(found.find()).as("the actual release audit reports its decision count").isTrue();
        return Integer.parseInt(found.group(1));
    }

    @Test
    @DisplayName("#1172 完成标准第 3 条、ADR-0068 第 2.3 条：列表剩余 0 对应真实 release 拒绝，暂停记录仍可读且为 HELD")
    void zeroRemainingMatchesTheReleaseRefusal() throws Exception {
        Held held = heldSeparationOn(administrator(), DUE);
        Staff releaser = auditor();
        assertThat(listed(releaser, held)).containsEntry(COUNT, 0);
        MockHttpServletResponse refused = releaseHttp(releaser, held);
        require(refused, 422, "the interface refuses zero remaining administrators");
        assertThat(JsonPath.<String>read(refused.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED");
        assertThat(detail(releaser, held)).containsEntry(COUNT, 0).containsEntry("status", "HELD");
        assertNoEmployeeCodeInAudit(held);
    }

    @Test
    @DisplayName("#1172 完成标准第 3、4 条：持放行权者即使数据范围为空也能全公司读列表与详情；每页 1 行遍历等于大页且无重复")
    void approvalPermissionReadsCompanyWideEvenWithAnEmptyScope() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        Staff scoped = scopedReader(APPROVE);
        List<Map<String, Object>> rows = all(scoped);
        assertThat(rows).anyMatch(row -> scenario.held().hold().toString().equals(row.get("separationHoldId")));
        assertThat(detail(scoped, scenario.held())).containsEntry("employeeId", scenario.held().employee().toString());
        MockHttpServletResponse whole = read(scoped, HOLDS + "?status=HELD&limit=200");
        require(whole, 200, "one big hold page");
        List<Map<String, Object>> big = JsonPath.read(whole.getContentAsString(), "$.items");
        assertThat(rows.stream().map(row -> row.get("separationHoldId")).toList()).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(big.stream().map(row -> row.get("separationHoldId")).toList());
        assertThat(JsonPath.<String>read(whole.getContentAsString(), "$.nextCursor")).isNull();
        assertThat(rows).allSatisfy(row -> assertThat(row.get("status")).isEqualTo("HELD"));
    }

    private Staff scopedReader(String permission) throws Exception {
        Staff first = inAdministratorMode(administrator());
        Staff second = inAdministratorMode(administrator());
        Staff reader = account();
        String code = "hold-read-" + Uuids.timeOrdered(NOW);
        String body = "{\"code\":\"" + code + "\",\"name\":\"Synthetic empty scope approver " + code + "\",\"enabled\":true,\"accessScope\":\"CUSTOM_ORG_UNITS\","
                + "\"customOrgUnits\":[{\"orgUnitId\":\"" + UUID.randomUUID() + "\",\"includeDescendants\":false}],\"permissionCodes\":[\"" + permission + "\"]}";
        MockHttpServletResponse created = mvc.perform(post("/api/v1/platform/roles").with(csrf()).with(session(first.session()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        if (created.getStatus() == 202) {
            String request = JsonPath.read(created.getContentAsString(), "$.requestId");
            require(mvc.perform(post("/api/v1/platform/authorization-requests/" + request + ":confirm").with(csrf()).with(session(second.session())))
                    .andReturn().getResponse(), 204, "confirming the approver role");
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                var roles = read(first, "/api/v1/platform/roles?limit=200");
                assertThat(JsonPath.<List<String>>read(roles.getContentAsString(), "$.items[*].code")).contains(code);
            });
        } else require(created, 201, "creating the approver role");
        MockHttpServletResponse account = read(first, ACCOUNTS + reader.userId());
        require(account, 200, "reading the approver account");
        MockHttpServletResponse granted = mvc.perform(post(ACCOUNTS + reader.userId() + ":replace-roles").with(csrf()).with(session(first.session()))
                .header("If-Match", account.getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"employee\",\"" + code + "\"]}")).andReturn().getResponse();
        if (granted.getStatus() == 202) {
            require(mvc.perform(post("/api/v1/platform/authorization-requests/" + JsonPath.<String>read(granted.getContentAsString(), "$.requestId") + ":confirm")
                    .with(csrf()).with(session(second.session()))).andReturn().getResponse(), 204, "confirming the approver grant");
        } else require(granted, 204, "granting the approver role");
        return signedInAgain(reader);
    }

    @Test
    @DisplayName("#1172 完成标准第 3、7 条：仅 org-unit:read 的调用者列表与详情 403，有放行权读未知 id 404，匿名两者 401")
    void permissionUnknownIdAndAnonymous() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        Staff orgOnly = scopedReader("organization.workforce:org-unit:read");
        for (String url : List.of(HOLDS, HOLDS + "/" + scenario.held().hold())) {
            require(read(orgOnly, url), 403, "caller without separation-hold:approve");
            require(CommandReadHttpSupport.safe(mvc.perform(get(url)).andReturn().getResponse()), 401, "anonymous caller");
        }
        MockHttpServletResponse unknown = read(scenario.releaser(), HOLDS + "/" + UUID.randomUUID());
        require(unknown, 404, "unknown hold id");
        assertThat(JsonPath.<String>read(unknown.getContentAsString(), "$.errorCode")).endsWith("_NOT_FOUND");
    }

    @Test
    @DisplayName("#1172 完成标准第 7 条（越权）：没有 separation-hold:approve 的人事专员列表与详情 403，管理员模式的超级管理员持有它（ADR-0028 第 2、3 条）故列表与详情 200；持有者 limit=0 与 limit=201 是 400")
    void otherPrivilegedRolesAreRefusedAndTheLimitHasBounds() throws Exception {
        Scenario scenario = heldOnWithRemaining(1, DUE);
        for (String url : List.of(HOLDS, HOLDS + "/" + scenario.held().hold())) {
            require(read(account("personnel_specialist"), url), 403, "a role without separation-hold:approve");
            require(read(inAdministratorMode(administrator()), url), 200, "the administrator mode holds separation-hold:approve and may read the list (ADR-0028)");
        }
        for (String limit : List.of("0", "201")) {
            require(read(scenario.releaser(), HOLDS + "?status=HELD&limit=" + limit), 400, "limit " + limit);
        }
    }

    private void assertNoEmployeeCodeInAudit(Held held) throws Exception {
        String code = (String) rows("select employee_code from employees where id = ?", stored(held.employee())).getFirst().get("employee_code");
        for (Map<String, Object> entry : auditQuery(null, null, "organization.workforce.")) {
            assertThat(entry.get("detail") + " " + entry.get("changeReason") + " " + entry.get("beforeText") + " " + entry.get("afterText")).doesNotContain(code);
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 4、8 条：待放行两个 GET 登记 NOT_SCOPE_BOUND 与理由，OpenAPI 包含两个 GET")
    void readsAreRegisteredAndPublished() throws Exception {
        CommandReadHttpSupport.assertRegistration(HOLDS, "separationHoldId", "NOT_SCOPE_BOUND");
        MockHttpServletResponse openapi = mvc.perform(get("/v3/api-docs").with(session(auditor().session()))).andReturn().getResponse();
        require(openapi, 200, "OpenAPI document");
        CommandReadHttpSupport.assertOpenApi(openapi, HOLDS, "separationHoldId");
    }
}
