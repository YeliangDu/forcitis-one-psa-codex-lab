package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * F-1 role scope grants, first block (#728): the custom org units of a role (ADR-0026 "1a 交付范围"), versioned with the role (ADR-0034
 * section 5), the scopes a two-person role may have (ADR-0032 section 3, answer 0032-Q1 (a)) and that sensitive permissions reach people
 * one by one (ADR-0032 section 1, user 2026-10-14 "采纳，敏感权限授给人"). Two super_admin holders act in the administrator("admin1") mode.
 * Moved from the F-1 acceptance branch claude/one-1a-par-f1-acceptance (base 771b7b0dc) with the assertions unchanged; what the fixture
 * had to follow on the main line is listed in the commit message. The holder's visible scope after a change belongs to #729.
 */
class RoleScopeGrantIT extends DataScopeSupport {

    static final String SCOPE_NOT_ALLOWED = "PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED";
    static final String GRANT_REFUSED = "platform.identity.grant.refused";

    static final TestDatabase postgres = TestPostgres.database(RoleScopeGrantIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void twoAdministratorsAndAnOrganisation() throws Exception {
        unit("F1-ROOT", null);
        unit("F1-A", "F1-ROOT");
        unit("F1-B", "F1-ROOT");
        position("F1-P1");
        for (String key : List.of("admin1", "admin2", "oda", "holder", "sensitive-holder")) {
            person(key, "F1-ROOT", "F1-P1");
        }
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        grantByOperations("oda", ORG_DATA_ADMIN);
    }

    private String id(String code) throws Exception {
        return orgUnitId(session("oda"), code);
    }

    private static List<String> customOrgUnitIds(MockHttpServletResponse role) throws Exception {
        return JsonPath.read(role.getContentAsString(), "$.customOrgUnits[*].orgUnitId");
    }

    private static List<String> unitsWithDescendants(MockHttpServletResponse role) throws Exception {
        return JsonPath.read(role.getContentAsString(), "$.customOrgUnits[?(@.includeDescendants == true)].orgUnitId");
    }

    private List<String> visibleOrgUnits(String key) throws Exception {
        MockHttpServletResponse listed = mvc.perform(get(ORG_UNITS).with(as(login(key)))).andReturn().getResponse();
        assertThat(listed.getStatus()).as(listed.getContentAsString()).isEqualTo(200);
        return field(items(listed), "orgUnitId");
    }

    @Test
    @DisplayName("F-1 ADR-0026「1a 交付范围」角色范围授予明细 / ADR-0034 第 5 项：自定义部门随角色保存，创建应答与读取都带回同一组部门 ID 及逐部门的「含下级」选择")
    void theCustomOrgUnitsAreKeptWithTheRole() throws Exception {
        MockHttpServletResponse created = createRole(administrator("admin1"), roleBody("f1-detail", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A")), onlyUnit(id("F1-B"))), ORG_UNIT_READ));

        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        assertThat(customOrgUnitIds(created)).containsExactlyInAnyOrder(id("F1-A"), id("F1-B"));
        assertThat(unitsWithDescendants(created)).containsExactly(id("F1-A"));
        String roleId = JsonPath.read(created.getContentAsString(), "$.roleId");
        MockHttpServletResponse read = readRole(administrator("admin1"), roleId);
        assertThat(customOrgUnitIds(read)).containsExactlyInAnyOrder(id("F1-A"), id("F1-B"));
        assertThat(unitsWithDescendants(read)).as("「含下级」是逐部门的选择，读回不变").containsExactly(id("F1-A"));
    }

    @Test
    @DisplayName("F-1 ADR-0034 第 5 项（改明细即新增角色版本）、F-1 事件「范围授予变更」：改自定义部门后读回的是新部门，审计留下改前与改后的部门（#728；持有人的可见范围见下一条，归 #729）")
    void changingTheCustomOrgUnitsLeavesHistory() throws Exception {
        String roleId = ordinaryRole(administrator("admin1"), "f1-moving-detail", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A"))), ORG_UNIT_READ);

        MockHttpServletResponse revised = reviseRole(administrator("admin1"), roleId, roleBody("f1-moving-detail", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-B"))), ORG_UNIT_READ));

        assertThat(revised.getStatus()).as(revised.getContentAsString()).isEqualTo(200);
        assertThat(customOrgUnitIds(readRole(administrator("admin1"), roleId))).containsExactly(id("F1-B"));
        eventually(() -> {
            MockHttpServletResponse found = mvc.perform(get(AUDIT_ENTRIES).param("actionPrefix", "platform.identity").param("limit", "200")
                    .with(AUDIT_READER)).andReturn().getResponse();
            assertThat(found.getStatus()).as(found.getContentAsString()).isEqualTo(200);
            List<Map<String, Object>> aboutTheRole = items(found).stream()
                    .filter(entry -> "ROLE".equals(String.valueOf(entry.get("subjectKind"))) && roleId.equals(String.valueOf(entry.get("subjectId")))).toList();
            assertThat(aboutTheRole).as("an audit entry about role " + roleId + " carries the org units before and after in its detail")
                    .anySatisfy(entry -> {
                        String detail = String.valueOf(entry.get("detail"));
                        Set<String> before = new HashSet<String>(JsonPath.<List<String>>read(detail, "$.before"));
                        Set<String> after = new HashSet<String>(JsonPath.<List<String>>read(detail, "$.after"));
                        assertThat(before).as("before, in detail " + detail).containsExactlyInAnyOrder(id("F1-A"));
                        assertThat(after).as("after, in detail " + detail).containsExactlyInAnyOrder(id("F1-B"));
                    });
        });
    }

    @Test
    @Disabled("归 #729：持有人的可见范围（范围计算、部门浏览）随 F-1 第 2 块实现；本条断言自 changingTheCustomOrgUnitsLeavesHistory 后半原样分出")
    @DisplayName("F-1 ADR-0034 第 5 项（改明细即新增角色版本）：改自定义部门后持有人的可见范围随之改变（归 #729）")
    void changingTheCustomOrgUnitsMovesTheHoldersScope() throws Exception {
        String roleId = ordinaryRole(administrator("admin1"), "f1-moving-scope", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A"))), ORG_UNIT_READ);
        grantOrdinaryRoles(administrator("admin1"), "holder", "f1-moving-scope");
        assertThat(visibleOrgUnits("holder")).contains(id("F1-A")).doesNotContain(id("F1-B"));

        MockHttpServletResponse revised = reviseRole(administrator("admin1"), roleId, roleBody("f1-moving-scope", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-B"))), ORG_UNIT_READ));

        assertThat(revised.getStatus()).as(revised.getContentAsString()).isEqualTo(200);
        assertThat(visibleOrgUnits("holder")).contains(id("F1-B")).doesNotContain(id("F1-A"));
    }

    @Test
    @DisplayName("F-1 ADR-0032 第 3 项 (a)（0032-Q1）：含敏感权限（双人逐人级）的角色只许 ALL / CUSTOM_ORG_UNITS / SELF，相对范围 422 且不开授权申请；SELF 照常进入双人确认")
    void aTwoPersonRoleTakesOnlyAScopeThatDoesNotFollowTheJob() throws Exception {
        for (String relative : List.of("OWN_ORG_UNIT", "OWN_ORG_UNIT_SUBTREE", "SELF_AND_DIRECT_REPORTS")) {
            MockHttpServletResponse refused = createRole(administrator("admin1"), roleBody("f1-sensitive-" + relative.toLowerCase().replace('_', '-'), relative, null, AUDIT_ENTRY_READ));
            assertThat(refused.getStatus()).as(relative + ": " + refused.getContentAsString()).isEqualTo(422);
            assertThat(errorCode(refused)).isEqualTo(SCOPE_NOT_ALLOWED);
        }
        MockHttpServletResponse requests = mvc.perform(get(AUTHORIZATION_REQUESTS).with(as(administrator("admin2")))).andReturn().getResponse();
        assertThat(requests.getContentAsString()).as("no authorization request was opened for a refused scope").doesNotContain("f1-sensitive-own");

        MockHttpServletResponse self = createRole(administrator("admin1"), roleBody("f1-sensitive-self", "SELF", null, AUDIT_ENTRY_READ));
        assertThat(self.getStatus()).as(self.getContentAsString()).isEqualTo(202);
    }

    @Test
    @DisplayName("F-1 F1-01 / ADR-0044 第 1 项：含双人逐人级权限的角色，自定义部门选「含下级」创建即 422 PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED、不开授权申请、审计 platform.identity.grant.refused（原因「双人逐人级角色含下级」）；全部「仅本部门」照常进入双人确认（202）")
    void aTwoPersonRoleNamesItsCustomOrgUnitsWithoutDescendants() throws Exception {
        int refusalsBefore = auditEntries(GRANT_REFUSED).size();
        MockHttpServletResponse refused = createRole(administrator("admin1"),
                roleBody("f1-sensitive-below", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-B")), withDescendants(id("F1-A"))), AUDIT_ENTRY_READ));
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(422);
        assertThat(errorCode(refused)).isEqualTo(SCOPE_NOT_ALLOWED);
        assertThat(mvc.perform(get(AUTHORIZATION_REQUESTS).with(as(administrator("admin2")))).andReturn().getResponse().getContentAsString())
                .doesNotContain("f1-sensitive-below");
        eventually(() -> {
            List<Map<String, Object>> refusals = auditEntries(GRANT_REFUSED);
            assertThat(refusals).as("ADR-0044 影响面：grant.refused 增加原因「双人逐人级角色含下级」").hasSizeGreaterThan(refusalsBefore);
            assertThat(refusals).anySatisfy(entry -> assertThat(auditText(entry)).containsAnyOf("含下级", "descendant", "DESCENDANT"));
        });

        MockHttpServletResponse unitsOnly = createRole(administrator("admin1"),
                roleBody("f1-sensitive-units-only", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-B"))), AUDIT_ENTRY_READ));
        assertThat(unitsOnly.getStatus()).as(unitsOnly.getContentAsString()).isEqualTo(202);
    }

    @Test
    @Disabled("归 #729：范围变化走授权申请（ADR-0032 第 1 项第 3 条）；调度裁定挪出 #728，断言不动")
    @DisplayName("F-1 F1-01：含双人逐人级权限的角色修改时把自定义部门改成「含下级」也 422（不走授权申请），角色不变；要覆盖新部门须逐个列出「仅本部门」并经第二人确认")
    void aTwoPersonRoleIsNotRevisedToIncludeDescendants() throws Exception {
        MockHttpServletResponse asked = createRole(administrator("admin1"),
                roleBody("f1-sensitive-custom", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A"))), AUDIT_ENTRY_READ));
        assertThat(asked.getStatus()).as(asked.getContentAsString()).isEqualTo(202);
        assertThat(confirm(administrator("admin2"), JsonPath.read(asked.getContentAsString(), "$.requestId")).getStatus()).isEqualTo(204);
        eventually(() -> assertThat(roleIdOf(administrator("admin1"), "f1-sensitive-custom")).isPresent());
        String roleId = roleIdOf(administrator("admin1"), "f1-sensitive-custom").orElseThrow();

        MockHttpServletResponse widened = reviseRole(administrator("admin1"), roleId,
                roleBody("f1-sensitive-custom", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A"))), AUDIT_ENTRY_READ));
        MockHttpServletResponse listed = reviseRole(administrator("admin1"), roleId,
                roleBody("f1-sensitive-custom", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-B"))), AUDIT_ENTRY_READ));

        assertThat(widened.getStatus()).as(widened.getContentAsString()).isEqualTo(422);
        assertThat(errorCode(widened)).isEqualTo(SCOPE_NOT_ALLOWED);
        assertThat(listed.getStatus()).as("one more unit listed alone needs the second person: " + listed.getContentAsString()).isEqualTo(202);
        assertThat(unitsWithDescendants(readRole(administrator("admin1"), roleId))).isEmpty();
    }

    @Test
    @DisplayName("F-1 ADR-0044 第 1 项第 3 条（加码升级为双人逐人级）：「含下级」的普通角色加入双人逐人级权限码返回 422 PLATFORM_IDENTITY_ROLE_SCOPE_NOT_ALLOWED，角色不变")
    void anOrdinaryRoleWithDescendantsTakesNoTwoPersonPermission() throws Exception {
        String roleId = ordinaryRole(administrator("admin1"), "f1-ordinary-below", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A"))), ORG_UNIT_READ);

        MockHttpServletResponse raised = reviseRole(administrator("admin1"), roleId,
                roleBody("f1-ordinary-below", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A"))), ORG_UNIT_READ, AUDIT_ENTRY_READ));

        assertThat(raised.getStatus()).as(raised.getContentAsString()).isEqualTo(422);
        assertThat(errorCode(raised)).isEqualTo(SCOPE_NOT_ALLOWED);
        assertThat((List<String>) JsonPath.read(readRole(administrator("admin1"), roleId).getContentAsString(), "$.permissionCodes")).containsExactly(ORG_UNIT_READ);
        assertThat(unitsWithDescendants(readRole(administrator("admin1"), roleId))).containsExactly(id("F1-A"));
    }
}
