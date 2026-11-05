package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.APPROVED;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.ASSIGN_LEGAL_ENTITY;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.AWAITING;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.COUNTERSIGNS;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.ESCALATED_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.FINANCE_HEAD_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.KIND_CHANGE;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.KIND_WITHDRAWAL;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.LAPSED;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.REJECTED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PERSONNEL_SPECIALIST_ROLE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1170 (acceptance part): the web exposure of the countersign use cases, {@code POST /api/v1/organization/attribution-countersigns/{id}:approve},
 * {@code :reject} and {@code GET /api/v1/organization/attribution-countersigns?status=&limit=&cursor=}. The use cases are unchanged, so the rules
 * are proved by CountersignApprovalIT, OrgCountersignApprovalIT, CountersignRejectionIT and CountersignAuthorityIT; here only the mapping to HTTP
 * is proved, with the commands entered through HTTP only (personnel commands, and org commands through #1169). A caller who holds the permission
 * but may not decide keeps 403 on POST (lead's proposal on the card, not the 404 of reads). Read-back is SQL on the stored rows, because the web
 * answers 200 without a body.
 */
class CountersignDecisionHttpIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignDecisionHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String PATH = "/api/v1/organization/attribution-countersigns";
    private static final String PERSONNEL = "/api/v1/organization/personnel-commands";
    private static final String ORG = "/api/v1/organization/org-structure-commands";
    private static final String NOT_FOUND = "ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND";
    private static final String FORBIDDEN = "ORGANIZATION_WORKFORCE_COUNTERSIGN_FORBIDDEN";
    private static final String NOT_OPEN = "ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN";
    private static final LocalDate LATER = TODAY.plusDays(10);

    // ---- http ----

    private static String body(Object... keyValues) {
        StringBuilder body = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] == null) {
                continue;
            }
            body.append(body.length() > 1 ? "," : "").append('"').append(keyValues[i]).append("\":\"")
                    .append(keyValues[i + 1].toString().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return body.append('}').toString();
    }

    private MockHttpServletResponse send(Staff who, String path, String content) throws Exception {
        return mvc.perform(post(path).with(csrf()).with(session(who.session())).contentType(MediaType.APPLICATION_JSON).content(content)).andReturn().getResponse();
    }

    private MockHttpServletResponse read(Staff who, String path) throws Exception {
        return mvc.perform(get(path).with(session(who.session()))).andReturn().getResponse();
    }

    private MockHttpServletResponse approveHttp(Staff who, UUID request, String content) throws Exception {
        return send(who, PATH + "/" + request + ":approve", content);
    }

    private MockHttpServletResponse rejectHttp(Staff who, UUID request, String reason) throws Exception {
        return send(who, PATH + "/" + request + ":reject", body("reason", reason));
    }

    private static void assertOk(MockHttpServletResponse answer) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getContentAsString()).as("the answer carries no body").isEmpty();
    }

    private static void assertAnswered(MockHttpServletResponse answer, int status, String code) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(status);
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo(code);
    }

    // ---- worlds (entered through HTTP only) ----

    private record Waiting(UUID employee, UUID command, Staff drafter) {
    }

    /** A TRANSFER of an inheriting employee of E1 to the unit of E2, entered by the person: waits for both heads. */
    private Waiting personnelBy(Staff drafter) throws Exception {
        UUID employee = inheritingIn(topE1);
        MockHttpServletResponse entered = send(drafter, PERSONNEL, body("employeeId", employee, "commandKind", "TRANSFER", "effectiveDate", LATER, "targetOrgUnitId", topE2));
        assertThat(entered.getStatus()).as("entering the transfer: " + entered.getContentAsString()).isEqualTo(200);
        UUID command = UUID.fromString(JsonPath.read(entered.getContentAsString(), "$.personnelCommandId"));
        assertThat(personnelStatus(command)).isEqualTo(AWAITING);
        return new Waiting(employee, command, drafter);
    }

    private Waiting personnel() throws Exception {
        return personnelBy(hr);
    }

    /** An ASSIGN_LEGAL_ENTITY to E2 of a unit of E1 with an inheriting employee, entered by the org data admin: waits for both heads. */
    private UUID orgWaiting() throws Exception {
        return orgWaitingBy(orgAdmin);
    }

    private UUID orgWaitingBy(Staff drafter) throws Exception {
        UUID unit = seedUnit(topE1);
        inheritingIn(unit);
        MockHttpServletResponse entered = send(drafter, ORG, body("targetKind", "ORG_UNIT", "commandKind", ASSIGN_LEGAL_ENTITY, "targetOrgUnitId", unit,
                "effectiveDate", LATER, "targetLegalEntityId", e2));
        assertThat(entered.getStatus()).as("entering the assignment: " + entered.getContentAsString()).isEqualTo(200);
        UUID command = UUID.fromString(JsonPath.read(entered.getContentAsString(), "$.orgStructureCommandId"));
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        return command;
    }

    private void withdrawPersonnelHttp(Staff who, UUID command) throws Exception {
        assertOk(send(who, PERSONNEL + "/" + command + ":withdraw", body("reason", "synthetic: plan changed")));
    }

    private void withdrawOrgHttp(Staff who, UUID command) throws Exception {
        assertOk(send(who, ORG + "/" + command + ":withdraw", body("reason", "synthetic: plan changed")));
    }

    private void approveBothHttp(UUID command, boolean org) throws Exception {
        assertOk(approveHttp(headE1, request(command, org, KIND_CHANGE, e1), "{}"));
        assertOk(approveHttp(headE2, request(command, org, KIND_CHANGE, e2), "{}"));
    }

    // ---- reads of the stored rows ----

    private String personnelStatus(UUID command) throws SQLException {
        return (String) rows("select status from personnel_commands where personnel_command_id = ?", command).get(0).get("status");
    }

    private Map<String, Object> row(UUID command, boolean org, String kind, UUID entity) throws SQLException {
        return org ? request(command, kind, entity)
                : rows("select * from " + COUNTERSIGNS + " where personnel_command_id = ? and request_kind = ? and legal_entity_id = ? order by request_sequence_number desc",
                        command, kind, entity).get(0);
    }

    private UUID request(UUID command, boolean org, String kind, UUID entity) throws SQLException {
        return (UUID) row(command, org, kind, entity).get("attribution_countersign_id");
    }

    private String decisionOf(UUID command, boolean org, String kind, UUID entity) throws SQLException {
        return (String) row(command, org, kind, entity).get("decision_kind");
    }

    private long versionsWrittenBy(UUID command) throws SQLException {
        long total = 0;
        for (String table : List.of("employment_relationship_versions", "assignment_versions", "employee_legal_entities")) {
            total += (long) rows("select count(*) as n from " + table + " where change_command_id = ?", command).get(0).get("n");
        }
        return total;
    }

    private long currentVersionsWrittenBy(UUID command) throws SQLException {
        long total = 0;
        for (String table : List.of("employment_relationship_versions", "assignment_versions", "employee_legal_entities")) {
            total += (long) rows("select count(*) as n from " + table + " where change_command_id = ? and superseded_at is null", command).get(0).get("n");
        }
        return total;
    }

    private void escalate(UUID request, int level) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update " + COUNTERSIGNS + " set escalation_level_number = ? where attribution_countersign_id = ?", (short) level, request);
        }
    }

    private LocalDate dueOf(UUID request) throws SQLException {
        return (LocalDate) rows("select due_on_date from " + COUNTERSIGNS + " where attribution_countersign_id = ?", request).get(0).get("due_on_date");
    }

    private List<String> listed(Staff who, String query) throws Exception {
        MockHttpServletResponse answer = read(who, PATH + query);
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        return JsonPath.read(answer.getContentAsString(), "$.items[*].attributionCountersignId");
    }

    // ---- approve ----

    @Test
    @DisplayName("#1170 完成标准第 1 条（人事命令，仅 HTTP 录入）：两位财务负责人各批准自己法人的请求：每次 200 无响应体；第一次后命令仍待会签；最后一次批准后命令转待生效并写入它的版本，决定人记为批准的账号，理由随请求保存")
    void approvingBothRequestsOfAPersonnelCommand() throws Exception {
        Waiting waiting = personnel();
        assertThat(versionsWrittenBy(waiting.command())).isZero();

        assertOk(approveHttp(headE1, request(waiting.command(), false, KIND_CHANGE, e1), "{}"));

        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e1)).isEqualTo(APPROVED);
        assertThat(personnelStatus(waiting.command())).isEqualTo(AWAITING);
        assertThat(versionsWrittenBy(waiting.command())).isZero();
        assertOk(approveHttp(headE2, request(waiting.command(), false, KIND_CHANGE, e2), body("reason", "synthetic: agreed")));

        assertThat(personnelStatus(waiting.command())).isIn(PENDING, "EFFECTIVE");
        assertThat(versionsWrittenBy(waiting.command())).isPositive();
        assertThat(row(waiting.command(), false, KIND_CHANGE, e1).get("decided_by")).isEqualTo(headE1.userId());
        assertThat(row(waiting.command(), false, KIND_CHANGE, e2).get("decided_by")).isEqualTo(headE2.userId());
        assertThat(row(waiting.command(), false, KIND_CHANGE, e2).get("decision_reason")).isEqualTo("synthetic: agreed");
    }

    @Test
    @DisplayName("#1170 完成标准第 1 条（组织命令，经 #1169 的 HTTP 录入）：两位财务负责人各批准自己法人的请求：每次 200 无响应体；第一次后命令仍待会签；最后一次批准后命令转待生效")
    void approvingBothRequestsOfAnOrgCommand() throws Exception {
        UUID command = orgWaiting();

        assertOk(approveHttp(headE1, request(command, true, KIND_CHANGE, e1), "{}"));

        assertThat(decisionOf(command, true, KIND_CHANGE, e1)).isEqualTo(APPROVED);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        assertOk(approveHttp(headE2, request(command, true, KIND_CHANGE, e2), body("reason", "synthetic: agreed")));

        assertThat(statusOf(command)).isEqualTo(PENDING);
        assertThat(row(command, true, KIND_CHANGE, e2).get("decided_by")).isEqualTo(headE2.userId());
        assertThat(row(command, true, KIND_CHANGE, e2).get("decision_reason")).isEqualTo("synthetic: agreed");
    }

    // ---- reject ----

    @Test
    @DisplayName("#1170 完成标准第 2 条（CHANGE 请求，人事命令）：一位负责人拒签 → 200 无响应体；命令转已撤回，不写版本；该请求 REJECTED 并带理由，另一行 LAPSED")
    void rejectingAChangeRequestWithdrawsAPersonnelCommand() throws Exception {
        Waiting waiting = personnel();

        assertOk(rejectHttp(headE2, request(waiting.command(), false, KIND_CHANGE, e2), "synthetic: not agreed"));

        assertThat(personnelStatus(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(versionsWrittenBy(waiting.command())).isZero();
        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e2)).isEqualTo(REJECTED);
        assertThat(row(waiting.command(), false, KIND_CHANGE, e2).get("decision_reason")).isEqualTo("synthetic: not agreed");
        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e1)).isEqualTo(LAPSED);
    }

    @Test
    @DisplayName("#1170 完成标准第 2 条（CHANGE 请求，组织命令）：一位负责人拒签 → 200 无响应体；命令转已撤回；该请求 REJECTED 并带理由，另一行 LAPSED")
    void rejectingAChangeRequestWithdrawsAnOrgCommand() throws Exception {
        UUID command = orgWaiting();

        assertOk(rejectHttp(headE1, request(command, true, KIND_CHANGE, e1), "synthetic: not agreed"));

        assertThat(statusOf(command)).isEqualTo(WITHDRAWN);
        assertThat(decisionOf(command, true, KIND_CHANGE, e1)).isEqualTo(REJECTED);
        assertThat(row(command, true, KIND_CHANGE, e1).get("decision_reason")).isEqualTo("synthetic: not agreed");
        assertThat(decisionOf(command, true, KIND_CHANGE, e2)).isEqualTo(LAPSED);
    }

    @Test
    @DisplayName("#1170 完成标准第 2 条（WITHDRAWAL 请求，人事命令）：已会签的待生效命令被撤回后，一位负责人拒签撤回请求 → 200；命令仍待生效、版本仍在；该请求 REJECTED，另一行 LAPSED")
    void rejectingAWithdrawalRequestLeavesAPersonnelCommandStanding() throws Exception {
        Waiting waiting = personnel();
        approveBothHttp(waiting.command(), false);
        withdrawPersonnelHttp(hr, waiting.command());
        long versions = currentVersionsWrittenBy(waiting.command());
        assertThat(versions).isPositive();

        assertOk(rejectHttp(headE2, request(waiting.command(), false, KIND_WITHDRAWAL, e2), "synthetic: the move is agreed"));

        assertThat(personnelStatus(waiting.command())).isEqualTo(PENDING);
        assertThat(currentVersionsWrittenBy(waiting.command())).isEqualTo(versions);
        assertThat(decisionOf(waiting.command(), false, KIND_WITHDRAWAL, e2)).isEqualTo(REJECTED);
        assertThat(decisionOf(waiting.command(), false, KIND_WITHDRAWAL, e1)).isEqualTo(LAPSED);
    }

    @Test
    @DisplayName("#1170 完成标准第 2 条（WITHDRAWAL 请求，组织命令）：已会签的待生效命令被撤回后，一位负责人拒签撤回请求 → 200；命令仍待生效；该请求 REJECTED，另一行 LAPSED")
    void rejectingAWithdrawalRequestLeavesAnOrgCommandStanding() throws Exception {
        UUID command = orgWaiting();
        approveBothHttp(command, true);
        withdrawOrgHttp(orgAdmin, command);

        assertOk(rejectHttp(headE1, request(command, true, KIND_WITHDRAWAL, e1), "synthetic: the move is agreed"));

        assertThat(statusOf(command)).isEqualTo(PENDING);
        assertThat(decisionOf(command, true, KIND_WITHDRAWAL, e1)).isEqualTo(REJECTED);
        assertThat(decisionOf(command, true, KIND_WITHDRAWAL, e2)).isEqualTo(LAPSED);
    }

    // ---- list ----

    @Test
    @DisplayName("#1170 完成标准第 3 条（范围与默认状态）：默认只列 OPEN；E1 的负责人只看到 E1 的请求（人事命令与组织命令各一），E2 的负责人只看到 E2 的；没有任何可见请求的人（E3 的负责人、未绑定法人的负责人）得到 200 和空页（无下一页）；批准后该行离开 OPEN、出现在 status=APPROVED")
    void theListHoldsWhatTheCallerMayDecide() throws Exception {
        Waiting waiting = personnel();
        UUID org = orgWaiting();
        UUID personnelE1 = request(waiting.command(), false, KIND_CHANGE, e1);
        UUID personnelE2 = request(waiting.command(), false, KIND_CHANGE, e2);
        UUID orgE1 = request(org, true, KIND_CHANGE, e1);
        UUID orgE2 = request(org, true, KIND_CHANGE, e2);

        assertThat(listed(headE1, "").stream().map(UUID::fromString)).containsExactlyInAnyOrder(personnelE1, orgE1);
        assertThat(listed(headE2, "?status=OPEN").stream().map(UUID::fromString)).containsExactlyInAnyOrder(personnelE2, orgE2);
        for (Staff nobody : List.of(headE3, person(FINANCE_HEAD_ROLE))) {
            MockHttpServletResponse answer = read(nobody, PATH);
            assertThat(answer.getStatus()).isEqualTo(200);
            assertThat((List<?>) JsonPath.read(answer.getContentAsString(), "$.items")).isEmpty();
            assertThat((Object) JsonPath.read(answer.getContentAsString(), "$.nextCursor")).isNull();
        }

        assertOk(approveHttp(headE1, personnelE1, "{}"));

        assertThat(listed(headE1, "").stream().map(UUID::fromString)).containsExactly(orgE1);
        assertThat(listed(headE1, "?status=APPROVED").stream().map(UUID::fromString)).containsExactly(personnelE1);
        for (String status : List.of("REJECTED", "LAPSED")) {
            assertThat(listed(headE1, "?status=" + status)).as(status).isEmpty();
        }
    }

    @Test
    @DisplayName("#1170 完成标准第 3 条（排序与分页）：同一天到期的两行都排在晚几天录入、到期日更晚的第三行之前（同日两行之间的先后在此不单独断言）；先录入但到期日更晚的行排在后录入但到期日更早的行之后（工作日历在两次录入之间改过，到期日先于 id）；逐页取（limit=1，沿游标）得到的顺序与一次取完相同，没有重复，游标用尽后下一页为空")
    void theListIsOrderedByDueDateThenIdAndPaged() throws Exception {
        UUID first = request(personnel().command(), false, KIND_CHANGE, e1);
        UUID second = request(personnel().command(), false, KIND_CHANGE, e1);
        setToday(TODAY.plusDays(5));
        UUID later = request(personnel().command(), false, KIND_CHANGE, e1);
        assertThat(dueOf(later)).isAfter(dueOf(first));
        assertThat(dueOf(second)).isEqualTo(dueOf(first));

        List<UUID> whole = listed(headE1, "").stream().map(UUID::fromString).toList();

        assertThat(whole).containsSubsequence(first, second, later);
        List<UUID> paged = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletResponse page = read(headE1, PATH + "?limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            assertThat(page.getStatus()).as(page.getContentAsString()).isEqualTo(200);
            List<String> items = JsonPath.read(page.getContentAsString(), "$.items[*].attributionCountersignId");
            assertThat(items).hasSize(1);
            paged.add(UUID.fromString(items.get(0)));
            cursor = JsonPath.read(page.getContentAsString(), "$.nextCursor");
        } while (cursor != null && paged.size() <= whole.size());
        assertThat(paged).isEqualTo(whole);
    }

    @Test
    @DisplayName("#1170 完成标准第 3 条（先到期的在前，与 id 先后无关）：先录入的请求因工作日历当时有假日而到期更晚，之后日历改掉再录入的请求到期更早；列表里后者在前")
    void theDueDateComesBeforeTheId() throws Exception {
        Staff maintainer = person("org_data_admin");
        maintainCalendar(maintainer, "{\"calendarDate\":\"" + TODAY.plusDays(1) + "\",\"dayKind\":\"HOLIDAY\"},{\"calendarDate\":\"" + TODAY.plusDays(2) + "\",\"dayKind\":\"HOLIDAY\"}");
        UUID slow = request(personnel().command(), false, KIND_CHANGE, e1);
        maintainCalendar(maintainer, "");
        UUID quick = request(personnel().command(), false, KIND_CHANGE, e1);

        assertThat(dueOf(slow)).isAfter(dueOf(quick));
        assertThat(slow.toString().compareTo(quick.toString())).as("the later entry was made second").isNegative();
        assertThat(listed(headE1, "").stream().map(UUID::fromString)).containsSubsequence(quick, slow);
    }

    /** Re-maintains 2026 through the attendance endpoint; the first call creates it (If-Match 0), later ones take the stored ETag. */
    private void maintainCalendar(Staff maintainer, String days) throws Exception {
        String year = "/api/v1/organization/work-calendars/2026";
        MockHttpServletResponse stored = read(maintainer, year);
        String tag = stored.getStatus() == 200 ? stored.getHeader("ETag") : "\"0\"";
        MockHttpServletResponse maintained = mvc.perform(put(year).header("If-Match", tag).with(csrf()).with(session(maintainer.session()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":[" + days + "],\"lockedDaysConfirmed\":true}")).andReturn().getResponse();
        assertThat(maintained.getStatus()).as("maintaining 2026: " + maintained.getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("#1170 完成标准第 3 条（条目内容）：条目带人员显示名但没有员工编号（详情接口仍带编号）；人事命令与组织命令的条目都带请求类型 CHANGE 和状态 OPEN")
    void aListedItemShowsTheNameButNotTheEmployeeNumber() throws Exception {
        Waiting waiting = personnel();
        UUID id = request(waiting.command(), false, KIND_CHANGE, e1);
        MockHttpServletResponse detail = read(headE1, PATH + "/" + id);
        assertThat(detail.getStatus()).isEqualTo(200);
        String name = JsonPath.read(detail.getContentAsString(), "$.command.employee.displayName");
        String number = JsonPath.read(detail.getContentAsString(), "$.command.employee.employeeCode");
        assertThat(name).isNotBlank();
        assertThat(number).isNotBlank();

        MockHttpServletResponse page = read(headE1, PATH);

        assertThat(page.getStatus()).isEqualTo(200);
        List<Map<String, Object>> found = JsonPath.read(page.getContentAsString(), "$.items[?(@.attributionCountersignId == '" + id + "')]");
        assertThat(found).hasSize(1);
        Map<String, Object> item = found.get(0);
        assertThat(item.toString()).contains(name).doesNotContain(number);
        assertThat(item).doesNotContainKey("employeeNumber").doesNotContainKey("employeeCode");
        assertThat(item.get("requestKind")).isEqualTo(KIND_CHANGE);
        assertThat(item.get("status")).isEqualTo("OPEN");
        UUID orgId = request(orgWaiting(), true, KIND_CHANGE, e1);
        List<Map<String, Object>> orgFound = JsonPath.read(read(headE1, PATH).getContentAsString(), "$.items[?(@.attributionCountersignId == '" + orgId + "')]");
        assertThat(orgFound).hasSize(1);
        assertThat(orgFound.get(0)).doesNotContainKey("employeeNumber").doesNotContainKey("employeeCode");
        assertThat(orgFound.get(0).get("requestKind")).isEqualTo(KIND_CHANGE);
        assertThat(orgFound.get(0).get("status")).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("#1170 完成标准第 3 条（未知状态）：status 不是 OPEN、APPROVED、REJECTED、LAPSED 之一 → 400；四个合法值都 200")
    void anUnknownStatusIsABadRequest() throws Exception {
        for (String status : List.of("OPEN", "APPROVED", "REJECTED", "LAPSED")) {
            assertThat(read(headE1, PATH + "?status=" + status).getStatus()).as(status).isEqualTo(200);
        }
        for (String status : List.of("PENDING", "ALL")) {
            assertThat(read(headE1, PATH + "?status=" + status).getStatus()).as("status '" + status + "'").isEqualTo(400);
        }
    }

    @Test
    @DisplayName("#1170 完成标准第 3 条（升级后的业务管理员）：业务管理员在列表里只看到升级等级不低于 2 的请求；等级 0、1 的不可见")
    void theEscalatedAdministratorListsOnlyEscalatedRequests() throws Exception {
        Waiting waiting = personnel();
        UUID level1 = request(waiting.command(), false, KIND_CHANGE, e1);
        UUID level2 = request(waiting.command(), false, KIND_CHANGE, e2);
        escalate(level1, 1);
        Staff administrator = person(ESCALATED_ROLE);
        assertThat(listed(administrator, "").stream().map(UUID::fromString)).doesNotContain(level1, level2);

        escalate(level2, 2);

        assertThat(listed(administrator, "").stream().map(UUID::fromString)).contains(level2).doesNotContain(level1);
        assertThat(listed(headE2, "").stream().map(UUID::fromString)).as("escalating does not widen the heads' view").doesNotContain(level1);
        assertThat(listed(headE3, "").stream().map(UUID::fromString)).doesNotContain(level1, level2);
    }

    // ---- refusals ----

    @Test
    @DisplayName("#1170 完成标准第 4 条（未知编号）：批准或拒签不存在的请求 → 404 ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND")
    void anUnknownRequestIsNotFound() throws Exception {
        UUID nobody = UUID.randomUUID();

        assertAnswered(approveHttp(headE1, nobody, "{}"), 404, NOT_FOUND);
        assertAnswered(rejectHttp(headE1, nobody, "synthetic: no such request"), 404, NOT_FOUND);
    }

    @Test
    @DisplayName("#1170 完成标准第 4 条（持权但不可决定 → 403 ORGANIZATION_WORKFORCE_COUNTERSIGN_FORBIDDEN，请求仍未决）：另一法人的负责人、持角色但未绑定任何法人的账号、升级等级 0 或 1 的业务管理员，批准与拒签都被拒")
    void aHolderWhoMayNotDecideIsRefused() throws Exception {
        Waiting waiting = personnel();
        UUID id = request(waiting.command(), false, KIND_CHANGE, e1);
        Staff unbound = person(FINANCE_HEAD_ROLE);
        Staff administrator = person(ESCALATED_ROLE);
        assertAnswered(approveHttp(administrator, id, "{}"), 403, FORBIDDEN);
        assertAnswered(rejectHttp(administrator, id, "synthetic: level 0"), 403, FORBIDDEN);
        escalate(id, 1);

        for (Staff who : List.of(headE3, headE2, unbound, administrator)) {
            assertAnswered(approveHttp(who, id, "{}"), 403, FORBIDDEN);
            assertAnswered(rejectHttp(who, id, "synthetic: not allowed"), 403, FORBIDDEN);
        }

        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e1)).isNull();
        assertThat(personnelStatus(waiting.command())).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("#1170 完成标准第 4 条（起草人与请求人）：起草的人即使是该法人的负责人也不能批准或拒签自己起草的命令；撤回的人（WITHDRAWAL 请求的请求人）同样不能决定自己提的撤回请求；均 403 ORGANIZATION_WORKFORCE_COUNTERSIGN_FORBIDDEN")
    void theDrafterAndTheRequesterDoNotDecide() throws Exception {
        Waiting waiting = personnel();
        approveBothHttp(waiting.command(), false);
        Staff withdrawer = person(PERSONNEL_SPECIALIST_ROLE, FINANCE_HEAD_ROLE);
        bind(withdrawer, e2);
        withdrawPersonnelHttp(withdrawer, waiting.command());
        UUID withdrawal = request(waiting.command(), false, KIND_WITHDRAWAL, e2);

        assertAnswered(approveHttp(withdrawer, withdrawal, "{}"), 403, FORBIDDEN);
        assertAnswered(rejectHttp(withdrawer, withdrawal, "synthetic: own request"), 403, FORBIDDEN);
        assertThat(decisionOf(waiting.command(), false, KIND_WITHDRAWAL, e2)).isNull();

        Staff drafter = person(PERSONNEL_SPECIALIST_ROLE, FINANCE_HEAD_ROLE);
        bind(drafter, e1);
        Waiting own = personnelBy(drafter);
        UUID drafted = request(own.command(), false, KIND_CHANGE, e1);

        assertAnswered(approveHttp(drafter, drafted, "{}"), 403, FORBIDDEN);
        assertAnswered(rejectHttp(drafter, drafted, "synthetic: own entry"), 403, FORBIDDEN);
        assertThat(decisionOf(own.command(), false, KIND_CHANGE, e1)).isNull();
    }

    @Test
    @DisplayName("#1170 完成标准第 4 条（不再未决 → 409 ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN）：已批准的请求再批准或再拒签 → 409，决定仍是 APPROVED")
    void aRequestNoLongerOpenIsAConflict() throws Exception {
        Waiting waiting = personnel();
        UUID e1Row = request(waiting.command(), false, KIND_CHANGE, e1);
        assertOk(approveHttp(headE1, e1Row, "{}"));

        assertAnswered(approveHttp(headE1, e1Row, "{}"), 409, NOT_OPEN);
        assertAnswered(rejectHttp(headE1, e1Row, "synthetic: too late"), 409, NOT_OPEN);
        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e1)).isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("#1170 完成标准第 4 条（LAPSED 行）：因另一行被拒而 LAPSED 的请求，其负责人再批准或再拒签 → 409 ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_OPEN，决定仍是 LAPSED")
    void aLapsedRequestIsAConflict() throws Exception {
        Waiting waiting = personnel();
        assertOk(rejectHttp(headE2, request(waiting.command(), false, KIND_CHANGE, e2), "synthetic: not agreed"));
        UUID lapsed = request(waiting.command(), false, KIND_CHANGE, e1);

        assertAnswered(approveHttp(headE1, lapsed, "{}"), 409, NOT_OPEN);
        assertAnswered(rejectHttp(headE1, lapsed, "synthetic: too late"), 409, NOT_OPEN);

        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e1)).isEqualTo(LAPSED);
    }

    // ---- no permission ----

    @ParameterizedTest(name = "role {0}")
    @ValueSource(strings = {"personnel_specialist", "org_data_admin", "super_admin", "security_auditor", "employee"})
    @DisplayName("#1170 完成标准第 5 条：不持有会签权限的账号（含起草的人事专员、其他管理员角色、超级管理员、默认员工）对三个接口都得 403，请求仍未决")
    void everyRoleWithoutThePermissionGetsForbiddenOnAllThree(String role) throws Exception {
        Waiting waiting = personnel();
        UUID id = request(waiting.command(), false, KIND_CHANGE, e1);
        Staff other = role.equals("employee") ? person() : person(role);

        assertThat(approveHttp(other, id, "{}").getStatus()).isEqualTo(403);
        assertThat(rejectHttp(other, id, "synthetic: not allowed").getStatus()).isEqualTo(403);
        assertThat(read(other, PATH).getStatus()).isEqualTo(403);

        assertThat(decisionOf(waiting.command(), false, KIND_CHANGE, e1)).isNull();
        assertThat(personnelStatus(waiting.command())).isEqualTo(AWAITING);
    }
}
