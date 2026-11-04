package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
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
 * #1173 (acceptance part, PR 2): {@code GET /api/v1/platform/user-accounts?status=&roleCode=&limit=&cursor=}, the account list of the account
 * administration page. Read with {@code platform.identity:role:read}; the roles and accounts are made through the existing endpoints, and the
 * single-account GET is the oracle for status and role codes. Display name and employee code of the fixture employees are fixed by
 * AccountsAcceptanceSupport#employee ("Synthetic person N", "ACC" + N), so the values are asserted, not only their presence.
 */
class UserAccountListHttpIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(UserAccountListHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String ACCOUNTS = "/api/v1/platform/user-accounts";
    private static final String ROLES = "/api/v1/platform/roles";
    private static final Set<String> ITEM_KEYS = Set.of("userId", "status", "roleCodes", "displayName", "employeeCode");
    private static final AtomicInteger NUMBER = new AtomicInteger();

    // ---- world ----

    private record Person(UUID employee, SignedIn session) {
        UUID user() {
            return session.userId();
        }
    }

    private UUID unit;

    private UUID unit() throws Exception {
        if (unit == null) {
            unit = newOrgUnitId();
            orgUnit(unit, null, null);
        }
        return unit;
    }

    private Person person() throws Exception {
        UUID employee = newEmployeeId();
        employee(employee, unit(), null);
        return new Person(employee, signIn(employee));
    }

    private SignedIn administrator() throws Exception {
        UUID employee = newEmployeeId();
        employee(employee, unit(), null);
        grantByOperations(employee, "super_admin");
        return administratorMode(signIn(employee));
    }

    private String createRole(SignedIn admin, String prefix, String... permissions) throws Exception {
        String code = prefix + "_" + NUMBER.incrementAndGet() + "_" + Long.toString(System.nanoTime() % 100000, 36);
        String codes = String.join(",", java.util.Arrays.stream(permissions).map(permission -> "\"" + permission + "\"").toList());
        MockHttpServletResponse role = mvc.perform(post(ROLES).with(csrf()).with(session(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Synthetic " + code + "\",\"enabled\":true,\"permissionCodes\":[" + codes + "]}")).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(201);
        return code;
    }

    private void replaceRoles(SignedIn admin, UUID account, String... roleCodes) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + "/" + account).with(session(admin))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        String body = "{\"roleCodes\":[" + String.join(",", java.util.Arrays.stream(roleCodes).map(code -> "\"" + code + "\"").toList()) + "]}";
        MockHttpServletResponse replaced = mvc.perform(post(ACCOUNTS + "/" + account + ":replace-roles").header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(admin))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertThat(replaced.getStatus()).as(replaced.getContentAsString()).isEqualTo(204);
    }

    private void disable(SignedIn admin, UUID account) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + "/" + account).with(session(admin))).andReturn().getResponse();
        MockHttpServletResponse disabled = mvc.perform(post(ACCOUNTS + "/" + account + ":disable").header("If-Match", read.getHeader("ETag")).with(csrf()).with(session(admin)))
                .andReturn().getResponse();
        assertThat(disabled.getStatus()).as(disabled.getContentAsString()).isEqualTo(204);
    }

    // ---- reading the list ----

    private MockHttpServletResponse list(SignedIn caller, String query) throws Exception {
        return mvc.perform(get(ACCOUNTS + query).with(session(caller))).andReturn().getResponse();
    }

    private List<Map<String, Object>> items(MockHttpServletResponse answer) {
        assertThat(answer.getStatus()).as(contentOf(answer)).isEqualTo(200);
        return JsonPath.read(contentOf(answer), "$.items");
    }

    private static String contentOf(MockHttpServletResponse answer) {
        try {
            return answer.getContentAsString();
        } catch (java.io.UnsupportedEncodingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private Set<String> userIds(SignedIn caller, String query) throws Exception {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : items(list(caller, query))) {
            ids.add((String) item.get("userId"));
        }
        return Set.copyOf(ids);
    }

    /** Every page of the query, following the cursors with the given page size. */
    private List<Map<String, Object>> allPages(SignedIn caller, String query, int limit) throws Exception {
        List<Map<String, Object>> all = new ArrayList<>();
        String cursor = null;
        for (int pages = 0; pages < 200; pages++) {
            MockHttpServletResponse page = list(caller, query + (query.isEmpty() ? "?" : "&") + "limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor));
            all.addAll(items(page));
            cursor = JsonPath.read(contentOf(page), "$.nextCursor");
            if (cursor == null) {
                return all;
            }
        }
        throw new AssertionError("the cursors did not run out");
    }

    // ---- filters ----

    @Test
    @DisplayName("#1173 完成标准第 1 条（roleCode 只匹配未结束的授予）：持有角色的账号出现在 ?roleCode=该角色；该角色被替换掉之后不再出现（已结束的授予不算）；未知角色码 → 200 空页、无下一页")
    void theRoleCodeMatchesOpenGrantsOnly() throws Exception {
        SignedIn admin = administrator();
        String roleA = createRole(admin, "synthetic_list_a");
        Person first = person();
        Person second = person();
        Person third = person();
        replaceRoles(admin, first.user(), "employee", roleA);
        replaceRoles(admin, second.user(), "employee", roleA);

        assertThat(userIds(admin, "?roleCode=" + roleA)).containsExactlyInAnyOrder(first.user().toString(), second.user().toString());

        replaceRoles(admin, second.user(), "employee");

        assertThat(userIds(admin, "?roleCode=" + roleA)).containsExactly(first.user().toString());
        MockHttpServletResponse unknown = list(admin, "?roleCode=synthetic_no_such_role");
        assertThat(items(unknown)).isEmpty();
        assertThat((Object) JsonPath.read(contentOf(unknown), "$.nextCursor")).isNull();
        assertThat(userIds(admin, "?roleCode=" + roleA)).doesNotContain(third.user().toString());
    }

    @Test
    @DisplayName("#1173 完成标准第 1 条（status 与 roleCode 组合）：ENABLED 与 DISABLED 各只列该状态的账号，条目的 status 与单账号 GET 一致；两个过滤条件取交集；status 取其他值（小写、未定义的值、ALL）→ 400")
    void theFiltersCombineAndAnUnknownStatusIsABadRequest() throws Exception {
        SignedIn admin = administrator();
        String roleB = createRole(admin, "synthetic_list_b");
        String roleC = createRole(admin, "synthetic_list_c");
        Person enabled = person();
        Person disabled = person();
        Person other = person();
        replaceRoles(admin, enabled.user(), "employee", roleB);
        replaceRoles(admin, disabled.user(), "employee", roleB);
        replaceRoles(admin, other.user(), "employee", roleC);
        disable(admin, disabled.user());

        assertThat(userIds(admin, "?status=ENABLED&roleCode=" + roleB)).containsExactly(enabled.user().toString());
        assertThat(userIds(admin, "?status=DISABLED&roleCode=" + roleB)).containsExactly(disabled.user().toString());
        assertThat(userIds(admin, "?roleCode=" + roleB)).containsExactlyInAnyOrder(enabled.user().toString(), disabled.user().toString());
        assertThat(userIds(admin, "?status=DISABLED&roleCode=" + roleC)).isEmpty();
        assertThat(allPages(admin, "?status=DISABLED", 50)).isNotEmpty().allSatisfy(item -> assertThat(item.get("status")).isEqualTo("DISABLED"));
        assertThat(allPages(admin, "?status=ENABLED", 50)).isNotEmpty().allSatisfy(item -> assertThat(item.get("status")).isEqualTo("ENABLED"))
                .noneMatch(item -> disabled.user().toString().equals(item.get("userId")));
        for (String bad : List.of("enabled", "ACTIVE", "ALL")) {
            MockHttpServletResponse refused = list(admin, "?status=" + bad);
            assertThat(refused.getStatus()).as("status " + bad + ": " + contentOf(refused)).isEqualTo(400);
        }
    }

    // ---- the item ----

    @Test
    @DisplayName("#1173 完成标准第 1 条（条目）：条目的键恰好是 userId、status、roleCodes、displayName、employeeCode；状态与角色码和单账号 GET 一致；姓名与工号是该员工行里的值；员工行不存在时姓名和工号为空（账号仍在列表里）")
    void anItemHasExactlyTheFiveKeys() throws Exception {
        SignedIn admin = administrator();
        String role = createRole(admin, "synthetic_list_d");
        Person known = person();
        Person orphan = person();
        replaceRoles(admin, known.user(), "employee", role);
        replaceRoles(admin, orphan.user(), "employee", role);
        removeFromSnapshot(orphan.employee());

        List<Map<String, Object>> found = items(list(admin, "?roleCode=" + role));

        assertThat(found).hasSize(2);
        Map<String, Object> item = found.stream().filter(candidate -> known.user().toString().equals(candidate.get("userId"))).findFirst().orElseThrow();
        assertThat(item.keySet()).containsExactlyInAnyOrderElementsOf(ITEM_KEYS);
        assertThat(item.get("displayName")).isEqualTo("Synthetic person " + serialOf(known.employee()));
        assertThat(item.get("employeeCode")).isEqualTo("ACC" + serialOf(known.employee()));
        MockHttpServletResponse single = mvc.perform(get(ACCOUNTS + "/" + known.user()).with(session(admin))).andReturn().getResponse();
        assertThat(item.get("status")).isEqualTo(JsonPath.read(contentOf(single), "$.status"));
        assertThat((List<String>) item.get("roleCodes")).containsExactlyInAnyOrderElementsOf(JsonPath.<List<String>>read(contentOf(single), "$.roleCodes"));
        Map<String, Object> orphaned = found.stream().filter(candidate -> orphan.user().toString().equals(candidate.get("userId"))).findFirst().orElseThrow();
        assertThat(orphaned.get("displayName")).isNull();
        assertThat(orphaned.get("employeeCode")).isNull();
        assertThat(contentOf(list(admin, "?roleCode=" + role))).as("nothing else of the person leaks").doesNotContain("loginName").doesNotContain("wecom").doesNotContain("email");
    }

    // ---- order and paging ----

    @Test
    @DisplayName("#1173 完成标准第 1 条（排序与分页）：按开户时间升序、开户时间相同的按用户 id 升序（开户时间与 id 先后不一致、也有相同的情形）；逐页取（limit=2，沿游标）与一次取完的结果相同、没有重复；第一页在还有数据时带下一页游标，游标用尽后为空")
    void theListIsOrderedByCreationThenUserIdAndPaged() throws Exception {
        SignedIn admin = administrator();
        String role = createRole(admin, "synthetic_list_e");
        List<String> mine = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Person person = person();
            replaceRoles(admin, person.user(), "employee", role);
            mine.add(person.user().toString());
        }
        // created_at against id order: the first made is the latest created; the last two share one moment, so only the id tells them apart
        long[] minutes = {40, 30, 20, 10, 10};
        try (java.sql.Connection identity = schemaConnection("platform_identity")) {
            for (int i = 0; i < mine.size(); i++) {
                try (java.sql.PreparedStatement statement = identity.prepareStatement("update user_accounts set created_at = ? where user_id = ?")) {
                    statement.setObject(1, java.time.OffsetDateTime.parse("2026-10-01T00:00:00Z").plusMinutes(minutes[i]));
                    statement.setObject(2, UUID.fromString(mine.get(i)));
                    statement.executeUpdate();
                }
            }
        }
        List<String> expected = List.of(mine.get(3), mine.get(4), mine.get(2), mine.get(1), mine.get(0));

        List<Map<String, Object>> byTwos = allPages(admin, "?roleCode=" + role, 2);
        List<Map<String, Object>> whole = allPages(admin, "?roleCode=" + role, 100);
        MockHttpServletResponse firstPage = list(admin, "?roleCode=" + role + "&limit=2");

        assertThat(byTwos).isEqualTo(whole);
        List<String> ids = whole.stream().map(item -> (String) item.get("userId")).toList();
        assertThat(ids).containsExactlyInAnyOrderElementsOf(mine).doesNotHaveDuplicates();
        assertThat(ids).as("by created_at, then user id").containsExactlyElementsOf(expected);
        assertThat(items(firstPage)).hasSize(2);
        assertThat((String) JsonPath.read(contentOf(firstPage), "$.nextCursor")).isNotBlank();
    }

    // ---- who may read ----

    @Test
    @DisplayName("#1173 完成标准第 3 条：没有 platform.identity:role:read 的账号（默认员工）得 403；管理员模式下的超级管理员（持有该权限）得 200；超级管理员在管理员模式之外得到的状态与单账号 GET 在同一会话下的状态相同，在管理员模式内为 200")
    void onlyAHolderOfRoleReadMayList() throws Exception {
        SignedIn admin = administrator();
        Person plain = person();
        UUID superEmployee = newEmployeeId();
        employee(superEmployee, unit(), null);
        grantByOperations(superEmployee, "super_admin");
        SignedIn outsideMode = signIn(superEmployee);

        assertThat(list(plain.session(), "").getStatus()).isEqualTo(403);
        assertThat(list(admin, "").getStatus()).isEqualTo(200);
        int single = mvc.perform(get(ACCOUNTS + "/" + plain.user()).with(session(outsideMode))).andReturn().getResponse().getStatus();
        assertThat(single).as("the single-account GET outside the administrator mode").isNotEqualTo(200);
        assertThat(list(outsideMode, "").getStatus()).isEqualTo(single);
        assertThat(list(administratorMode(outsideMode), "").getStatus()).isEqualTo(200);
    }
}
