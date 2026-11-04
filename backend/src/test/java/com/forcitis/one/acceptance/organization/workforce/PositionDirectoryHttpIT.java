package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1171, the position directory: {@code GET /api/v1/organization/positions?status=&limit=&cursor=} and {@code GET .../positions/{positionId}} give every
 * position's code, name, status and the dates of its current version, nothing else (ADR-0068 section 3.x: no headcount, no duties), to every logged-in
 * user (user 2026-11-04, intent 055504); an anonymous call answers 401. The register line is NOT_SCOPE_BOUND, company-level reference data.
 *
 * <p>Contract fixed here, because the card names no field: a row is {@code positionId, code, name, status, effectiveFromDate, effectiveToDate}
 * (the last one absent or null while the version is open); a page is {@code items} and {@code nextCursor} (null on the last page) as the other
 * lists of the module answer. The directory is read from the position versions, not from the snapshot: the current version (not superseded, in force
 * today) decides code, name and status.
 */
class PositionDirectoryHttpIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(PositionDirectoryHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String POSITIONS = "/api/v1/organization/positions";
    static final LocalDate SINCE = LocalDate.of(2026, 1, 1);
    static final LocalDate RENAMED_ON = LocalDate.of(2026, 6, 1);
    static final Set<String> ROW_KEYS = Set.of("positionId", "code", "name", "status", "effectiveFromDate", "effectiveToDate");

    private final Map<String, UUID> positionIds = new java.util.LinkedHashMap<>();

    @BeforeAll
    void theWorld() throws Exception {
        unit("PD-U", null);
        position("PD-SEED");
        for (String key : List.of("admin1", "admin2", "reader", "plain")) {
            person(key, "PD-U", "PD-SEED");
        }
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        ordinaryRole(administrator("admin1"), "pd-org-unit-reader", "ALL", null, ORG_UNIT_READ);
        grantOrdinaryRoles(administrator("admin1"), "reader", "pd-org-unit-reader");
        try (Connection workforce = schemaConnection("organization_workforce")) {
            version(workforce, "active", "PD-ACTIVE", "Synthetic active post", "ACTIVE", SINCE, null);
            version(workforce, "disabled", "PD-DISABLED", "Synthetic disabled post", "DISABLED", SINCE, null);
            version(workforce, "renamed", "PD-RENAMED", "Synthetic old name", "ACTIVE", SINCE, RENAMED_ON);
            version(workforce, "renamed", "PD-RENAMED", "Synthetic new name", "ACTIVE", RENAMED_ON, null);
        }
    }

    /** One position version written directly (the MIGRATION channel); the position row itself on first use. */
    private void version(Connection workforce, String key, String code, String name, String status, LocalDate from, LocalDate to) throws Exception {
        boolean first = !positionIds.containsKey(key);
        UUID id = positionIds.computeIfAbsent(key, k -> UUID.randomUUID());
        if (first) {
            run(workforce, "insert into positions (id, code, name, sort_order, status) values (?, ?, ?, 1, ?)", id, code, name, status);
        }
        run(workforce, """
                insert into position_versions (position_version_id, position_id, effective_from_date, effective_to_date, revision_number, code, name, sort_order, status,
                    change_channel_kind, closed_at)
                values (?, ?, ?, ?, 0, ?, ?, 1, ?, 'MIGRATION', case when ?::date is null then null else now() end)""",
                UUID.randomUUID(), id, from, to, code, name, status, to);
    }

    private static void run(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        }
    }

    private MockHttpServletResponse read(String key, String url) throws Exception {
        return mvc.perform(get(url).with(as(session(key)))).andReturn().getResponse();
    }

    private List<Map<String, Object>> itemsOf(MockHttpServletResponse answer) throws Exception {
        return JsonPath.read(answer.getContentAsString(), "$.items");
    }

    /** Every row of the list, one row per page. */
    private List<Map<String, Object>> all(String key, String query) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 30; page++) {
            MockHttpServletResponse answer = read(key, POSITIONS + "?" + query + (query.isEmpty() ? "" : "&") + "limit=1" + (cursor == null ? "" : "&cursor=" + cursor));
            require(answer, 200, key + " lists the positions");
            assertThat(itemsOf(answer)).hasSizeLessThanOrEqualTo(1);
            rows.addAll(itemsOf(answer));
            cursor = JsonPath.read(answer.getContentAsString(), "$.nextCursor");
            if (cursor == null) {
                return rows;
            }
        }
        throw new AssertionError("more than 30 pages of one row");
    }

    private static Set<String> codes(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> String.valueOf(row.get("code"))).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("#1171 完成标准第 4 条（目录内容）：目录给出每个岗位的编码、名称、状态和当前版本的起止日，已被改名的岗位只给当前名称；行里只有约定的键（无编制人数、无职责）")
    void theDirectoryShowsTheCurrentVersionOfEveryPosition() throws Exception {
        List<Map<String, Object>> rows = all("reader", "");

        assertThat(codes(rows)).contains("PD-ACTIVE", "PD-DISABLED", "PD-RENAMED");
        Map<String, Object> renamed = rows.stream().filter(row -> "PD-RENAMED".equals(row.get("code"))).findFirst().orElseThrow();
        assertThat(rows.stream().filter(row -> "PD-RENAMED".equals(row.get("code"))).count()).as("one row per position, not per version").isEqualTo(1);
        assertThat(renamed.get("name")).isEqualTo("Synthetic new name");
        assertThat(renamed.get("status")).isEqualTo("ACTIVE");
        assertThat(renamed.get("positionId")).isEqualTo(positionIds.get("renamed").toString());
        assertThat(renamed.get("effectiveFromDate")).isEqualTo(RENAMED_ON.toString());
        assertThat(renamed.get("effectiveToDate")).isNull();
        Map<String, Object> disabled = rows.stream().filter(row -> "PD-DISABLED".equals(row.get("code"))).findFirst().orElseThrow();
        assertThat(disabled.get("status")).isEqualTo("DISABLED");
        for (Map<String, Object> row : rows) {
            assertThat(row.keySet()).as("a position row carries only the agreed keys").isSubsetOf(ROW_KEYS).contains("positionId", "code", "name", "status", "effectiveFromDate");
        }
    }

    @Test
    @DisplayName("#1171 完成标准第 4 条（状态过滤与分页）：status=ACTIVE 不含停用岗位，status=DISABLED 只含停用岗位；limit=1 逐页取遍的结果与不分页相同；不认识的 status 是 400")
    void theStatusFilterAndThePaging() throws Exception {
        Set<String> everything = codes(all("reader", ""));
        assertThat(codes(all("reader", "status=ACTIVE"))).contains("PD-ACTIVE", "PD-RENAMED").doesNotContain("PD-DISABLED");
        assertThat(codes(all("reader", "status=DISABLED"))).contains("PD-DISABLED").doesNotContain("PD-ACTIVE", "PD-RENAMED");
        MockHttpServletResponse whole = read("reader", POSITIONS + "?limit=200");
        require(whole, 200, "reader lists the positions in one page");
        assertThat(codes(itemsOf(whole))).isEqualTo(everything);
        assertThat(read("reader", POSITIONS + "?status=NOT_A_STATUS").getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("#1171 完成标准第 4 条（单个岗位）：按 positionId 取得与列表同一行；不存在的 id 是 404 且错误码以 _NOT_FOUND 结尾")
    void onePositionAndAnUnknownId() throws Exception {
        MockHttpServletResponse found = read("reader", POSITIONS + "/" + positionIds.get("renamed"));

        require(found, 200, "reader reads one position");
        Map<String, Object> body = JsonPath.parse(found.getContentAsString()).read("$");
        assertThat(body).containsEntry("positionId", positionIds.get("renamed").toString()).containsEntry("code", "PD-RENAMED").containsEntry("name", "Synthetic new name");
        assertThat(body.keySet()).isSubsetOf(ROW_KEYS);
        MockHttpServletResponse unknown = read("reader", POSITIONS + "/" + UUID.randomUUID());
        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(String.valueOf(JsonPath.<String>read(unknown.getContentAsString(), "$.errorCode"))).endsWith("_NOT_FOUND");
    }

    @Test
    @DisplayName("#1171 完成标准第 6 条（任何登录用户都能看）：只有员工角色的人、只有 org-unit:read 的人都得到 200，目录内容相同；未登录得到 401（列表与单个都是）")
    void everyLoggedInUserReadsTheDirectoryAndAnAnonymousCallIsRefused() throws Exception {
        Set<String> expected = codes(all("reader", ""));

        assertThat(codes(all("plain", ""))).isEqualTo(expected);
        assertThat(read("plain", POSITIONS + "/" + positionIds.get("active")).getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get(POSITIONS)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get(POSITIONS + "/" + positionIds.get("active"))).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
