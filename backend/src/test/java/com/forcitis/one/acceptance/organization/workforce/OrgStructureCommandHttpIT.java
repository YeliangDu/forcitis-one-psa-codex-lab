package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.ASSIGN_LEGAL_ENTITY;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.AWAITING;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1169 (acceptance part): the HTTP entry of org-structure commands, {@code POST /api/v1/organization/org-structure-commands} and
 * {@code POST .../{orgStructureCommandId}:withdraw}. The use cases are unchanged, so each error case has the status the service already gives
 * (the service ITs of #906 and #910 prove the rules; here only the mapping to HTTP is proved). Read-back: a SET_ATTRIBUTES dated today through
 * {@code GET /org-units/{id}}, every other kind by test SQL on the stored command and version rows, because org_units is not updated by them.
 */
class OrgStructureCommandHttpIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgStructureCommandHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String PATH = "/api/v1/organization/org-structure-commands";
    private static final String NOT_ALLOWED = "ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED";
    private static final String COMMAND_INVALID = "ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_INVALID";
    private static final String LATER = TODAY.plusDays(10).toString();

    // ---- http ----

    private static String json(Object... keyValues) {
        StringBuilder body = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] == null) {
                continue;
            }
            Object value = keyValues[i + 1];
            body.append(body.length() > 1 ? "," : "").append('"').append(keyValues[i]).append("\":")
                    .append(value instanceof Boolean ? value.toString() : "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return body.append('}').toString();
    }

    private MockHttpServletResponse send(Staff who, String path, String body) throws Exception {
        return mvc.perform(post(path).with(csrf()).with(session(who.session())).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private MockHttpServletResponse enterBy(Staff who, String body) throws Exception {
        return send(who, PATH, body);
    }

    private MockHttpServletResponse enterHttp(String body) throws Exception {
        return enterBy(orgAdmin, body);
    }

    private MockHttpServletResponse withdrawHttp(Object command, String body) throws Exception {
        return send(orgAdmin, PATH + "/" + command + ":withdraw", body);
    }

    /** An entry that must be accepted; answers the id the server made. */
    private UUID entered(String body) throws Exception {
        MockHttpServletResponse answer = enterHttp(body);
        assertThat(answer.getStatus()).as("entering " + body + " answered " + answer.getContentAsString()).isEqualTo(200);
        return UUID.fromString(JsonPath.read(answer.getContentAsString(), "$.orgStructureCommandId"));
    }

    private static String errorCode(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.errorCode");
    }

    private void assertAnswered(MockHttpServletResponse answer, int status, String code) throws Exception {
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(status);
        assertThat(errorCode(answer)).isEqualTo(code);
    }

    private static String unitEntry(String kind, Object target, LocalDate day, Object... extra) {
        return entry(ORG_UNIT, kind, TARGET_ORG_UNIT_ID, target, day, extra);
    }

    private static String entry(String targetKind, String kind, String targetName, Object target, LocalDate day, Object... extra) {
        Object[] all = new Object[6 + extra.length];
        System.arraycopy(new Object[] {TARGET_KIND, targetKind, COMMAND_KIND, kind, targetName, target}, 0, all, 0, 6);
        System.arraycopy(extra, 0, all, 6, extra.length);
        Object[] withDay = new Object[all.length + 2];
        System.arraycopy(all, 0, withDay, 0, all.length);
        withDay[all.length] = EFFECTIVE_DATE;
        withDay[all.length + 1] = day;
        return json(withDay);
    }

    private static String positionEntry(String kind, Object target, LocalDate day, Object... extra) {
        return entry(POSITION, kind, TARGET_POSITION_ID, target, day, extra);
    }

    // ---- 200: every kind on an org unit ----

    @Test
    @DisplayName("#1169 完成标准第 1 条（CREATE，组织单元）：200 并返回服务端生成的命令号；当天生效，新单元有首个版本（名称、简称、上级、区域、是否交付单元），命令行记录录入人")
    void createOrgUnit() throws Exception {
        UUID parent = seedUnit(topE1);

        UUID command = entered(entry(ORG_UNIT, CREATE, TARGET_ORG_UNIT_ID, null, TODAY, TARGET_NAME, "Synthetic http unit", TARGET_SHORT_NAME, "HttpU",
                TARGET_PARENT_ORG_UNIT_ID, parent, TARGET_REGION_KIND, REGIONAL, TARGET_IS_DELIVERY_UNIT, true));

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(command).get("command_kind")).isEqualTo(CREATE);
        assertThat(orgCommand(command).get("target_kind")).isEqualTo(ORG_UNIT);
        assertThat(orgCommand(command).get("created_by")).isEqualTo(orgAdmin.userId());
        Map<String, Object> version = unitOn(targetUnitOf(command), TODAY);
        assertThat(version.get("name")).isEqualTo("Synthetic http unit");
        assertThat(version.get("short_name")).isEqualTo("HttpU");
        assertThat(fromStored(version.get("parent_org_unit_id"))).isEqualTo(parent);
        assertThat(version.get("region_kind")).isEqualTo(REGIONAL);
        assertThat(version.get("is_delivery_unit")).isEqualTo(true);
        assertThat(version.get("change_org_structure_command_id")).isEqualTo(command);
    }

    @Test
    @DisplayName("#1169 完成标准第 1 条（SET_ATTRIBUTES，当天）：200；经 GET /org-units/{id} 读回名称、简称、区域分类、是否交付单元四项（单元快照读当天在用的版本行）")
    void setAttributesIsReadBackThroughTheOrgUnitApi() throws Exception {
        UUID target = seedUnit(topE1);

        UUID command = entered(unitEntry(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Renamed over http", TARGET_SHORT_NAME, "RnHttp", TARGET_REGION_KIND, REGIONAL,
                TARGET_IS_DELIVERY_UNIT, true));

        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        String body = mvc.perform(get("/api/v1/organization/org-units/" + target).with(session(orgAdmin.session()))).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.orgUnit.name")).isEqualTo("Renamed over http");
        assertThat((String) JsonPath.read(body, "$.orgUnit.shortName")).isEqualTo("RnHttp");
        assertThat((String) JsonPath.read(body, "$.orgUnit.regionCategory")).isEqualTo(REGIONAL);
        assertThat((Boolean) JsonPath.read(body, "$.orgUnit.deliveryUnit")).isTrue();
    }

    @Test
    @DisplayName("#1169 完成标准第 1 条（DISSOLVE、REPARENT、ASSIGN_HEAD、ASSIGN_RECEIVABLES_MANAGER，当天）：200；命令 EFFECTIVE，当天版本行分别是 REVOKED、新上级、新负责人、新区域应收负责人（测试 SQL 读版本行，GET 不读这些字段）")
    void theOtherKindsOnAUnitTakeEffectToday() throws Exception {
        UUID empty = seedUnit(topE1);
        UUID moved = seedUnit(topE1);
        UUID newParent = seedUnit(topE2);
        UUID headed = seedUnit(topE1);
        UUID managed = seedUnit(topE1);
        UUID person = employed(REGULAR);

        UUID dissolve = entered(unitEntry(DISSOLVE, empty, TODAY));
        UUID reparent = entered(unitEntry(REPARENT, moved, TODAY, TARGET_PARENT_ORG_UNIT_ID, newParent));
        UUID head = entered(unitEntry(ASSIGN_HEAD, headed, TODAY, TARGET_HEAD_EMPLOYEE_ID, person));
        UUID manager = entered(unitEntry(ASSIGN_RECEIVABLES_MANAGER, managed, TODAY, TARGET_RECEIVABLES_MANAGER_EMPLOYEE_ID, person));

        for (UUID command : List.of(dissolve, reparent, head, manager)) {
            assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
            assertThat(orgCommand(command).get("created_by")).isEqualTo(orgAdmin.userId());
        }
        assertThat(unitOn(empty, TODAY).get("status")).isEqualTo(REVOKED);
        assertThat(fromStored(unitOn(moved, TODAY).get("parent_org_unit_id"))).isEqualTo(newParent);
        assertThat(fromStored(unitOn(headed, TODAY).get("head_employee_id"))).isEqualTo(person);
        assertThat(fromStored(unitOn(managed, TODAY).get("regional_receivables_manager_id"))).isEqualTo(person);
    }

    @Test
    @DisplayName("#1169 完成标准第 1 条（REPARENT 日后）：200；命令 PENDING，其版本行已写入（日后版本），当天事实不变")
    void aLaterCommandStaysPendingWithItsVersionsWritten() throws Exception {
        UUID moved = seedUnit(topE1);
        UUID newParent = seedUnit(topE2);

        UUID command = entered(unitEntry(REPARENT, moved, LocalDate.parse(LATER), TARGET_PARENT_ORG_UNIT_ID, newParent));

        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
        assertThat(unitVersionRowsWrittenBy(command)).isPositive();
        assertThat(fromStored(unitOn(moved, TODAY).get("parent_org_unit_id"))).isEqualTo(topE1);
        assertThat(fromStored(unitOn(moved, LocalDate.parse(LATER)).get("parent_org_unit_id"))).isEqualTo(newParent);
    }

    @Test
    @DisplayName("#1169 完成标准第 1 条（MERGE）：200；只写命令行，状态 PENDING，到期前不写版本行（合并由到期作业执行）")
    void aMergeWritesOnlyItsCommandUntilDue() throws Exception {
        UUID merged = seedUnit(topE1);
        UUID receiving = seedUnit(topE1);

        UUID command = entered(unitEntry(MERGE, merged, LocalDate.parse(LATER), MERGE_INTO_ORG_UNIT_ID, receiving));

        assertThat(orgCommand(command).get("status")).isEqualTo(PENDING);
        assertThat(orgCommand(command).get("command_kind")).isEqualTo(MERGE);
        assertThat(fromStored(orgCommand(command).get("merge_into_org_unit_id"))).isEqualTo(receiving);
        assertThat(unitVersionRowsWrittenBy(command)).isZero();
        assertThat(unitOn(merged, TODAY).get("status")).isEqualTo(ACTIVE);
    }

    @Test
    @DisplayName("#1169 完成标准第 1 条（ASSIGN_LEGAL_ENTITY）：没有受影响的人 → 200、EFFECTIVE，写单元法人归属行；有继承该单元法人的在职员工 → 200、待法人归属会签，不写任何版本或归属行")
    void assigningALegalEntityTakesEffectOrAwaitsTheCountersign() throws Exception {
        UUID noOne = seedUnit(topE1);
        UUID withPeople = seedUnit(topE1);
        inheritingIn(withPeople);

        UUID plain = entered(unitEntry(ASSIGN_LEGAL_ENTITY, noOne, TODAY, TARGET_LEGAL_ENTITY_ID, e2));
        UUID awaiting = entered(unitEntry(ASSIGN_LEGAL_ENTITY, withPeople, LocalDate.parse(LATER), TARGET_LEGAL_ENTITY_ID, e2));

        assertThat(orgCommand(plain).get("status")).isEqualTo(EFFECTIVE);
        assertThat(unitAttributions(noOne)).hasSize(1).allSatisfy(row -> assertThat(fromStored(row.get("legal_entity_id"))).isEqualTo(e2));
        assertThat(statusOf(awaiting)).isEqualTo(AWAITING);
        assertThat(unitAttributionRowsWrittenBy(awaiting)).isZero();
        assertThat(unitVersionRowsWrittenBy(awaiting)).isZero();
    }

    @Test
    @DisplayName("#1169 完成标准第 1 条（岗位 CREATE 与 DISABLE）：200；新岗位有首个版本（编码、名称，ACTIVE），无人持有的岗位停用后当天版本为 DISABLED")
    void positionCreateAndDisable() throws Exception {
        String code = "HTTP-" + UUID.randomUUID().toString().substring(0, 8);

        UUID create = entered(entry(POSITION, CREATE, TARGET_POSITION_ID, null, TODAY.minusDays(1), TARGET_CODE, code, TARGET_NAME, "Synthetic http position"));
        UUID position = targetPositionOf(create);
        UUID disable = entered(positionEntry(DISABLE, position, TODAY));

        assertThat(orgCommand(create).get("status")).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(create).get("target_kind")).isEqualTo(POSITION);
        assertThat(positionOn(position, TODAY.minusDays(1)).get("code")).isEqualTo(code);
        assertThat(positionOn(position, TODAY.minusDays(1)).get("status")).isEqualTo(ACTIVE);
        assertThat(orgCommand(disable).get("status")).isEqualTo(EFFECTIVE);
        assertThat(positionOn(position, TODAY).get("status")).isEqualTo(DISABLED);
    }

    // ---- error statuses ----

    @Test
    @DisplayName("#1169 完成标准第 2 条（404）：目标组织单元不存在 → 404 ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND，不留命令行")
    void anUnknownTargetUnitIsNotFound() throws Exception {
        long commands = orgCommandCount();

        assertAnswered(enterHttp(unitEntry(DISSOLVE, UUID.randomUUID(), TODAY)), 404, "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND");
        assertAnswered(enterHttp(unitEntry(SET_ATTRIBUTES, UUID.randomUUID(), TODAY, TARGET_NAME, "Nobody")), 404, "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND");

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#1169 完成标准第 2 条（422 NOT_ALLOWED）：不存在的岗位、上级、并入单元、负责人、法人，以及规则拒绝（有在用下级的单元不能撤销）→ 422 ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED，不留命令行")
    void unknownReferencesAndRuleRefusalsAreUnprocessable() throws Exception {
        UUID target = seedUnit(topE1);
        UUID parent = seedUnit(topE1);
        seedUnit(parent);
        long commands = orgCommandCount();

        assertAnswered(enterHttp(positionEntry(DISABLE, UUID.randomUUID(), TODAY)), 422, NOT_ALLOWED);
        assertAnswered(enterHttp(unitEntry(REPARENT, target, TODAY, TARGET_PARENT_ORG_UNIT_ID, UUID.randomUUID())), 422, NOT_ALLOWED);
        assertAnswered(enterHttp(unitEntry(MERGE, target, LocalDate.parse(LATER), MERGE_INTO_ORG_UNIT_ID, UUID.randomUUID())), 422, NOT_ALLOWED);
        assertAnswered(enterHttp(unitEntry(ASSIGN_HEAD, target, TODAY, TARGET_HEAD_EMPLOYEE_ID, UUID.randomUUID())), 422, NOT_ALLOWED);
        assertAnswered(enterHttp(unitEntry(ASSIGN_LEGAL_ENTITY, target, TODAY, TARGET_LEGAL_ENTITY_ID, UUID.randomUUID())), 422, NOT_ALLOWED);
        assertAnswered(enterHttp(unitEntry(DISSOLVE, parent, TODAY)), 422, NOT_ALLOWED);

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#1169 完成标准第 2 条（422 COMMAND_INVALID）：组件集与命令种类不符 → 422 ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_INVALID：CREATE 指了目标、DISSOLVE 带了名称、SET_ATTRIBUTES 什么都没改、岗位带了 SET_ATTRIBUTES")
    void aWrongComponentSetIsInvalid() throws Exception {
        UUID target = seedUnit(topE1);
        long commands = orgCommandCount();

        assertAnswered(enterHttp(entry(ORG_UNIT, CREATE, TARGET_ORG_UNIT_ID, target, TODAY, TARGET_NAME, "Names a target", TARGET_IS_DELIVERY_UNIT, false)), 422, COMMAND_INVALID);
        assertAnswered(enterHttp(unitEntry(DISSOLVE, target, TODAY, TARGET_NAME, "A dissolve names nothing")), 422, COMMAND_INVALID);
        assertAnswered(enterHttp(unitEntry(SET_ATTRIBUTES, target, TODAY)), 422, COMMAND_INVALID);
        assertAnswered(enterHttp(positionEntry(SET_ATTRIBUTES, UUID.randomUUID(), TODAY, TARGET_NAME, "A position takes no attributes command")), 422, COMMAND_INVALID);

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#1169 完成标准第 2 条（400，网关边缘）：JSON 无法解析、日期无法解析、缺 targetKind、缺 commandKind → 400，不留命令行")
    void malformedInputIsABadRequest() throws Exception {
        UUID target = seedUnit(topE1);
        long commands = orgCommandCount();

        assertThat(enterHttp("{not json").getStatus()).as("unparseable JSON").isEqualTo(400);
        assertThat(enterHttp(unitEntry(DISSOLVE, target, TODAY).replace(TODAY.toString(), "2026-13-45")).getStatus()).as("unparseable date").isEqualTo(400);
        assertThat(enterHttp(json(COMMAND_KIND, DISSOLVE, TARGET_ORG_UNIT_ID, target, EFFECTIVE_DATE, TODAY)).getStatus()).as("no targetKind").isEqualTo(400);
        assertThat(enterHttp(json(TARGET_KIND, ORG_UNIT, TARGET_ORG_UNIT_ID, target, EFFECTIVE_DATE, TODAY)).getStatus()).as("no commandKind").isEqualTo(400);

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    // ---- withdrawing ----

    @Test
    @DisplayName("#1169 完成标准第 3 条（200）：撤回待生效的命令，以及待法人归属会签的命令 → 200、无响应体；命令置 WITHDRAWN，撤回人是调用人")
    void withdrawingAPendingOrAwaitingCommand() throws Exception {
        UUID target = seedUnit(topE1);
        UUID pending = unitCommand(SET_ATTRIBUTES, target, LocalDate.parse(LATER), TARGET_NAME, "Planned over http");
        UUID withPeople = seedUnit(topE1);
        inheritingIn(withPeople);
        UUID awaiting = assignLegalEntity(withPeople, LocalDate.parse(LATER), e2);
        assertThat(statusOf(awaiting)).isEqualTo(AWAITING);

        MockHttpServletResponse first = withdrawHttp(pending, json(REASON, "synthetic: not needed"));
        MockHttpServletResponse second = withdrawHttp(awaiting, json(REASON, "synthetic: not needed either"));

        for (MockHttpServletResponse answer : List.of(first, second)) {
            assertThat(answer.getStatus()).isEqualTo(200);
            assertThat(answer.getContentAsString()).isEmpty();
        }
        assertThat(orgCommand(pending).get("status")).isEqualTo(WITHDRAWN);
        assertThat(orgCommand(pending).get("withdrawn_by")).isEqualTo(orgAdmin.userId());
        assertThat(statusOf(awaiting)).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#1169 完成标准第 3 条（409、404、422）：已生效且无会签的命令撤回 → 409 NOT_ALLOWED，命令仍 EFFECTIVE；不存在的命令号 → 404 ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_FOUND；理由为空白 → 422 ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_REASON_INVALID，命令仍 PENDING")
    void withdrawalRefusals() throws Exception {
        UUID target = seedUnit(topE1);
        UUID effective = unitCommand(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Already in force");
        UUID pending = unitCommand(SET_ATTRIBUTES, seedUnit(topE1), LocalDate.parse(LATER), TARGET_NAME, "Planned over http");

        assertAnswered(withdrawHttp(effective, json(REASON, "synthetic: too late")), 409, NOT_ALLOWED);
        assertAnswered(withdrawHttp(UUID.randomUUID(), json(REASON, "synthetic: nothing there")), 404, "ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_FOUND");
        assertAnswered(withdrawHttp(pending, json(REASON, "   ")), 422, "ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_REASON_INVALID");

        assertThat(orgCommand(effective).get("status")).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(pending).get("status")).isEqualTo(PENDING);
    }

    // ---- who may ----

    /** An ordinary role (no sensitive permission) made through the role api by a super administrator; answers its code. */
    private String role(String accessScope, UUID customUnit, String... permissions) throws Exception {
        UUID superAdministrator = newEmployeeId();
        employee(superAdministrator, unit, null);
        grantByOperations(superAdministrator, "super_admin");
        String code = "http-" + Uuids.timeOrdered(NOW).toString().substring(24);
        String units = customUnit == null ? "" : ",\"customOrgUnits\":[{\"orgUnitId\":\"" + customUnit + "\",\"includeDescendants\":false}]";
        String body = "{\"code\":\"" + code + "\",\"name\":\"Synthetic role " + code + "\",\"enabled\":true,\"accessScope\":\"" + accessScope + "\"" + units
                + ",\"permissionCodes\":[\"" + String.join("\",\"", permissions) + "\"]}";
        MockHttpServletResponse created = send(new Staff(superAdministrator, administratorMode(signIn(superAdministrator))), "/api/v1/platform/roles", body);
        assertThat(created.getStatus()).as("fixture: creating role " + code + ": " + created.getContentAsString()).isEqualTo(201);
        return code;
    }

    @Test
    @DisplayName("#1169 完成标准第 4 条：只持有 org-unit:read 的调用人，录入与撤回两个端点都是 403；录入体缺 commandKind（处理器在权限检查之后才判为无效）也是 403 而不是 400；不留命令行，待生效的命令仍 PENDING")
    void aCallerWithoutThePermissionIsForbidden() throws Exception {
        Staff reader = staff(role("SELF", null, "organization.workforce:org-unit:read"));
        UUID target = seedUnit(topE1);
        UUID pending = unitCommand(SET_ATTRIBUTES, seedUnit(topE1), LocalDate.parse(LATER), TARGET_NAME, "Planned over http");
        long commands = orgCommandCount();

        assertThat(enterBy(reader, unitEntry(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Not allowed")).getStatus()).isEqualTo(403);
        assertThat(enterBy(reader, json(TARGET_KIND, ORG_UNIT, TARGET_ORG_UNIT_ID, target, EFFECTIVE_DATE, TODAY)).getStatus())
                .as("a body the handler judges invalid is still the permission's answer first").isEqualTo(403);
        assertThat(send(reader, PATH + "/" + pending + ":withdraw", json(REASON, "synthetic: not allowed")).getStatus()).isEqualTo(403);

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(orgCommand(pending).get("status")).isEqualTo(PENDING);
        assertThat(unitOn(target, TODAY).get("name")).isEqualTo(seededName(target));
    }

    @Test
    @DisplayName("#1169 完成标准第 5 条（用户决定：录入不查数据范围）：有录入权限、数据范围是自定义单元且不含目标单元的调用人，录入照样成功：200，命令 EFFECTIVE")
    void entryChecksNoDataScope() throws Exception {
        UUID elsewhere = seedUnit(topE3);
        UUID target = seedUnit(topE1);
        Staff scoped = staff(role("CUSTOM_ORG_UNITS", elsewhere, PERMISSION, "organization.workforce:org-unit:read"));

        MockHttpServletResponse answer = enterBy(scoped, unitEntry(SET_ATTRIBUTES, target, TODAY, TARGET_NAME, "Entered from outside the scope"));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        UUID command = UUID.fromString(JsonPath.read(answer.getContentAsString(), "$.orgStructureCommandId"));
        assertThat(orgCommand(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(orgCommand(command).get("created_by")).isEqualTo(scoped.userId());
        assertThat(unitOn(target, TODAY).get("name")).isEqualTo("Entered from outside the scope");
    }
}
