package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * #910 acceptance part, DD amendment B on #910 (decider read path; lead ruling 2026-11-02 that it is #910's behaviour, review rv1041a M3):
 * a countersign is not a platform.approval document, so this is the only route by which a decider sees employees outside their data scope.
 * Only the finance head of the row's entity (business_admin from escalation level 2) reads the row and its parent command; anything else is
 * 404 (ADR-0026 1.7, naming-standard 2.6 A-10). Responses carry only the listed columns (no L3, no contact data, no other records of the
 * employee), and errors do not echo reason text (ADR-0080 4.2). The org data admin sees the merge preview and the basis whatever its own
 * data scope; finance heads of a requested entity see the basis. The paths are those of OrgCountersignNames, accepted by one-design on #910
 * (comment 6588705997): a caller without the function permission gets 403, a holder who is not the decider or addressee gets 404.
 */
class OrgCountersignReadPathIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgCountersignReadPathIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(10);
    private static final Pattern JSON_KEY = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:");

    private record Waiting(UUID unit, UUID first, UUID second, UUID command) {
    }

    private Waiting waiting() throws Exception {
        UUID unitX = seedUnit(topE1);
        UUID first = inheritingIn(unitX);
        UUID second = inheritingIn(unitX);
        UUID command = assignLegalEntity(unitX, DAY, e2);
        assertThat(statusOf(command)).isEqualTo(AWAITING);
        return new Waiting(unitX, first, second, command);
    }

    private MockHttpServletResponse read(Staff who, MockHttpServletRequestBuilder request) throws Exception {
        MockHttpServletResponse response = mvc.perform(request.with(session(who.session()))).andReturn().getResponse();
        actIn(orgAdmin);
        return response;
    }

    private MockHttpServletResponse row(Staff who, UUID countersignId) throws Exception {
        return read(who, get(READ_COUNTERSIGN_PATH, countersignId));
    }

    private MockHttpServletResponse bases(Staff who, UUID orgCommand) throws Exception {
        return read(who, get(READ_BASES_PATH, orgCommand));
    }

    /** Every JSON property name in the body (objects at any depth). */
    private static Set<String> keysOf(String json) {
        Set<String> keys = new TreeSet<>();
        Matcher found = JSON_KEY.matcher(json);
        while (found.find()) {
            keys.add(found.group(1));
        }
        return keys;
    }

    private static void assertOnlyListedColumns(MockHttpServletResponse response) throws Exception {
        assertThat(keysOf(response.getContentAsString())).as("amendment B: columns shown, and only these (no L3, no contact data)").isSubsetOf(READ_PATH_FIELDS);
    }

    @Test
    @DisplayName("#910 修正案 B（读路径）：E1 负责人读 E1 的请求行：200，带父命令（ID、类型、生效日、状态）与同一命令的各请求行（E1、E2）；响应只含所列字段")
    void theHeadOfTheRowsEntityReadsItWithItsCommand() throws Exception {
        Waiting waiting = waiting();

        MockHttpServletResponse response = row(headE1, requestId(waiting.command(), KIND_CHANGE, e1));

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).contains(waiting.command().toString(), ASSIGN_LEGAL_ENTITY, AWAITING, DAY.toString(), e1.toString(), e2.toString());
        assertThat(JsonPath.<List<Object>>read(body, "$..requestKind")).hasSize(2);
        assertOnlyListedColumns(response);
    }

    @Test
    @DisplayName("#910 修正案 B、ADR-0026 第 1.7 条（越权与跨范围）：另一法人的负责人（E2 读 E1 的行、E3 读 E1 的行）、有权限但不是任何法人负责人的人、不存在的行都得 404；没有审批权限的人得 403（one-design 命名裁定 #910 comment 6588705997）；拒绝的响应不带决定理由")
    void everyoneElseGetsNotFound() throws Exception {
        Waiting waiting = waiting();
        reject(headE2, waiting.command(), KIND_CHANGE, e2, "synthetic-reason-marker-7f3a");
        UUID rowE2 = requestId(waiting.command(), KIND_CHANGE, e2);
        UUID rowE1 = requestId(waiting.command(), KIND_CHANGE, e1);
        Staff holderOnly = person(FINANCE_HEAD_ROLE);
        Staff nobody = person();

        for (Staff outsider : List.of(headE3, holderOnly)) {
            MockHttpServletResponse response = row(outsider, rowE2);
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.getContentAsString()).doesNotContain("synthetic-reason-marker-7f3a");
        }
        assertThat(row(headE1, rowE2).getStatus()).as("E2's row is not addressed to E1").isEqualTo(404);
        assertThat(row(headE2, rowE1).getStatus()).as("E1's row is not addressed to E2").isEqualTo(404);
        assertThat(row(headE1, UUID.randomUUID()).getStatus()).isEqualTo(404);
        assertThat(row(nobody, rowE2).getStatus()).isEqualTo(403);
        assertThat(row(headE2, rowE2).getStatus()).as("the twin: its own head reads it").isEqualTo(200);
    }

    @Test
    @DisplayName("#910 修正案 B、用户答复 Q3（边界）：业务管理员在升级级别 0 时读该行得 404，升到 2 后得 200")
    void theEscalatedRoleReadsFromLevelTwo() throws Exception {
        Waiting waiting = waiting();
        Staff escalated = person(ESCALATED_ROLE);
        UUID rowE1 = requestId(waiting.command(), KIND_CHANGE, e1);

        assertThat(row(escalated, rowE1).getStatus()).isEqualTo(404);
        escalateTo(waiting.command(), KIND_CHANGE, e1, 2);
        MockHttpServletResponse response = row(escalated, rowE1);

        assertThat(response.getStatus()).isEqualTo(200);
        assertOnlyListedColumns(response);
    }

    @Test
    @DisplayName("#910 修正案 B（会签依据）：被请求法人的负责人（E2）与组织数据管理员读依据：200，每人一行（员工 ID、姓名、工号、之前与之后的法人），只含所列字段；E3 负责人（有审批权限、不在请求集合内）得 404；既无审批权限也无组织命令权限的人（含数据范围为全部的人事专员）得 403；升级级别 0 的业务管理员得 404；依据外的人与该员工的其他命令不出现")
    void theBasisIsReadByTheRequestedHeadsAndTheOrgAdminOnly() throws Exception {
        Waiting waiting = waiting();
        UUID outsider = inheritingIn(topE1);
        actInSpecialist();
        UUID otherCommand = separate(waiting.first(), DAY.minusDays(2));
        actIn(orgAdmin);

        for (Staff reader : List.of(headE2, orgAdmin)) {
            MockHttpServletResponse response = bases(reader, waiting.command());
            assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
            String body = response.getContentAsString();
            assertThat(JsonPath.<List<String>>read(body, "$.items[*].employeeId")).containsExactlyInAnyOrder(text(waiting.first()), text(waiting.second()));
            assertThat(JsonPath.<List<Object>>read(body, "$.items[*].employeeCode")).hasSize(2).doesNotContainNull();
            assertThat(JsonPath.<List<Object>>read(body, "$.items[*].displayName")).hasSize(2).doesNotContainNull();
            assertThat(body).contains(e1.toString(), e2.toString()).doesNotContain(text(outsider), otherCommand.toString());
            assertOnlyListedColumns(response);
        }
        assertThat(bases(headE3, waiting.command()).getStatus()).isEqualTo(404);
        assertThat(bases(person(), waiting.command()).getStatus()).isEqualTo(403);
        assertThat(bases(hr, waiting.command()).getStatus()).as("a 人事专员 (ALL data scope, no approve or org command permission)").isEqualTo(403);
        assertThat(bases(person(ESCALATED_ROLE), waiting.command()).getStatus()).as("business_admin at escalation level 0").isEqualTo(404);
    }

    @Test
    @DisplayName("#910 修正案 B（读路径不带其他记录）：请求行响应不含该员工的其他人事命令与版本")
    void theRowCarriesNoOtherRecordsOfTheEmployee() throws Exception {
        Waiting waiting = waiting();
        actInSpecialist();
        UUID otherCommand = separate(waiting.second(), DAY.minusDays(2));
        actIn(orgAdmin);

        MockHttpServletResponse response = row(headE1, requestId(waiting.command(), KIND_CHANGE, e1));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).doesNotContain(otherCommand.toString());
        assertOnlyListedColumns(response);
    }

    @Test
    @DisplayName("#910 修正案 B、ADR-0080 第 4.2 条：校验失败的拒签（理由 501 字）被拒，错误信息不回显理由文字；请求仍未决")
    void aRefusedRejectionDoesNotEchoTheReason() throws Exception {
        Waiting waiting = waiting();
        String reason = "synthetic-echo-marker-" + "x".repeat(479);

        Throwable failure = catchThrowable(() -> reject(headE1, waiting.command(), KIND_CHANGE, e1, reason));

        assertThat(failure).isNotNull();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            assertThat(String.valueOf(cause.getMessage())).doesNotContain("synthetic-echo-marker-");
        }
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isNull();
    }

    @Test
    @DisplayName("#910 修正案 B（合并预演）：组织数据管理员能看合并预演：200，被并入单元的每个在职员工一行（员工 ID、姓名、工号、当前单元、之前与之后的法人、是否需要会签），继承法人的人需要会签、有自有归属的人不需要；只含所列字段；预演不写任何东西；没有组织命令权限的人得 403")
    void theOrgAdminSeesTheMergePreviewWhateverItsScope() throws Exception {
        UUID merged = seedUnit(topE1);
        UUID receiving = seedUnit(topE2);
        UUID inheriting = inheritingIn(merged);
        UUID own = employedIn(merged, REGULAR, true);
        long commands = orgCommandCount();

        MockHttpServletResponse response = read(orgAdmin, get(PREVIEW_MERGE_PATH, merged).param("mergeIntoOrgUnitId", text(receiving))
                .param("effectiveDate", DAY.toString()));

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        String body = response.getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        assertThat(items).extracting(item -> item.get("employeeId")).containsExactlyInAnyOrder(text(inheriting), text(own));
        assertThat(items).allSatisfy(item -> assertThat(item).containsKeys("displayName", "employeeCode", "needsAttributionCountersign"));
        for (Map<String, Object> item : items) {
            assertThat(item.get("needsAttributionCountersign")).as("employee " + item.get("employeeId")).isEqualTo(text(inheriting).equals(item.get("employeeId")));
        }
        assertOnlyListedColumns(response);
        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(read(headE1, get(PREVIEW_MERGE_PATH, merged).param("mergeIntoOrgUnitId", text(receiving)).param("effectiveDate", DAY.toString())).getStatus())
                .isEqualTo(403);
    }
}
