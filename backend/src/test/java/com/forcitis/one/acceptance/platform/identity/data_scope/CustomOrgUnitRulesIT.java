package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * F-1 card (#728), constraint table rows 4 and 7 and section "引用" (the application half of the first block): what saving a role with
 * custom org units writes, and what it refuses. Rulings of one-coordinator for this block, in the words of the task card: a role whose
 * scope is CUSTOM_ORG_UNITS lists at least one unit and otherwise gets 400 from the request validation (not 422); on update customOrgUnits
 * null means "keep the stored value" and an empty array under CUSTOM_ORG_UNITS is the same 400; saving does not check that an org unit
 * exists (a dangling one is caught by the scope review). Rows are read from identity's own schema; the assumed contract is in
 * F1_728_ACCEPTANCE.md.
 */
class CustomOrgUnitRulesIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(CustomOrgUnitRulesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @BeforeAll
    void twoAdministratorsAndAnOrganisation() throws Exception {
        unit("F1-ROOT", null);
        unit("F1-A", "F1-ROOT");
        unit("F1-B", "F1-ROOT");
        unit("F1-C", "F1-ROOT");
        position("F1-P1");
        for (String key : List.of("admin1", "admin2", "oda")) {
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

    private String admin() throws Exception {
        return administrator("admin1");
    }

    private static List<String> customOrgUnitIds(MockHttpServletResponse role) throws Exception {
        return JsonPath.read(role.getContentAsString(), "$.customOrgUnits[*].orgUnitId");
    }

    private static List<String> unitsWithDescendants(MockHttpServletResponse role) throws Exception {
        return JsonPath.read(role.getContentAsString(), "$.customOrgUnits[?(@.includeDescendants == true)].orgUnitId");
    }

    /** A PUT with a body written out by the test, for the shapes roleBody cannot say: customOrgUnits null, accessScope left out. */
    private MockHttpServletResponse reviseWithBody(String roleId, String body) throws Exception {
        String etag = readRole(admin(), roleId).getHeader("ETag");
        return mvc.perform(put(ROLES + "/" + roleId).header("If-Match", etag).with(csrf()).with(as(admin())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private static String createBody(String code, String scopeMember, String unitsMember, String permissionCode) {
        return jsonBody(code, "Synthetic role " + code, scopeMember, unitsMember, permissionCode);
    }

    private static String jsonBody(String name, String scopeMember, String unitsMember, String permissionCode) {
        return jsonBody(null, name, scopeMember, unitsMember, permissionCode);
    }

    private static String jsonBody(String code, String name, String scopeMember, String unitsMember, String permissionCode) {
        List<String> members = new ArrayList<>();
        if (code != null) {
            members.add("\"code\":\"" + code + "\"");
        }
        members.add("\"name\":\"" + name + "\"");
        members.add("\"enabled\":true");
        if (scopeMember != null) {
            members.add(scopeMember);
        }
        if (unitsMember != null) {
            members.add(unitsMember);
        }
        members.add("\"permissionCodes\":[\"" + permissionCode + "\"]");
        return "{" + String.join(",", members) + "}";
    }

    // ---- what identity's tables hold ------------------------------------------------------------------------------------------

    private record Version(UUID roleVersionId, int number, boolean open, String scopeKind) {
    }

    private List<Version> versionsOf(String roleId) throws SQLException {
        List<Version> versions = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select role_version_id, version_number, effective_to_at is null, access_scope_kind from role_versions where role_id = ? order by version_number""")) {
            select.setObject(1, UUID.fromString(roleId));
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    versions.add(new Version(rs.getObject(1, UUID.class), rs.getInt(2), rs.getBoolean(3), rs.getString(4)));
                }
            }
        }
        return versions;
    }

    private record Unit(UUID rowId, String orgUnitId, boolean descendants) {
    }

    private List<Unit> unitsOf(UUID roleVersionId) throws SQLException {
        List<Unit> units = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select role_version_custom_org_unit_id, org_unit_id, is_descendants_included from role_version_custom_org_units
                 where role_version_id = ? order by org_unit_id""")) {
            select.setObject(1, roleVersionId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    units.add(new Unit(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3)));
                }
            }
        }
        return units;
    }

    private static List<String> keys(List<Unit> units) {
        return units.stream().map(unit -> unit.orgUnitId() + (unit.descendants() ? "+" : "")).sorted().toList();
    }

    private long detailRowsOfRole(String roleId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select count(*) from role_version_custom_org_units d join role_versions v on v.role_version_id = d.role_version_id where v.role_id = ?""")) {
            select.setObject(1, UUID.fromString(roleId));
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long requestRows(String requestId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement(
                "select count(*) from authorization_request_custom_org_units where request_id = ?")) {
            select.setObject(1, UUID.fromString(requestId));
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---- constraint 4: no new version without a changed definition, details only with the version that owns them ----------------

    @Test
    @DisplayName("F-1 不变量 4（ADR-0034 第 1 项第 4 条）：定义不变就不增加版本——同一定义原样再保存（含部门顺序不同）返回 200，版本数仍为 1，明细行仍是原来那几行")
    void anUnchangedDefinitionAddsNoVersion() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-same-definition", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A")), onlyUnit(id("F1-B"))), ORG_UNIT_READ);
        List<Version> before = versionsOf(roleId);
        List<Unit> rowsBefore = unitsOf(before.getFirst().roleVersionId());
        assertThat(before).as("a new role has one version").hasSize(1);
        assertThat(keys(rowsBefore)).containsExactlyInAnyOrder(id("F1-A") + "+", id("F1-B"));

        MockHttpServletResponse same = reviseRole(admin(), roleId, roleBody("f1-same-definition", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-B")), withDescendants(id("F1-A"))), ORG_UNIT_READ));

        assertThat(same.getStatus()).as(same.getContentAsString()).isEqualTo(200);
        assertThat(versionsOf(roleId)).as("the same definition adds no version").isEqualTo(before);
        assertThat(unitsOf(before.getFirst().roleVersionId())).as("the detail rows are the very rows written before").isEqualTo(rowsBefore);
        assertThat(detailRowsOfRole(roleId)).isEqualTo(2);
    }

    @Test
    @DisplayName("F-1 不变量 4 / 3：改某个部门的「含下级」即新增版本 2、版本 1 关闭；版本 1 的明细行一行不动，版本 2 带完整的新明细（不向已有版本追加）")
    void aChangedUnitChoiceWritesAFullDetailOnlyOnTheNewVersion() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-new-version", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-B"))), ORG_UNIT_READ);
        Version first = versionsOf(roleId).getFirst();
        List<Unit> firstRows = unitsOf(first.roleVersionId());

        MockHttpServletResponse revised = reviseRole(admin(), roleId, roleBody("f1-new-version", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A")), onlyUnit(id("F1-B"))), ORG_UNIT_READ));

        assertThat(revised.getStatus()).as(revised.getContentAsString()).isEqualTo(200);
        List<Version> versions = versionsOf(roleId);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).open()).as("version 1 is closed by the new one").isFalse();
        assertThat(versions.get(1).open()).isTrue();
        assertThat(unitsOf(first.roleVersionId())).as("the rows of version 1 are untouched").isEqualTo(firstRows);
        assertThat(keys(unitsOf(versions.get(1).roleVersionId()))).as("version 2 holds the whole new detail").containsExactlyInAnyOrder(id("F1-A") + "+", id("F1-B"));
        assertThat(detailRowsOfRole(roleId)).as("2 rows of version 1 and 2 of version 2, none added to an old version").isEqualTo(4);
    }

    @Test
    @DisplayName("F-1 不变量 4 / ADR-0034 第 5 项「每个角色版本存完整明细」：只改名称（部门不变）也新增版本，新版本带着与旧版本相同的完整明细")
    void aNewVersionCarriesTheWholeDetailEvenWhenOnlyTheNameChanged() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-rename-before", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A")), onlyUnit(id("F1-C"))), ORG_UNIT_READ);

        MockHttpServletResponse renamed = reviseRole(admin(), roleId, roleBody("f1-rename-before", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A")), onlyUnit(id("F1-C"))), ORG_UNIT_READ)
                .replace("Synthetic role f1-rename-before", "Synthetic role f1-rename-after"));

        assertThat(renamed.getStatus()).as(renamed.getContentAsString()).isEqualTo(200);
        List<Version> versions = versionsOf(roleId);
        assertThat(versions).as("a changed name is a changed definition").hasSize(2);
        assertThat(keys(unitsOf(versions.get(1).roleVersionId()))).isEqualTo(keys(unitsOf(versions.get(0).roleVersionId())));
        assertThat(detailRowsOfRole(roleId)).isEqualTo(4);
    }

    @Test
    @DisplayName("F-1 不变量 4：范围改成非 CUSTOM_ORG_UNITS 的新版本不带明细，旧版本的明细保留；再改回 CUSTOM_ORG_UNITS 的新版本写入新明细")
    void detailsFollowTheScopeKindOfEachVersion() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-scope-moves", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A"))), ORG_UNIT_READ);

        MockHttpServletResponse toAll = reviseRole(admin(), roleId, roleBody("f1-scope-moves", "ALL", null, ORG_UNIT_READ));
        assertThat(toAll.getStatus()).as(toAll.getContentAsString()).isEqualTo(200);
        List<Version> afterAll = versionsOf(roleId);
        assertThat(afterAll).hasSize(2);
        assertThat(unitsOf(afterAll.get(1).roleVersionId())).as("an ALL version lists no unit").isEmpty();
        assertThat(unitsOf(afterAll.get(0).roleVersionId())).as("the CUSTOM version keeps its unit").hasSize(1);

        MockHttpServletResponse back = reviseRole(admin(), roleId, roleBody("f1-scope-moves", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-B"))), ORG_UNIT_READ));
        assertThat(back.getStatus()).as(back.getContentAsString()).isEqualTo(200);
        List<Version> afterBack = versionsOf(roleId);
        assertThat(afterBack).hasSize(3);
        assertThat(keys(unitsOf(afterBack.get(2).roleVersionId()))).containsExactly(id("F1-B"));
        assertThat(detailRowsOfRole(roleId)).isEqualTo(2);
    }

    @Test
    @DisplayName("F-1 不变量 4「只在新建版本的同一事务内写入」：明细写不进去（同一部门列两次）时整次保存回滚——新建不留角色，修改不留新版本也不留零散明细")
    void theVersionAndItsDetailAreOneTransaction() throws Exception {
        MockHttpServletResponse created = createRole(admin(), roleBody("f1-twice-created", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-A"))), ORG_UNIT_READ));
        assertThat(created.getStatus()).as("the same unit twice cannot be kept: " + created.getContentAsString()).isGreaterThanOrEqualTo(400);
        assertThat(roleIdOf(admin(), "f1-twice-created")).as("no role is left behind").isEmpty();

        String roleId = ordinaryRole(admin(), "f1-twice-revised", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-B"))), ORG_UNIT_READ);
        List<Version> before = versionsOf(roleId);
        MockHttpServletResponse revised = reviseRole(admin(), roleId, roleBody("f1-twice-revised", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-A"))), ORG_UNIT_READ));

        assertThat(revised.getStatus()).as(revised.getContentAsString()).isGreaterThanOrEqualTo(400);
        assertThat(versionsOf(roleId)).as("no new version, the old one stays open").isEqualTo(before);
        assertThat(detailRowsOfRole(roleId)).as("no stray detail row").isEqualTo(1);
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactly(id("F1-B"));
    }

    @Test
    @DisplayName("F-1 用例表「新建 / 修改角色」与「提交授权申请」：双人逐人级角色申请提交时写申请子行（仅本部门），角色此时还没有；第二人确认后角色版本的明细与申请的部门一致，申请子行仍在")
    void aRequestKeepsItsUnitsAndTheConfirmedRoleHoldsTheSame() throws Exception {
        MockHttpServletResponse asked = createRole(admin(), roleBody("f1-request-units", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-C"))), AUDIT_ENTRY_READ));
        assertThat(asked.getStatus()).as(asked.getContentAsString()).isEqualTo(202);
        String requestId = JsonPath.read(asked.getContentAsString(), "$.requestId");

        assertThat(requestRows(requestId)).as("one row per unit of the request").isEqualTo(2);
        assertThat(roleIdOf(admin(), "f1-request-units")).as("the role does not exist before the second person confirms").isEmpty();

        assertThat(confirm(administrator("admin2"), requestId).getStatus()).isEqualTo(204);
        eventually(() -> assertThat(roleIdOf(admin(), "f1-request-units")).isPresent());
        String roleId = roleIdOf(admin(), "f1-request-units").orElseThrow();
        List<Version> versions = versionsOf(roleId);
        assertThat(versions).hasSize(1);
        assertThat(keys(unitsOf(versions.getFirst().roleVersionId()))).containsExactlyInAnyOrder(id("F1-A"), id("F1-C"));
        assertThat(requestRows(requestId)).as("the request rows stay after the confirmation").isEqualTo(2);
    }

    // ---- constraint 7: CUSTOM_ORG_UNITS lists at least one unit, 400 from the request validation --------------------------------

    @Test
    @DisplayName("F-1 不变量 7（调度裁定：400，走请求校验，不用 422）：范围是 CUSTOM_ORG_UNITS 而不带 customOrgUnits、带 null 或带空数组，创建都返回 400，不建角色")
    void aCustomScopeWithoutAnyUnitIsRefusedAtCreation() throws Exception {
        String[] bodies = {
                roleBody("f1-none-missing", "CUSTOM_ORG_UNITS", null, ORG_UNIT_READ),
                createBody("f1-none-null", "\"accessScope\":\"CUSTOM_ORG_UNITS\"", "\"customOrgUnits\":null", ORG_UNIT_READ),
                roleBody("f1-none-empty", "CUSTOM_ORG_UNITS", List.of(), ORG_UNIT_READ)};
        String[] codes = {"f1-none-missing", "f1-none-null", "f1-none-empty"};
        for (int i = 0; i < bodies.length; i++) {
            MockHttpServletResponse refused = createRole(admin(), bodies[i]);

            assertThat(refused.getStatus()).as(codes[i] + ": " + refused.getContentAsString()).isEqualTo(400);
            assertThat(roleIdOf(admin(), codes[i])).as(codes[i] + " was not created").isEmpty();
        }
    }

    @Test
    @DisplayName("F-1 不变量 7：双人逐人级角色选 CUSTOM_ORG_UNITS 而没有部门同样 400（不是 202，不开授权申请）")
    void aTwoPersonRoleWithoutAnyUnitIsRefusedAtOnce() throws Exception {
        MockHttpServletResponse empty = createRole(admin(), roleBody("f1-sensitive-none", "CUSTOM_ORG_UNITS", List.of(), AUDIT_ENTRY_READ));
        MockHttpServletResponse missing = createRole(admin(), roleBody("f1-sensitive-missing", "CUSTOM_ORG_UNITS", null, AUDIT_ENTRY_READ));

        assertThat(empty.getStatus()).as(empty.getContentAsString()).isEqualTo(400);
        assertThat(missing.getStatus()).as(missing.getContentAsString()).isEqualTo(400);
        String requests = mvc.perform(get(AUTHORIZATION_REQUESTS).with(as(administrator("admin2")))).andReturn().getResponse().getContentAsString();
        assertThat(requests).as("no authorization request was opened").doesNotContain("f1-sensitive-none").doesNotContain("f1-sensitive-missing");
    }

    @Test
    @DisplayName("F-1 不变量 7：把没有自定义部门的角色（ALL）改成 CUSTOM_ORG_UNITS 而不给部门——不带 customOrgUnits 沿用的原值就是空——返回 400，角色与版本不变")
    void aRevisionToACustomScopeWithoutUnitsIsRefused() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-all-to-custom", "ALL", null, ORG_UNIT_READ);
        List<Version> before = versionsOf(roleId);

        MockHttpServletResponse withoutUnits = reviseRole(admin(), roleId, roleBody("f1-all-to-custom", "CUSTOM_ORG_UNITS", null, ORG_UNIT_READ));
        MockHttpServletResponse emptyUnits = reviseRole(admin(), roleId, roleBody("f1-all-to-custom", "CUSTOM_ORG_UNITS", List.of(), ORG_UNIT_READ));

        assertThat(withoutUnits.getStatus()).as(withoutUnits.getContentAsString()).isEqualTo(400);
        assertThat(emptyUnits.getStatus()).as(emptyUnits.getContentAsString()).isEqualTo(400);
        assertThat(versionsOf(roleId)).isEqualTo(before);
        assertThat((String) JsonPath.read(readRole(admin(), roleId).getContentAsString(), "$.accessScope")).isEqualTo("ALL");
    }

    // ---- customOrgUnits on update (one-coordinator ruling) ---------------------------------------------------------------------

    @Test
    @DisplayName("F-1 更新时 customOrgUnits 的语义（调度裁定）：传 null（不带该成员，或显式 null）表示沿用原值——部门与各自的「含下级」读回不变，新版本带着完整的原明细")
    void nullUnitsOnUpdateKeepTheStoredUnits() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-keep-units", "CUSTOM_ORG_UNITS", List.of(withDescendants(id("F1-A")), onlyUnit(id("F1-B"))), ORG_UNIT_READ);

        MockHttpServletResponse omitted = reviseRole(admin(), roleId, roleBody("f1-keep-units", "CUSTOM_ORG_UNITS", null, ORG_UNIT_READ, NUMBERING_RULE_READ));
        assertThat(omitted.getStatus()).as(omitted.getContentAsString()).isEqualTo(200);
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactlyInAnyOrder(id("F1-A"), id("F1-B"));
        assertThat(unitsWithDescendants(readRole(admin(), roleId))).containsExactly(id("F1-A"));

        MockHttpServletResponse explicit = reviseWithBody(roleId, jsonBody("Synthetic role f1-keep-units", "\"accessScope\":\"CUSTOM_ORG_UNITS\"", "\"customOrgUnits\":null", ORG_UNIT_READ));
        assertThat(explicit.getStatus()).as(explicit.getContentAsString()).isEqualTo(200);
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactlyInAnyOrder(id("F1-A"), id("F1-B"));
        assertThat(unitsWithDescendants(readRole(admin(), roleId))).containsExactly(id("F1-A"));

        List<Version> versions = versionsOf(roleId);
        assertThat(versions).as("a changed permission set, then a changed one back: three versions").hasSize(3);
        for (Version version : versions) {
            assertThat(keys(unitsOf(version.roleVersionId()))).as("version " + version.number() + " carries the whole detail").containsExactlyInAnyOrder(id("F1-A") + "+", id("F1-B"));
        }
    }

    @Test
    @DisplayName("F-1 更新时 customOrgUnits 的语义（调度裁定）：不带 accessScope（沿用）也不带 customOrgUnits，两者一并沿用——范围与部门读回不变")
    void leavingOutScopeAndUnitsKeepsBoth() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-keep-both", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-C"))), ORG_UNIT_READ);

        MockHttpServletResponse kept = reviseWithBody(roleId, jsonBody("Synthetic role f1-keep-both renamed", null, null, ORG_UNIT_READ));

        assertThat(kept.getStatus()).as(kept.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse read = readRole(admin(), roleId);
        assertThat((String) JsonPath.read(read.getContentAsString(), "$.accessScope")).isEqualTo("CUSTOM_ORG_UNITS");
        assertThat(customOrgUnitIds(read)).containsExactly(id("F1-C"));
    }

    @Test
    @DisplayName("F-1 更新时 customOrgUnits 的语义（调度裁定）：CUSTOM_ORG_UNITS 范围下传空数组按不变量 7 返回 400，不当作「清空」，角色与版本不变")
    void anEmptyArrayUnderACustomScopeIsRefusedOnUpdate() throws Exception {
        String roleId = ordinaryRole(admin(), "f1-empty-update", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), onlyUnit(id("F1-B"))), ORG_UNIT_READ);
        List<Version> before = versionsOf(roleId);

        MockHttpServletResponse emptied = reviseRole(admin(), roleId, roleBody("f1-empty-update", "CUSTOM_ORG_UNITS", List.of(), ORG_UNIT_READ));
        MockHttpServletResponse emptiedWithoutScope = reviseWithBody(roleId, jsonBody("Synthetic role f1-empty-update", null, "\"customOrgUnits\":[]", ORG_UNIT_READ));

        assertThat(emptied.getStatus()).as(emptied.getContentAsString()).isEqualTo(400);
        assertThat(emptiedWithoutScope.getStatus()).as("the scope kept is CUSTOM_ORG_UNITS: " + emptiedWithoutScope.getContentAsString()).isEqualTo(400);
        assertThat(versionsOf(roleId)).isEqualTo(before);
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactlyInAnyOrder(id("F1-A"), id("F1-B"));
    }

    // ---- references: no existence check at save (one-coordinator ruling) -------------------------------------------------------

    @Test
    @DisplayName("F-1 引用一节（调度裁定：保存时不校验部门是否真实存在，悬空由范围复核兜底）：引用一个不存在的部门创建、修改、读回都成功，明细照存")
    void aUnitThatDoesNotExistIsSaved() throws Exception {
        String nobody = Uuids.timeOrdered(NOW).toString();

        MockHttpServletResponse created = createRole(admin(), roleBody("f1-dangling", "CUSTOM_ORG_UNITS", List.of(onlyUnit(nobody)), ORG_UNIT_READ));

        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String roleId = JsonPath.read(created.getContentAsString(), "$.roleId");
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactly(nobody);
        assertThat(keys(unitsOf(versionsOf(roleId).getFirst().roleVersionId()))).containsExactly(nobody);

        String otherNobody = Uuids.timeOrdered(NOW).toString();
        MockHttpServletResponse revised = reviseRole(admin(), roleId, roleBody("f1-dangling", "CUSTOM_ORG_UNITS", List.of(onlyUnit(id("F1-A")), withDescendants(otherNobody)), ORG_UNIT_READ));
        assertThat(revised.getStatus()).as(revised.getContentAsString()).isEqualTo(200);
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactlyInAnyOrder(id("F1-A"), otherNobody);
    }

    @Test
    @DisplayName("F-1 引用一节：双人逐人级角色的申请里引用不存在的部门也能提交（202）并经第二人确认；申请子行与确认后的角色明细都照存")
    void aTwoPersonRoleMayNameAUnitThatDoesNotExist() throws Exception {
        String nobody = Uuids.timeOrdered(NOW).toString();

        MockHttpServletResponse asked = createRole(admin(), roleBody("f1-sensitive-dangling", "CUSTOM_ORG_UNITS", List.of(onlyUnit(nobody)), AUDIT_ENTRY_READ));

        assertThat(asked.getStatus()).as(asked.getContentAsString()).isEqualTo(202);
        String requestId = JsonPath.read(asked.getContentAsString(), "$.requestId");
        assertThat(requestRows(requestId)).isEqualTo(1);
        assertThat(confirm(administrator("admin2"), requestId).getStatus()).isEqualTo(204);
        eventually(() -> assertThat(roleIdOf(admin(), "f1-sensitive-dangling")).isPresent());
        String roleId = roleIdOf(admin(), "f1-sensitive-dangling").orElseThrow();
        assertThat(customOrgUnitIds(readRole(admin(), roleId))).containsExactly(nobody);
    }
}
