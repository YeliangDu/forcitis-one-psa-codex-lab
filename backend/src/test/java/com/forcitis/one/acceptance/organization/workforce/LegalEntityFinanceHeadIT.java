package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #895 leftover (b), setting and changing the finance head (one-design contract on #895, comment 6603037487; design note comment
 * 6518142765 items 1-2): {@code PUT /api/v1/organization/legal-entities/{id}/finance-head} with {@code {"financeHeadEmployeeId": uuid|null}}, If-Match,
 * effective on today's business date, permission legal-entity:update; the finance head rides every new legal entity version.
 */
class LegalEntityFinanceHeadIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(LegalEntityFinanceHeadIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final String CHANGED = "organization.workforce.legal-entity.finance-head.changed";
    private static final String INVALID = "ORGANIZATION_WORKFORCE_FINANCE_HEAD_EMPLOYEE_INVALID";
    private static final String SELF = "ORGANIZATION_WORKFORCE_FINANCE_HEAD_SELF_APPOINTMENT_FORBIDDEN";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // ---- calls ----

    private String path(UUID entity) {
        return LEGAL_ENTITIES + "/" + entity;
    }

    private MockHttpServletResponse read(UUID entity) throws Exception {
        return mvc.perform(get(path(entity)).with(session(orgAdmin.session()))).andReturn().getResponse();
    }

    private MockHttpServletResponse setHead(Staff caller, UUID entity, String ifMatch, String body) throws Exception {
        var request = put(path(entity) + "/finance-head").with(csrf()).with(session(caller.session())).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(ifMatch == null ? request : request.header("If-Match", ifMatch)).andReturn().getResponse();
    }

    private MockHttpServletResponse setHead(UUID entity, UUID head) throws Exception {
        return setHead(orgAdmin, entity, read(entity).getHeader("ETag"), "{\"financeHeadEmployeeId\":" + (head == null ? "null" : "\"" + head + "\"") + "}");
    }

    private MockHttpServletResponse rename(UUID entity, String name) throws Exception {
        String code = JsonPath.read(read(entity).getContentAsString(), "$.code");
        return mvc.perform(put(path(entity)).header("If-Match", read(entity).getHeader("ETag")).with(csrf()).with(session(orgAdmin.session()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"status\":\"ACTIVE\"}")).andReturn().getResponse();
    }

    private UUID newEntity() throws Exception {
        return createLegalEntity(orgAdmin.session(), newLegalEntityCode("FH"), "Synthetic finance head entity");
    }

    private List<Map<String, Object>> versions(UUID entity) throws SQLException {
        return rows("select * from " + LEGAL_ENTITY_VERSIONS + " where legal_entity_id = ? order by revision_number", entity);
    }

    private Map<String, Object> open(UUID entity) throws SQLException {
        List<Map<String, Object>> open = versions(entity).stream().filter(version -> version.get("effective_to_date") == null).toList();
        assertThat(open).as("exactly one open version").hasSize(1);
        return open.getFirst();
    }

    private Object headOf(Map<String, Object> version) {
        return version.get(FINANCE_HEAD_COLUMN);
    }

    private int changes() throws Exception {
        return auditQuery(null, null, CHANGED).size();
    }

    private static String head(MockHttpServletResponse answer) throws Exception {
        Object value = JsonPath.read(answer.getContentAsString(), "$.financeHeadEmployeeId");
        return value == null ? null : value.toString();
    }

    private static String errorCode(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.errorCode");
    }

    private UUID employedPerson() throws Exception {
        return employed(REGULAR);
    }

    // ---- cases ----

    @Test
    @DisplayName("#895 财务负责人 (1)：给没有财务负责人的法人设置 → 200、响应带 financeHeadEmployeeId、ETag 变化，写恰好一条审计 organization.workforce.legal-entity.finance-head.changed，detail 恰为 \"legal entity <id> finance head <id> effective <今日>\"")
    void settingTheFirstHeadWritesTheVersionAndOneAuditEntry() throws Exception {
        UUID entity = newEntity();
        UUID person = employedPerson();
        String before = read(entity).getHeader("ETag");
        int audited = changes();

        MockHttpServletResponse set = setHead(entity, person);

        assertThat(set.getStatus()).as(set.getContentAsString()).isEqualTo(200);
        assertThat(head(set)).isEqualTo(person.toString());
        assertThat(set.getHeader("ETag")).isNotEqualTo(before);
        assertThat(String.valueOf(headOf(open(entity)))).isEqualTo(person.toString());
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditQuery(null, null, CHANGED)).hasSize(audited + 1));
        Map<String, Object> entry = auditQuery(null, null, CHANGED).stream().filter(candidate -> String.valueOf(candidate.get("detail")).contains(entity.toString())).findFirst().orElseThrow();
        assertThat(entry.get("detail")).isEqualTo("legal entity " + entity + " finance head " + person + " effective " + TODAY);
    }

    @Test
    @DisplayName("#895 财务负责人 (2)：更换负责人 → 追加新版本，旧负责人的版本在今日关闭、历史两版都在；审计 detail 带新负责人")
    void changingTheHeadClosesTheOldVersionAndKeepsTheHistory() throws Exception {
        UUID entity = newEntity();
        UUID first = employedPerson();
        UUID second = employedPerson();
        setHead(entity, first);
        int audited = changes();

        MockHttpServletResponse changed = setHead(entity, second);

        assertThat(changed.getStatus()).as(changed.getContentAsString()).isEqualTo(200);
        assertThat(String.valueOf(headOf(open(entity)))).isEqualTo(second.toString());
        List<Map<String, Object>> history = versions(entity);
        assertThat(history.stream().map(version -> String.valueOf(headOf(version))).toList()).as("the history keeps the old head").contains(first.toString(), second.toString());
        Map<String, Object> closed = history.stream().filter(version -> first.toString().equals(String.valueOf(headOf(version)))).findFirst().orElseThrow();
        assertThat(closed.get("effective_to_date")).isNotNull();
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditQuery(null, null, CHANGED)).hasSize(audited + 1));
    }

    @Test
    @DisplayName("#895 财务负责人 (3)：同一天更换负责人又改名 → 只有一个当前版本，名称和负责人都是新的")
    void aHeadChangeAndARenameOnTheSameDayShareOneCurrentVersion() throws Exception {
        UUID entity = newEntity();
        UUID person = employedPerson();

        assertThat(setHead(entity, person).getStatus()).isEqualTo(200);
        MockHttpServletResponse renamed = rename(entity, "Synthetic renamed same day");

        assertThat(renamed.getStatus()).as(renamed.getContentAsString()).isEqualTo(200);
        Map<String, Object> current = open(entity);
        assertThat(current.get("name")).isEqualTo("Synthetic renamed same day");
        assertThat(String.valueOf(headOf(current))).isEqualTo(person.toString());
    }

    @Test
    @DisplayName("#895 财务负责人 (4)：只改名不改负责人，负责人保持（改名不能把负责人丢掉）")
    void aRenameKeepsTheHead() throws Exception {
        UUID entity = newEntity();
        UUID person = employedPerson();
        setHead(entity, person);

        MockHttpServletResponse renamed = rename(entity, "Synthetic renamed only");

        assertThat(renamed.getStatus()).as(renamed.getContentAsString()).isEqualTo(200);
        assertThat(String.valueOf(headOf(open(entity)))).isEqualTo(person.toString());
        assertThat(head(read(entity))).isEqualTo(person.toString());
    }

    @Test
    @DisplayName("#895 财务负责人 (5)：再次设置同一个负责人 → 200、ETag 与版本数不变、不写审计")
    void settingTheSameHeadAgainChangesNothing() throws Exception {
        UUID entity = newEntity();
        UUID person = employedPerson();
        setHead(entity, person);
        String etag = read(entity).getHeader("ETag");
        int versionCount = versions(entity).size();
        int audited = changes();

        MockHttpServletResponse again = setHead(entity, person);

        assertThat(again.getStatus()).as(again.getContentAsString()).isEqualTo(200);
        assertThat(again.getHeader("ETag")).isEqualTo(etag);
        assertThat(versions(entity)).hasSize(versionCount);
        assertThat(changes()).isEqualTo(audited);
    }

    @Test
    @DisplayName("#895 财务负责人 (6)：null 清除负责人（版本里为空、审计 detail 为 finance head none）；缺少 financeHeadEmployeeId 键 → 400，不写任何东西")
    void nullClearsTheHeadAndAnAbsentKeyIsRefused() throws Exception {
        UUID entity = newEntity();
        UUID person = employedPerson();
        setHead(entity, person);
        int audited = changes();

        MockHttpServletResponse absent = setHead(orgAdmin, entity, read(entity).getHeader("ETag"), "{}");
        assertThat(absent.getStatus()).isEqualTo(400);
        assertThat(String.valueOf(headOf(open(entity)))).isEqualTo(person.toString());
        assertThat(changes()).isEqualTo(audited);

        MockHttpServletResponse cleared = setHead(entity, null);
        assertThat(cleared.getStatus()).as(cleared.getContentAsString()).isEqualTo(200);
        assertThat(head(cleared)).isNull();
        assertThat(headOf(open(entity))).isNull();
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditQuery(null, null, CHANGED)).hasSize(audited + 1));
        assertThat(auditQuery(null, null, CHANGED).stream().map(entry -> String.valueOf(entry.get("detail"))).toList())
                .contains("legal entity " + entity + " finance head none effective " + TODAY);
    }

    @Test
    @DisplayName("#895 财务负责人 (7)：不存在的员工、今天不在职的员工 → 400 ORGANIZATION_WORKFORCE_FINANCE_HEAD_EMPLOYEE_INVALID，负责人与审计都不变")
    void anUnknownOrNotEmployedPersonIsRefused() throws Exception {
        UUID entity = newEntity();
        UUID notEmployed = newEmployeeId();
        employee(notEmployed, unitE1, null);
        int audited = changes();

        for (UUID invalid : List.of(UUID.randomUUID(), notEmployed)) {
            MockHttpServletResponse refused = setHead(entity, invalid);
            assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(400);
            assertThat(errorCode(refused)).isEqualTo(INVALID);
        }
        assertThat(headOf(open(entity))).isNull();
        assertThat(changes()).isEqualTo(audited);
    }

    @Test
    @DisplayName("#895 财务负责人 (8)：调用者不能任命自己 → 403 ORGANIZATION_WORKFORCE_FINANCE_HEAD_SELF_APPOINTMENT_FORBIDDEN，什么都不写；任命别人可以")
    void nobodyAppointsThemselves() throws Exception {
        UUID entity = newEntity();
        int audited = changes();

        MockHttpServletResponse self = setHead(orgAdmin, entity, read(entity).getHeader("ETag"), "{\"financeHeadEmployeeId\":\"" + orgAdmin.employeeId() + "\"}");

        assertThat(self.getStatus()).as(self.getContentAsString()).isEqualTo(403);
        assertThat(errorCode(self)).isEqualTo(SELF);
        assertThat(headOf(open(entity))).isNull();
        assertThat(changes()).isEqualTo(audited);
        assertThat(setHead(entity, employedPerson()).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("#895 财务负责人 (9)：缺 If-Match → 428，过期的 If-Match → 412，负责人不变")
    void theVersionIsRequiredAndChecked() throws Exception {
        UUID entity = newEntity();
        UUID person = employedPerson();
        String stale = read(entity).getHeader("ETag");
        setHead(entity, employedPerson());
        Object kept = headOf(open(entity));

        MockHttpServletResponse missing = setHead(orgAdmin, entity, null, "{\"financeHeadEmployeeId\":\"" + person + "\"}");
        MockHttpServletResponse old = setHead(orgAdmin, entity, stale, "{\"financeHeadEmployeeId\":\"" + person + "\"}");

        assertThat(missing.getStatus()).isEqualTo(428);
        assertThat(old.getStatus()).isEqualTo(412);
        assertThat(headOf(open(entity))).isEqualTo(kept);
    }

    @Test
    @DisplayName("#895 财务负责人 (10)：没有 legal-entity:update 权限的人（默认员工、人事专员、财务负责人自己）→ 403，什么都不写")
    void callersWithoutTheUpdatePermissionAreRefused() throws Exception {
        UUID entity = newEntity();
        int audited = changes();
        UUID person = employedPerson();

        for (Staff caller : List.of(account(), account("personnel_specialist"), account(FINANCE_HEAD_ROLE))) {
            MockHttpServletResponse refused = setHead(caller, entity, read(entity).getHeader("ETag"), "{\"financeHeadEmployeeId\":\"" + person + "\"}");
            assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(403);
        }
        assertThat(headOf(open(entity))).isNull();
        assertThat(changes()).isEqualTo(audited);
    }

    @Test
    @DisplayName("#895 财务负责人 (11)：GET 法人显示当前财务负责人（设置前为空，设置后为所设的人；用 fixture 绑定的 E1 也显示）")
    void theLegalEntityShowsItsHead() throws Exception {
        UUID entity = newEntity();
        assertThat(head(read(entity))).isNull();
        UUID person = employedPerson();
        setHead(entity, person);

        assertThat(head(read(entity))).isEqualTo(person.toString());
        assertThat(head(read(e1))).as("the head bound by the fixture shows too").isEqualTo(headE1.employeeId().toString());
    }
}
