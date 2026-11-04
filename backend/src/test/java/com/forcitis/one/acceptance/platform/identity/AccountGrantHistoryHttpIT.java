package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1173 (acceptance part, PR 2): {@code GET /api/v1/platform/user-accounts/{userId}/role-grants?limit=&cursor=}, the full grant history of one
 * account (open and closed rows, newest first). The oracle is the stored account_role_grants rows read by test SQL, after a grant, a
 * replace-roles and a revoke made through the endpoints. The order of rows with an equal start moment is not asserted (the card leaves the
 * tie direction open and the fixed clock makes such ties likely).
 */
class AccountGrantHistoryHttpIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(AccountGrantHistoryHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String ACCOUNTS = "/api/v1/platform/user-accounts";
    private static final String ROLES = "/api/v1/platform/roles";
    private static final Set<String> ROW_KEYS = Set.of("roleCode", "channel", "effectiveFrom", "effectiveTo", "revocationCause", "grantedBy", "revokedBy");
    private static final AtomicInteger NUMBER = new AtomicInteger();

    private UUID unit;

    private UUID unit() throws Exception {
        if (unit == null) {
            unit = newOrgUnitId();
            orgUnit(unit, null, null);
        }
        return unit;
    }

    private SignedIn person() throws Exception {
        UUID employee = newEmployeeId();
        employee(employee, unit(), null);
        return signIn(employee);
    }

    private SignedIn administrator() throws Exception {
        UUID employee = newEmployeeId();
        employee(employee, unit(), null);
        grantByOperations(employee, "super_admin");
        return administratorMode(signIn(employee));
    }

    private String createRole(SignedIn admin) throws Exception {
        String code = "synthetic_hist_" + NUMBER.incrementAndGet() + "_" + Long.toString(System.nanoTime() % 100000, 36);
        MockHttpServletResponse role = mvc.perform(post(ROLES).with(csrf()).with(session(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Synthetic " + code + "\",\"enabled\":true,\"permissionCodes\":[]}")).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(201);
        return code;
    }

    private void replaceRoles(SignedIn admin, UUID account, String... roleCodes) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + "/" + account).with(session(admin))).andReturn().getResponse();
        assertThat(read.getStatus()).as(contentOf(read)).isEqualTo(200);
        String body = "{\"roleCodes\":[" + String.join(",", Arrays.stream(roleCodes).map(code -> "\"" + code + "\"").toList()) + "]}";
        MockHttpServletResponse replaced = mvc.perform(post(ACCOUNTS + "/" + account + ":replace-roles").header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertThat(replaced.getStatus()).as(contentOf(replaced)).isEqualTo(204);
    }

    private static String contentOf(MockHttpServletResponse answer) {
        try {
            return answer.getContentAsString();
        } catch (java.io.UnsupportedEncodingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private MockHttpServletResponse history(SignedIn caller, UUID account, String query) throws Exception {
        return mvc.perform(get(ACCOUNTS + "/" + account + "/role-grants" + query).with(session(caller))).andReturn().getResponse();
    }

    private List<Map<String, Object>> items(MockHttpServletResponse answer) {
        assertThat(answer.getStatus()).as(contentOf(answer)).isEqualTo(200);
        return JsonPath.read(contentOf(answer), "$.items");
    }

    private List<Map<String, Object>> allPages(SignedIn caller, UUID account, int limit) throws Exception {
        List<Map<String, Object>> all = new ArrayList<>();
        String cursor = null;
        for (int pages = 0; pages < 200; pages++) {
            MockHttpServletResponse page = history(caller, account, "?limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor));
            all.addAll(items(page));
            cursor = JsonPath.read(contentOf(page), "$.nextCursor");
            if (cursor == null) {
                return all;
            }
        }
        throw new AssertionError("the cursors did not run out");
    }

    /** The stored rows of the account, one line per grant: role code, channel, start, end, cause, grantor, revoker. */
    private List<List<String>> storedGrants(UUID account) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Connection identity = schemaConnection("platform_identity"); PreparedStatement select = identity.prepareStatement("""
                select r.code, g.grant_channel_kind, g.effective_from_at, g.effective_to_at, g.revocation_cause_kind, g.granted_by, g.revoked_by
                from account_role_grants g join roles r on r.role_id = g.role_id where g.user_id = ?""")) {
            select.setObject(1, account);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    rows.add(List.of(rs.getString(1), rs.getString(2), instant(rs.getObject(3, OffsetDateTime.class)), instant(rs.getObject(4, OffsetDateTime.class)),
                            String.valueOf(rs.getString(5)), String.valueOf(rs.getObject(6)), String.valueOf(rs.getObject(7))));
                }
            }
        }
        return rows;
    }

    private static String instant(OffsetDateTime at) {
        return at == null ? "null" : String.valueOf(at.toInstant().toEpochMilli());
    }

    private static String millis(Object text) {
        return text == null ? "null" : String.valueOf(java.time.OffsetDateTime.parse((String) text).toInstant().toEpochMilli());
    }

    private static List<String> line(Map<String, Object> row) {
        return List.of((String) row.get("roleCode"), (String) row.get("channel"), millis(row.get("effectiveFrom")), millis(row.get("effectiveTo")),
                String.valueOf(row.get("revocationCause")), String.valueOf(row.get("grantedBy")), String.valueOf(row.get("revokedBy")));
    }

    // ---- the rows ----

    @Test
    @DisplayName("#1173 完成标准第 2 条（与存储一致）：授予、整体替换角色、撤销之后，接口的行（开、闭都在）与 account_role_grants 里该账号的行一一相同；每行的键恰好是 roleCode、channel、effectiveFrom、effectiveTo、revocationCause、grantedBy、revokedBy")
    void theRowsEqualTheStoredGrants() throws Exception {
        SignedIn admin = administrator();
        String roleA = createRole(admin);
        String roleB = createRole(admin);
        SignedIn target = person();
        replaceRoles(admin, target.userId(), "employee", roleA);
        replaceRoles(admin, target.userId(), "employee", roleB);
        replaceRoles(admin, target.userId(), "employee");

        List<Map<String, Object>> rows = allPages(admin, target.userId(), 100);

        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(ROW_KEYS));
        assertThat(rows.stream().map(AccountGrantHistoryHttpIT::line).toList()).containsExactlyInAnyOrderElementsOf(storedGrants(target.userId()));
        assertThat(rows.stream().filter(row -> roleA.equals(row.get("roleCode"))).toList()).hasSize(1).allSatisfy(row -> {
            assertThat(row.get("effectiveTo")).as("roleA was taken away").isNotNull();
            assertThat(row.get("revocationCause")).isNotNull();
        });
        assertThat(rows.stream().filter(row -> roleB.equals(row.get("roleCode"))).toList()).hasSize(1).allSatisfy(row -> assertThat(row.get("effectiveTo")).isNotNull());
        assertThat(rows.stream().filter(row -> "employee".equals(row.get("roleCode"))).toList()).isNotEmpty()
                .anySatisfy(row -> assertThat(row.get("effectiveTo")).as("a role held throughout stays open").isNull());
    }

    @Test
    @DisplayName("#1173 完成标准第 2 条（授予人、撤销人）：管理员手工授予与撤销的行，grantedBy 与 revokedBy 是该管理员的用户 id（只有 id，没有姓名）；仍在持有的行 revokedBy 与 revocationCause 为空")
    void theGrantorAndTheRevokerAreUserIds() throws Exception {
        SignedIn admin = administrator();
        String role = createRole(admin);
        SignedIn target = person();
        replaceRoles(admin, target.userId(), "employee", role);
        List<Map<String, Object>> open = allPages(admin, target.userId(), 100);
        assertThat(open).filteredOn(row -> role.equals(row.get("roleCode"))).hasSize(1).allSatisfy(row -> {
            assertThat(row.get("grantedBy")).isEqualTo(admin.userId().toString());
            assertThat(row.get("effectiveTo")).isNull();
            assertThat(row.get("revokedBy")).isNull();
            assertThat(row.get("revocationCause")).isNull();
        });

        replaceRoles(admin, target.userId(), "employee");

        assertThat(allPages(admin, target.userId(), 100)).filteredOn(row -> role.equals(row.get("roleCode"))).hasSize(1).allSatisfy(row -> {
            assertThat(row.get("revokedBy")).isEqualTo(admin.userId().toString());
            assertThat(row.get("effectiveTo")).isNotNull();
        });
    }

    @Test
    @DisplayName("#1173 完成标准第 2 条（排序与分页）：生效时间新的在前；逐页取（limit=2，沿游标）与一次取完的结果相同、没有重复，游标用尽后下一页为空；不断言生效时间相同的行之间的先后")
    void newestFirstAndPaged() throws Exception {
        SignedIn admin = administrator();
        SignedIn target = person();
        List<String> roles = new ArrayList<>();
        for (int day = 1; day <= 4; day++) {
            clock.set(NOW.plusSeconds(600L * day));
            String role = createRole(admin);
            roles.add(role);
            replaceRoles(admin, target.userId(), "employee", role);
        }

        List<Map<String, Object>> byTwos = allPages(admin, target.userId(), 2);
        List<Map<String, Object>> whole = allPages(admin, target.userId(), 100);

        assertThat(byTwos).isEqualTo(whole);
        assertThat(whole.stream().map(AccountGrantHistoryHttpIT::line).toList()).doesNotHaveDuplicates();
        List<Long> starts = whole.stream().map(row -> Long.parseLong(millis(row.get("effectiveFrom")))).toList();
        assertThat(starts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        List<String> order = whole.stream().map(row -> (String) row.get("roleCode")).filter(roles::contains).toList();
        assertThat(order).as("the grants made on later days come first").containsExactlyElementsOf(roles.reversed());
        MockHttpServletResponse firstPage = history(admin, target.userId(), "?limit=2");
        assertThat(items(firstPage)).hasSize(2);
        assertThat((String) JsonPath.read(contentOf(firstPage), "$.nextCursor")).isNotBlank();
    }

    // ---- refusals and exposure ----

    @Test
    @DisplayName("#1173 完成标准第 2 条（找不到）：不存在的账号 id → 404 PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND；把员工 id 当账号 id 同样 404")
    void anUnknownAccountIsNotFound() throws Exception {
        SignedIn admin = administrator();
        SignedIn target = person();

        for (UUID missing : List.of(UUID.randomUUID(), target.employeeId())) {
            MockHttpServletResponse answer = history(admin, missing, "");
            assertThat(answer.getStatus()).as(contentOf(answer)).isEqualTo(404);
            assertThat((String) JsonPath.read(contentOf(answer), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND");
        }
    }

    @Test
    @DisplayName("#1173 完成标准第 3 条：没有 platform.identity:role:read 的账号（默认员工）得 403，管理员模式下的超级管理员（持有该权限）得 200；超级管理员在管理员模式之外得到的状态与单账号 GET 相同；答案里没有登录名、企业微信 id、授权请求 id")
    void onlyAHolderOfRoleReadMayReadAndNothingElseLeaks() throws Exception {
        SignedIn admin = administrator();
        SignedIn plain = person();
        SignedIn target = person();
        replaceRoles(admin, target.userId(), "employee", createRole(admin));
        UUID superEmployee = newEmployeeId();
        employee(superEmployee, unit(), null);
        grantByOperations(superEmployee, "super_admin");
        SignedIn outsideMode = signIn(superEmployee);

        assertThat(history(plain, target.userId(), "").getStatus()).isEqualTo(403);
        MockHttpServletResponse allowed = history(admin, target.userId(), "");
        assertThat(allowed.getStatus()).isEqualTo(200);
        assertThat(contentOf(allowed)).doesNotContain("loginName").doesNotContain("wecom").doesNotContain("authorizationRequest").doesNotContain("auditEntry");
        int single = mvc.perform(get(ACCOUNTS + "/" + target.userId()).with(session(outsideMode))).andReturn().getResponse().getStatus();
        assertThat(single).isNotEqualTo(200);
        assertThat(history(outsideMode, target.userId(), "").getStatus()).isEqualTo(single);
        assertThat(history(administratorMode(outsideMode), target.userId(), "").getStatus()).isEqualTo(200);
    }
}
