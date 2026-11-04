package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * K-3b-4 (card section 6): the audit list, the audit export and the caller's own login records on the shared paging contract, seen only
 * through HTTP and the audit schema. Written before the implementation: on the baseline most of these are red.
 * Every test writes rows under an action or a person of its own, so tests do not see each other's rows.
 */
@Testcontainers
@TestPropertySource(properties = "forcitis.platform.audit.export.max-rows=5")
class K3b4AuditPagingIT extends AcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    static final String OWN = "/api/v1/platform/own-login-records";
    static final String LIST = AUDIT_ENTRIES;
    static final String EXPORT = "/api/v1/platform/audit-entries:export";
    static final String LOGIN_SUCCEEDED = "platform.identity.login.succeeded";
    static final String LOGIN_FAILED = "platform.identity.login.failed";
    static final String LOGOUT = "platform.identity.logout";
    static final String REQUEST_INVALID = "PLATFORM_WEB_REQUEST_INVALID";
    static final String CURSOR_INVALID = "PLATFORM_WEB_CURSOR_INVALID";
    static final String TOO_LARGE = "PLATFORM_AUDIT_EXPORT_TOO_LARGE";
    static final String RETIRED_CODE = "PLATFORM_AUDIT_QUERY_INVALID";
    static final Set<String> OWN_FIELDS = Set.of("occurredAt", "action", "outcome", "sourceAddress", "loginMethod");

    static final RequestPostProcessor READER = person(2, Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);
    static final RequestPostProcessor EXPORTER = person(3, Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT);

    static RequestPostProcessor owner(int person) {
        return person(person, Permission.Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ);
    }

    static UUID user(int person) {
        return UUID.fromString(principalName(person));
    }

    /** Ids that sort by their last digits, so that a tie on created_at is broken by a known id. */
    static UUID entryId(int serial) {
        return UUID.fromString(String.format("01900000-0000-7000-8000-%012d", 9_000_000 + serial));
    }

    record Row(UUID id, String action, UUID actor, UUID onBehalfOf, String kind, UUID subject, String outcome, String address, String method, Instant at) {
    }

    private static Row own(int serial, String action, int person, String outcome, String address, Instant at) {
        return new Row(entryId(serial), action, user(person), null, "USER_ACCOUNT", user(person), outcome, address, "WECOM_WORKBENCH", at);
    }

    private static Row plain(int serial, String action, UUID actor, UUID onBehalfOf, String outcome, String address, Instant at) {
        return new Row(entryId(serial), action, actor, onBehalfOf, "OPERATION", null, outcome, address, null, at);
    }

    private void insert(Row... rows) throws Exception {
        try (Connection audit = schemaConnection("platform_audit");
             PreparedStatement statement = audit.prepareStatement("insert into audit_entries (id, action, actor_id, on_behalf_of_id, subject_kind, subject_id, "
                     + "outcome_kind, source_address, login_method_kind, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing")) {
            for (Row row : rows) {
                statement.setObject(1, row.id());
                statement.setString(2, row.action());
                statement.setObject(3, row.actor());
                statement.setObject(4, row.onBehalfOf());
                statement.setString(5, row.kind());
                statement.setObject(6, row.subject());
                statement.setString(7, row.outcome());
                statement.setString(8, row.address());
                statement.setString(9, row.method());
                statement.setTimestamp(10, Timestamp.from(row.at()));
                statement.executeUpdate();
            }
        }
    }

    private long scalar(String sql, Object... parameters) throws Exception {
        try (Connection audit = schemaConnection("platform_audit"); PreparedStatement statement = audit.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private MockHttpServletResponse call(String path, RequestPostProcessor who, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path).with(who);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private static void assertRefused(MockHttpServletResponse response, String errorCode) {
        assertThat(response.getStatus()).as(bodyOf(response)).isEqualTo(400);
        assertThat((String) JsonPath.read(bodyOf(response), "$.errorCode")).isEqualTo(errorCode);
        assertThat(bodyOf(response)).doesNotContain(RETIRED_CODE);
    }

    private static String bodyOf(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (java.io.UnsupportedEncodingException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static List<String> csvEntryIds(MockHttpServletResponse export) {
        assertThat(export.getStatus()).as(bodyOf(export)).isEqualTo(200);
        String[] lines = bodyOf(export).split("\r\n");
        assertThat(lines[0]).startsWith("entryId,createdAt,action,actorId");
        return Arrays.stream(lines).skip(1).map(line -> line.substring(0, line.indexOf(','))).toList();
    }

    private static List<String> listEntryIds(MockHttpServletResponse list) {
        assertThat(list.getStatus()).as(bodyOf(list)).isEqualTo(200);
        return JsonPath.read(bodyOf(list), "$.items[*].entryId");
    }

    private static List<String> ids(Row... rows) {
        return Arrays.stream(rows).map(row -> row.id().toString()).toList();
    }

    // ---- own login records ----

    @Test
    @DisplayName("K-3b-4 本人登录记录返回 {items,nextCursor}，items 每项只有 occurredAt、action、outcome、sourceAddress、loginMethod")
    void ownRecordsAreAnObjectWithItemsAndNextCursor() throws Exception {
        insert(own(101, LOGIN_SUCCEEDED, 41, "SUCCEEDED", "10.41.0.1", NOW.minusSeconds(30)),
                own(102, LOGIN_FAILED, 41, "FAILED", "10.41.0.2", NOW.minusSeconds(20)),
                own(103, LOGOUT, 41, "SUCCEEDED", "10.41.0.3", NOW.minusSeconds(10)));

        MockHttpServletResponse own = call(OWN, owner(41));

        assertThat(own.getStatus()).as(bodyOf(own)).isEqualTo(200);
        Map<String, Object> body = JsonPath.read(bodyOf(own), "$");
        assertThat(body.keySet()).containsExactlyInAnyOrder("items", "nextCursor");
        assertThat(body.get("nextCursor")).isNull();
        List<Map<String, Object>> items = JsonPath.read(bodyOf(own), "$.items");
        assertThat(items).hasSize(3);
        for (Map<String, Object> item : items) {
            assertThat(item.keySet()).containsExactlyInAnyOrderElementsOf(OWN_FIELDS);
        }
        assertThat((List<String>) JsonPath.read(bodyOf(own), "$.items[*].sourceAddress")).containsExactly("10.41.0.3", "10.41.0.2", "10.41.0.1");
        assertThat(bodyOf(own)).doesNotContain("entryId", "subjectId", "actorId", "detail", entryId(101).toString());
    }

    @Test
    @DisplayName("K-3b-4 本人登录记录 limit=2 翻页：沿 nextCursor 取完全部，不重不漏，最后一页 nextCursor 为空")
    void ownRecordsPageThroughTiesWithoutGapOrRepeat() throws Exception {
        // A is newest; B, C, D share one created_at and are ordered by id, newest id first; E is oldest. Expected order: A, D, C, B, E.
        Instant tie = NOW.minusSeconds(600);
        insert(own(111, LOGIN_SUCCEEDED, 42, "SUCCEEDED", "10.42.0.1", NOW.minusSeconds(60)),
                own(112, LOGIN_SUCCEEDED, 42, "SUCCEEDED", "10.42.0.2", tie),
                own(113, LOGIN_FAILED, 42, "FAILED", "10.42.0.3", tie),
                own(114, LOGOUT, 42, "SUCCEEDED", "10.42.0.4", tie),
                own(115, LOGIN_SUCCEEDED, 42, "SUCCEEDED", "10.42.0.5", NOW.minusSeconds(1200)),
                own(116, LOGIN_SUCCEEDED, 43, "SUCCEEDED", "10.43.0.1", tie));

        List<String> seen = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletResponse page = cursor == null ? call(OWN, owner(42), "limit", "2") : call(OWN, owner(42), "limit", "2", "cursor", cursor);
            assertThat(page.getStatus()).as(bodyOf(page)).isEqualTo(200);
            List<String> addresses = JsonPath.read(bodyOf(page), "$.items[*].sourceAddress");
            seen.addAll(addresses);
            pageSizes.add(addresses.size());
            cursor = JsonPath.read(bodyOf(page), "$.nextCursor");
            assertThat(pageSizes).as("paging must terminate").hasSizeLessThanOrEqualTo(10);
        } while (cursor != null);

        assertThat(seen).containsExactly("10.42.0.1", "10.42.0.4", "10.42.0.3", "10.42.0.2", "10.42.0.5");
        assertThat(pageSizes).containsExactly(2, 2, 1);
    }

    // RULE-487
    @Test
    @DisplayName("K-3b-4 本人登录记录游标不能越出本人范围：把别人的条目 id 编进游标也取不到别人的行")
    void ownRecordsCursorCannotReachOtherPeoplesRows() throws Exception {
        insert(own(121, LOGIN_SUCCEEDED, 44, "SUCCEEDED", "10.44.0.1", NOW.minusSeconds(100)),
                own(122, LOGIN_SUCCEEDED, 44, "SUCCEEDED", "10.44.0.2", NOW.minusSeconds(200)),
                own(123, LOGIN_SUCCEEDED, 44, "SUCCEEDED", "10.44.0.3", NOW.minusSeconds(300)),
                own(124, LOGIN_SUCCEEDED, 45, "SUCCEEDED", "10.45.0.1", NOW.minusSeconds(150)),
                own(125, LOGIN_SUCCEEDED, 45, "SUCCEEDED", "10.45.0.2", NOW.minusSeconds(250)),
                plain(126, LOGIN_SUCCEEDED, user(46), null, "SUCCEEDED", "10.46.0.1", NOW.minusSeconds(260)));
        MockHttpServletResponse first = call(OWN, owner(44), "limit", "1");
        String real = JsonPath.read(bodyOf(first), "$.nextCursor");
        String decoded = new String(Base64.getUrlDecoder().decode(real), StandardCharsets.ISO_8859_1);
        assertThat(decoded).as("the cursor ends with the entry id of the last row of the page").endsWith(entryId(121).toString());
        String forged = decoded.substring(0, decoded.length() - 36) + entryId(124).toString();
        String forgedCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes(StandardCharsets.ISO_8859_1));

        MockHttpServletResponse answer = call(OWN, owner(44), "limit", "200", "cursor", forgedCursor);

        assertThat(answer.getStatus()).as(bodyOf(answer)).isIn(200, 400);
        assertThat(bodyOf(answer)).doesNotContain("10.45.0.", "10.46.0.");
        if (answer.getStatus() == 200) {
            assertThat((List<String>) JsonPath.read(bodyOf(answer), "$.items[*].sourceAddress")).isSubsetOf("10.44.0.1", "10.44.0.2", "10.44.0.3");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"cursor of the audit-entries list", "altered first byte", "version byte 9", "not base64", "well-encoded nonsense", "too long"})
    @DisplayName("K-3b-4 本人登录记录游标属于 audit-entries 列表、被篡改、版本不对、过长时答 400 PLATFORM_WEB_CURSOR_INVALID")
    void ownRecordsRefuseCursorsThatAreNotTheirOwn(String kind) throws Exception {
        insert(own(131, LOGIN_SUCCEEDED, 47, "SUCCEEDED", "10.47.0.1", NOW.minusSeconds(100)),
                own(132, LOGIN_SUCCEEDED, 47, "SUCCEEDED", "10.47.0.2", NOW.minusSeconds(200)));
        assertThat(call(OWN, owner(47)).getStatus()).as("control request").isEqualTo(200);
        String ownCursor = JsonPath.read(bodyOf(call(OWN, owner(47), "limit", "1")), "$.nextCursor");
        String bad = switch (kind) {
            case "cursor of the audit-entries list" -> JsonPath.read(bodyOf(call(LIST, READER, "actorId", user(47).toString(), "limit", "1")), "$.nextCursor");
            case "altered first byte" -> flipFirstByte(ownCursor, (byte) 'Z');
            case "version byte 9" -> flipFirstByte(ownCursor, (byte) '9');
            case "not base64" -> "***not base64***";
            case "well-encoded nonsense" -> Base64.getUrlEncoder().withoutPadding().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));
            default -> ownCursor.repeat(200);
        };

        assertRefused(call(OWN, owner(47), "cursor", bad), CURSOR_INVALID);
    }

    private static String flipFirstByte(String cursor, byte replacement) {
        byte[] bytes = Base64.getUrlDecoder().decode(cursor);
        bytes[0] = replacement;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @ParameterizedTest(name = "limit={0}")
    @ValueSource(strings = {"0", "201"})
    @DisplayName("K-3b-4 本人登录记录 limit=0 与 limit=201 答 400 PLATFORM_WEB_REQUEST_INVALID，响应里没有 PLATFORM_AUDIT_QUERY_INVALID")
    void ownRecordsRefuseLimitsOutsideOneToTwoHundred(String limit) throws Exception {
        assertThat(call(OWN, owner(48)).getStatus()).as("control request").isEqualTo(200);

        assertRefused(call(OWN, owner(48), "limit", limit), REQUEST_INVALID);
    }

    // ---- export parameters, filters, order ----

    @Test
    @DisplayName("K-3b-4 导出接受 onBehalfOfId、actionPrefix、sourceAddress、outcome 并按它们过滤，结果与列表同条件一致")
    void exportFiltersLikeTheListByTheFourAddedParameters() throws Exception {
        UUID actor = user(51);
        UUID proxy = user(52);
        Row a = plain(201, "k3b4.filters.alpha.one", actor, proxy, "SUCCEEDED", "10.51.0.1", NOW.minusSeconds(50));
        Row b = plain(202, "k3b4.filters.alpha.two", actor, null, "FAILED", "10.51.0.2", NOW.minusSeconds(40));
        Row c = plain(203, "k3b4.filters.beta.one", actor, proxy, "FAILED", "10.51.0.1", NOW.minusSeconds(30));
        Row d = plain(204, "k3b4.filters.beta.two", actor, null, "SUCCEEDED", "10.51.0.3", NOW.minusSeconds(20));
        insert(a, b, c, d);
        String[][] filters = {
                {"onBehalfOfId", proxy.toString()},
                {"actionPrefix", "k3b4.filters.alpha."},
                {"sourceAddress", "10.51.0.1"},
                {"outcome", "FAILED"},
        };
        List<List<String>> expected = List.of(ids(c, a), ids(b, a), ids(c, a), ids(c, b));

        for (int i = 0; i < filters.length; i++) {
            String[] params = {"actorId", actor.toString(), filters[i][0], filters[i][1]};
            assertThat(listEntryIds(call(LIST, READER, params))).as("list %s", filters[i][0]).isEqualTo(expected.get(i));
            assertThat(csvEntryIds(call(EXPORT, EXPORTER, params))).as("export %s", filters[i][0]).isEqualTo(expected.get(i));
        }
    }

    @Test
    @DisplayName("K-3b-4 导出与列表同序：created_at 降序，同刻按 id 降序")
    void exportAndListAnswerInTheSameFixedOrder() throws Exception {
        UUID actor = user(53);
        Instant tie = NOW.minusSeconds(500);
        Row newest = plain(211, "k3b4.order.a", actor, null, "SUCCEEDED", "10.53.0.1", NOW.minusSeconds(10));
        Row tieLow = plain(212, "k3b4.order.b", actor, null, "SUCCEEDED", "10.53.0.2", tie);
        Row tieMid = plain(213, "k3b4.order.c", actor, null, "SUCCEEDED", "10.53.0.3", tie);
        Row tieHigh = plain(214, "k3b4.order.d", actor, null, "SUCCEEDED", "10.53.0.4", tie);
        Row oldest = plain(215, "k3b4.order.e", actor, null, "SUCCEEDED", "10.53.0.5", NOW.minusSeconds(900));
        insert(oldest, tieMid, newest, tieHigh, tieLow);
        List<String> expected = ids(newest, tieHigh, tieMid, tieLow, oldest);

        assertThat(listEntryIds(call(LIST, READER, "actorId", actor.toString()))).isEqualTo(expected);
        assertThat(csvEntryIds(call(EXPORT, EXPORTER, "actorId", actor.toString()))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} ?sort -> 400")
    @ValueSource(strings = {LIST, EXPORT})
    @DisplayName("K-3b-4 列表与导出传 sort 得 400 PLATFORM_WEB_REQUEST_INVALID，invalidParameters 含 sort（固定顺序，sort 白名单为空）")
    void sortIsAnUnknownParameter(String path) throws Exception {
        RequestPostProcessor who = path.equals(LIST) ? READER : EXPORTER;
        assertThat(call(path, who, "actorId", user(54).toString()).getStatus()).as("control request").isEqualTo(200);

        MockHttpServletResponse refused = call(path, who, "sort", "-createdAt");

        assertRefused(refused, REQUEST_INVALID);
        assertThat((String) JsonPath.read(bodyOf(refused), "$.invalidParameters")).contains("sort");
    }

    @ParameterizedTest(name = "export ?{0} -> 400")
    @ValueSource(strings = {"limit", "cursor"})
    @DisplayName("K-3b-4 导出收到 limit 或 cursor 答 400 PLATFORM_WEB_REQUEST_INVALID 并点名参数")
    void exportRefusesPagingParameters(String parameter) throws Exception {
        MockHttpServletResponse refused = call(EXPORT, EXPORTER, parameter, "10");

        assertRefused(refused, REQUEST_INVALID);
        assertThat((String) JsonPath.read(bodyOf(refused), "$.invalidParameters")).contains(parameter);
    }

    static Stream<Arguments> invalidFilters() {
        String from = "2026-11-02T01:00:00Z";
        String earlier = "2026-11-02T00:00:00Z";
        String id = "01900000-0000-7000-8000-000000000777";
        List<String[]> bad = List.of(
                new String[] {"subjectId without subjectKind", "subjectId", id},
                new String[] {"subjectKind with no object given an id", "subjectKind", "OPERATION", "subjectId", id},
                new String[] {"from equal to to", "from", from, "to", from},
                new String[] {"from after to", "from", from, "to", earlier});
        return Stream.of(LIST, EXPORT).flatMap(path -> bad.stream().map(parameters -> Arguments.of(path, parameters[0], parameters)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidFilters")
    @DisplayName("K-3b-4 导出过滤条件非法（subjectId 无 subjectKind、from 不早于 to）答 400 PLATFORM_WEB_REQUEST_INVALID；对列表同样断言（共用校验）")
    void invalidFiltersAreRefusedTheSameWayByBoth(String path, String label, String[] caseAndParameters) throws Exception {
        RequestPostProcessor who = path.equals(LIST) ? READER : EXPORTER;
        String[] parameters = Arrays.copyOfRange(caseAndParameters, 1, caseAndParameters.length);
        assertThat(call(path, who, "actorId", user(55).toString()).getStatus()).as("control request").isEqualTo(200);

        assertRefused(call(path, who, parameters), REQUEST_INVALID);
    }

    // ---- export ceiling, no count ----

    @Test
    @DisplayName("K-3b-4 导出匹配行数恰等于上限时成功，上限加一行时答 422 PLATFORM_AUDIT_EXPORT_TOO_LARGE 且不写导出审计条目")
    void exportSucceedsAtTheCeilingAndFailsOneRowAbove() throws Exception {
        UUID actor = user(56);
        List<Row> atCeiling = new ArrayList<>();
        List<Row> aboveCeiling = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            atCeiling.add(plain(300 + i, "k3b4.ceiling.five", actor, null, "SUCCEEDED", "10.56.0.1", NOW.minusSeconds(100 + i)));
        }
        for (int i = 0; i < 6; i++) {
            aboveCeiling.add(plain(310 + i, "k3b4.ceiling.six", actor, null, "SUCCEEDED", "10.56.0.2", NOW.minusSeconds(100 + i)));
        }
        insert(atCeiling.toArray(Row[]::new));
        insert(aboveCeiling.toArray(Row[]::new));
        String exported = "platform.audit.audit-entry.exported";

        MockHttpServletResponse ok = call(EXPORT, EXPORTER, "action", "k3b4.ceiling.five");
        assertThat(csvEntryIds(ok)).hasSize(5);
        assertThat(scalar("select count(*) from audit_entries where action = ? and detail like ?", exported, "rows=5 filter=action=k3b4.ceiling.five%")).isEqualTo(1);

        MockHttpServletResponse refused = call(EXPORT, EXPORTER, "action", "k3b4.ceiling.six");
        assertThat(refused.getStatus()).as(bodyOf(refused)).isEqualTo(422);
        assertThat((String) JsonPath.read(bodyOf(refused), "$.errorCode")).isEqualTo(TOO_LARGE);
        assertThat(scalar("select count(*) from audit_entries where action = ? and detail like ?", exported, "%k3b4.ceiling.six%")).isZero();
    }

    @Test
    @DisplayName("K-3b-4 导出执行期间数据库没有收到 count 语句")
    void exportSendsNoCountStatementToTheDatabase() throws Exception {
        UUID actor = user(57);
        insert(plain(321, "k3b4.nocount", actor, null, "SUCCEEDED", "10.57.0.1", NOW.minusSeconds(10)),
                plain(322, "k3b4.nocount", actor, null, "SUCCEEDED", "10.57.0.1", NOW.minusSeconds(20)));
        try (Connection admin = schemaConnection("platform_audit"); Statement statement = admin.createStatement()) {
            statement.execute("alter system set log_statement = 'all'");
            statement.execute("select pg_reload_conf()");
        }
        try {
            String startMarker = "k3b4-window-start-" + UUID.randomUUID();
            String endMarker = "k3b4-window-end-" + UUID.randomUUID();
            marker(startMarker);
            awaitLog(startMarker);

            MockHttpServletResponse export = call(EXPORT, EXPORTER, "action", "k3b4.nocount");

            marker(endMarker);
            String log = awaitLog(endMarker);
            assertThat(csvEntryIds(export)).hasSize(2);
            String window = log.substring(log.indexOf(startMarker), log.lastIndexOf(endMarker));
            assertThat(window).as("the capture works: the export's select on audit_entries is in the log").containsIgnoringCase("from platform_audit.audit_entries");
            assertThat(window.toLowerCase()).as("no count statement during the export").doesNotContain("count(");
        } finally {
            try (Connection admin = schemaConnection("platform_audit"); Statement statement = admin.createStatement()) {
                statement.execute("alter system reset log_statement");
                statement.execute("select pg_reload_conf()");
            }
        }
        Class<?> queries = classNamed("com.forcitis.one.platform.audit.domain.AuditEntryQueries");
        assertThat(Arrays.stream(queries.getMethods()).map(java.lang.reflect.Method::getName)).as("AuditEntryQueries has no count method").doesNotContain("count");
    }

    private void marker(String text) throws Exception {
        try (Connection audit = schemaConnection("platform_audit"); Statement statement = audit.createStatement()) {
            statement.execute("select '" + text + "'");
        }
    }

    private String awaitLog(String text) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> postgres.getLogs().contains(text));
        return postgres.getLogs();
    }

    private static Class<?> classNamed(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("class not found: " + name, missing);
        }
    }
}
