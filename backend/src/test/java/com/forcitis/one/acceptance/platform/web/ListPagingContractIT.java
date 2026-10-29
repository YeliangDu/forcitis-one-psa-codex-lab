package com.forcitis.one.acceptance.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * K-3a list paging contract, seen only through HTTP (NEXT_BATCH_PLAN section 8, one-coordinator ruling 2026-10-15; NAMING_STANDARD A-3, A-4).
 * Four lists stand for all of them: legal entities, audit entries, source references, work calendars.
 * Ruling item 1: cursor opaque, versioned, unsigned, structure and types checked, bad ones 400 PLATFORM_WEB_CURSOR_INVALID.
 * Ruling item 3: unknown query parameter 400 PLATFORM_WEB_REQUEST_INVALID naming the parameter.
 * Tests whose name says "characterization" already pass on the pre-K-3a code and must stay green.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListPagingContractIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(ListPagingContractIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String WORK_CALENDARS = "/api/v1/organization/work-calendars";
    private static final String SOURCE_REFERENCES = "/api/v1/platform/source-references";
    private static final int SEEDED_LEGAL_ENTITIES = 51;
    private static final String REQUEST_INVALID = "PLATFORM_WEB_REQUEST_INVALID";
    private static final String CURSOR_INVALID = "PLATFORM_WEB_CURSOR_INVALID";

    private static final RequestPostProcessor SOURCE_REFERENCE_READER = person(11, "platform.integration:source-reference:update");
    private static final RequestPostProcessor CALENDAR_READER = person(21, "organization.attendance:work-calendar:read");

    record Endpoint(String label, String path, List<String> fixedParams, RequestPostProcessor who) {
        @Override
        public String toString() {
            return label;
        }
    }

    static final Endpoint LEGAL_ENTITIES_LIST = new Endpoint("legal-entities", LEGAL_ENTITIES, List.of(), LEGAL_ENTITY_MAINTAINER);
    static final Endpoint AUDIT_ENTRIES_LIST = new Endpoint("audit-entries", AUDIT_ENTRIES, List.of(), AUDIT_READER);
    static final Endpoint SOURCE_REFERENCES_LIST = new Endpoint("source-references", SOURCE_REFERENCES,
            List.of("sourceSystem", "ACCOUNTING_SYSTEM", "sourceObjectKind", "LEGAL_ENTITY"), SOURCE_REFERENCE_READER);
    static final Endpoint WORK_CALENDARS_LIST = new Endpoint("work-calendars", WORK_CALENDARS, List.of(), CALENDAR_READER);

    static Stream<Endpoint> lists() {
        return Stream.of(LEGAL_ENTITIES_LIST, AUDIT_ENTRIES_LIST, SOURCE_REFERENCES_LIST, WORK_CALENDARS_LIST);
    }

    static Stream<Arguments> endpoints() {
        return lists().map(Arguments::of);
    }

    static Stream<Arguments> endpointsAndBadLimits() {
        return lists().flatMap(e -> Stream.of("0", "-1", "201", "abc", "99999999999").map(v -> Arguments.of(e, v)));
    }

    private List<UUID> legalEntities;

    @BeforeAll
    void seedLegalEntities() throws Exception {
        legalEntities = new ArrayList<>();
        for (int i = 0; i < SEEDED_LEGAL_ENTITIES; i++) {
            String code = "PG" + (char) ('A' + i / 26) + (char) ('A' + i % 26);
            legalEntities.add(createLegalEntity(code, "Synthetic paging " + code));
        }
    }

    private MockHttpServletRequestBuilder request(Endpoint endpoint, String... extraParams) {
        MockHttpServletRequestBuilder request = get(endpoint.path()).with(endpoint.who());
        for (int i = 0; i < endpoint.fixedParams().size(); i += 2) {
            request.param(endpoint.fixedParams().get(i), endpoint.fixedParams().get(i + 1));
        }
        for (int i = 0; i < extraParams.length; i += 2) {
            request.param(extraParams[i], extraParams[i + 1]);
        }
        return request;
    }

    private MockHttpServletResponse call(Endpoint endpoint, String... extraParams) throws Exception {
        return mvc.perform(request(endpoint, extraParams)).andReturn().getResponse();
    }

    /** The same request without the parameter under test must succeed, so a 400 below is caused by that parameter alone. */
    private void controlRequestSucceeds(Endpoint endpoint) throws Exception {
        MockHttpServletResponse control = call(endpoint);
        assertThat(control.getStatus()).as("control request to %s: %s", endpoint, control.getContentAsString()).isEqualTo(200);
    }

    private void assertRefused(MockHttpServletResponse response, String errorCode) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.errorCode")).isEqualTo(errorCode);
    }

    private String firstPageCursorOfLegalEntities() throws Exception {
        MockHttpServletResponse first = call(LEGAL_ENTITIES_LIST, "limit", "1");
        assertThat(first.getStatus()).isEqualTo(200);
        String cursor = JsonPath.read(first.getContentAsString(), "$.nextCursor");
        assertThat(cursor).as("%d legal entities were seeded, so a page of 1 has a next page", SEEDED_LEGAL_ENTITIES).isNotBlank();
        return cursor;
    }

    // ---- limit: A-3, K-3a "limit 默认 50、上限 200" ----

    @ParameterizedTest(name = "{0} limit={1} -> 400")
    @MethodSource("endpointsAndBadLimits")
    @DisplayName("K-3a A-3 limit 为 0、负数、201、非数字、超出整数范围时返回 400 PLATFORM_WEB_REQUEST_INVALID，不静默截断")
    void limitOutsideOneToTwoHundredIsRefused(Endpoint endpoint, String limit) throws Exception {
        controlRequestSucceeds(endpoint);

        assertRefused(call(endpoint, "limit", limit), REQUEST_INVALID);
    }

    @ParameterizedTest(name = "{0} limit=1 and limit=200 -> 200")
    @MethodSource("endpoints")
    @DisplayName("K-3a A-3 limit 的边界值 1 和 200 被接受（特征测试）")
    void limitBoundariesOneAndTwoHundredAreAccepted(Endpoint endpoint) throws Exception {
        assertThat(call(endpoint, "limit", "1").getStatus()).isEqualTo(200);
        assertThat(call(endpoint, "limit", "200").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("K-3a A-3 不带 limit 时一页 50 行，limit=200 一页取完 51 行，limit=1 一页 1 行（特征测试）")
    void defaultPageIsFiftyRows() throws Exception {
        MockHttpServletResponse byDefault = call(LEGAL_ENTITIES_LIST);
        MockHttpServletResponse widest = call(LEGAL_ENTITIES_LIST, "limit", "200");
        MockHttpServletResponse single = call(LEGAL_ENTITIES_LIST, "limit", "1");

        assertThat((List<?>) JsonPath.read(byDefault.getContentAsString(), "$.items")).hasSize(50);
        assertThat((String) JsonPath.read(byDefault.getContentAsString(), "$.nextCursor")).isNotBlank();
        assertThat((List<?>) JsonPath.read(widest.getContentAsString(), "$.items")).hasSize(SEEDED_LEGAL_ENTITIES);
        assertThat((Object) JsonPath.read(widest.getContentAsString(), "$.nextCursor")).isNull();
        assertThat((List<?>) JsonPath.read(single.getContentAsString(), "$.items")).hasSize(1);
    }

    @Test
    @DisplayName("K-3a A-3 逐页取完法人列表：无重复、无遗漏，末页 nextCursor 为 null（特征测试）")
    void walkingTheLegalEntitiesCoversEveryRowOnce() throws Exception {
        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletResponse page = cursor == null ? call(LEGAL_ENTITIES_LIST, "limit", "7") : call(LEGAL_ENTITIES_LIST, "limit", "7", "cursor", cursor);
            assertThat(page.getStatus()).as(page.getContentAsString()).isEqualTo(200);
            seen.addAll(JsonPath.read(page.getContentAsString(), "$.items[*].legalEntityId"));
            cursor = JsonPath.read(page.getContentAsString(), "$.nextCursor");
            pages++;
            assertThat(pages).as("paging must terminate").isLessThanOrEqualTo(20);
        } while (cursor != null);

        assertThat(pages).isEqualTo(8);
        assertThat(seen).hasSize(SEEDED_LEGAL_ENTITIES).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seen)).isEqualTo(legalEntities.stream().map(UUID::toString).collect(Collectors.toSet()));
    }

    // ---- unknown query parameters: ruling item 3, A-4 ----

    @ParameterizedTest(name = "{0} ?bogusFilter -> 400")
    @MethodSource("endpoints")
    @DisplayName("K-3a 裁定第 3 条 A-4 集合接口收到未登记的查询参数返回 400 PLATFORM_WEB_REQUEST_INVALID 并指明参数名")
    void unregisteredQueryParameterIsRefusedByName(Endpoint endpoint) throws Exception {
        controlRequestSucceeds(endpoint);

        MockHttpServletResponse refused = call(endpoint, "bogusFilter", "1");

        assertRefused(refused, REQUEST_INVALID);
        assertThat(refused.getContentAsString()).as("the response names the parameter").contains("bogusFilter");
    }

    @ParameterizedTest(name = "{0} ?limit=10&bogusFilter -> 400")
    @MethodSource("endpoints")
    @DisplayName("K-3a 裁定第 3 条 已登记参数与未登记参数同时出现时仍返回 400，且点名的是未登记的那个")
    void unregisteredParameterNextToRegisteredOnesIsStillRefused(Endpoint endpoint) throws Exception {
        MockHttpServletResponse refused = call(endpoint, "limit", "10", "bogusFilter", "1");

        assertRefused(refused, REQUEST_INVALID);
        assertThat(refused.getContentAsString()).contains("bogusFilter");
    }

    @Test
    @DisplayName("K-3a 裁定第 3 条 所有 /api 的 GET 生效：单个资源读取带未登记参数也返回 400")
    void unregisteredParameterOnASingleResourceReadIsRefused() throws Exception {
        String one = LEGAL_ENTITIES + "/" + legalEntities.getFirst();
        MockHttpServletResponse found = mvc.perform(get(one).with(LEGAL_ENTITY_MAINTAINER)).andReturn().getResponse();
        assertThat(found.getStatus()).as("control read").isEqualTo(200);

        MockHttpServletResponse refused = mvc.perform(get(one).param("bogusFilter", "1").with(LEGAL_ENTITY_MAINTAINER)).andReturn().getResponse();

        assertRefused(refused, REQUEST_INVALID);
        assertThat(refused.getContentAsString()).contains("bogusFilter");
    }

    @Test
    @DisplayName("K-3a 裁定第 3 条 springdoc 的 /v3/api-docs 不受未登记参数拦截（特征测试）")
    void apiDocsIgnoresTheUnknownParameterRule() throws Exception {
        MockHttpServletResponse docs = mvc.perform(get("/v3/api-docs").param("bogusFilter", "1").with(person(1))).andReturn().getResponse();

        assertThat(docs.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("K-3a 裁定第 3 条 actuator 与非 /api 路径不受未登记参数拦截（特征测试）")
    void actuatorAndNonApiPathsIgnoreTheUnknownParameterRule() throws Exception {
        for (String path : new String[] {"/actuator/health", "/not-an-api-path"}) {
            MockHttpServletResponse response = mvc.perform(get(path).param("bogusFilter", "1")).andReturn().getResponse();

            assertThat(response.getContentAsString()).as("GET %s", path).doesNotContain(REQUEST_INVALID);
        }
    }

    // ---- cursor: ruling item 1, A-3 ----

    @ParameterizedTest(name = "{0} cursor of another list -> 400")
    @MethodSource("endpoints")
    @DisplayName("K-3a 裁定第 1 条 取自法人列表的游标用在别的列表上返回 400 PLATFORM_WEB_CURSOR_INVALID（特征测试）")
    void cursorOfAnotherListIsRefused(Endpoint endpoint) throws Exception {
        if (endpoint == LEGAL_ENTITIES_LIST) {
            return;
        }
        String foreign = firstPageCursorOfLegalEntities();
        controlRequestSucceeds(endpoint);

        assertRefused(call(endpoint, "cursor", foreign), CURSOR_INVALID);
    }

    @ParameterizedTest(name = "{0} cursor not a cursor -> 400")
    @MethodSource("endpoints")
    @DisplayName("K-3a 裁定第 1 条 非法编码和编码合法但内容不是游标的字符串返回 400 PLATFORM_WEB_CURSOR_INVALID（特征测试）")
    void cursorThatIsNotOneThisServerIssuedIsRefused(Endpoint endpoint) throws Exception {
        controlRequestSucceeds(endpoint);
        String wellEncodedNonsense = Base64.getUrlEncoder().withoutPadding().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));

        assertRefused(call(endpoint, "cursor", "***not base64***"), CURSOR_INVALID);
        assertRefused(call(endpoint, "cursor", wellEncodedNonsense), CURSOR_INVALID);
    }

    @ValueSource(strings = {"9", "Z", "~"})
    @ParameterizedTest(name = "first byte replaced by {0} -> 400")
    @DisplayName("K-3a 裁定第 1 条 把真实游标的第一个字节改掉（版本号或列表名被破坏）返回 400 PLATFORM_WEB_CURSOR_INVALID")
    void cursorWithAlteredFirstByteIsRefused(String replacement) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(firstPageCursorOfLegalEntities());
        bytes[0] = (byte) replacement.charAt(0);
        String altered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertRefused(call(LEGAL_ENTITIES_LIST, "cursor", altered), CURSOR_INVALID);
    }

    @Test
    @DisplayName("K-3a 裁定第 1 条 真实游标的键被换成不是 UUID 的字符时（键类型错）返回 400 PLATFORM_WEB_CURSOR_INVALID，不是 500")
    void cursorWhoseKeyHasTheWrongTypeIsRefused() throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(firstPageCursorOfLegalEntities());
        byte[] notHex = "notauuid-key".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(notHex, 0, bytes, bytes.length - notHex.length, notHex.length);
        String altered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertRefused(call(LEGAL_ENTITIES_LIST, "cursor", altered), CURSOR_INVALID);
    }

    @Test
    @DisplayName("K-3a 裁定第 1 条 游标不签名：服务端签发的游标原样重放，翻页继续且结果一致（特征测试）")
    void issuedCursorCanBeReplayedAsIs() throws Exception {
        String cursor = firstPageCursorOfLegalEntities();

        MockHttpServletResponse first = call(LEGAL_ENTITIES_LIST, "limit", "1", "cursor", cursor);
        MockHttpServletResponse again = call(LEGAL_ENTITIES_LIST, "limit", "1", "cursor", cursor);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(again.getContentAsString()).isEqualTo(first.getContentAsString());
    }

    // ---- OpenAPI: K-3a "OpenAPI limit 的 minimum / maximum", A-3 ----

    @Test
    @DisplayName("K-3a A-3 运行中的 /v3/api-docs 里所有 GET 的 limit 参数都声明 minimum 1、maximum 200、default 50")
    void everyLimitParameterInTheOpenApiDocumentDeclaresTheBounds() throws Exception {
        MockHttpServletResponse docs = mvc.perform(get("/v3/api-docs").with(person(1))).andReturn().getResponse();
        assertThat(docs.getStatus()).isEqualTo(200);
        Map<String, Map<String, Object>> paths = JsonPath.read(docs.getContentAsString(), "$.paths");

        Set<String> checked = new HashSet<>();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> path : paths.entrySet()) {
            Object operation = path.getValue().get("get");
            if (!(operation instanceof Map<?, ?> get) || !(get.get("parameters") instanceof List<?> parameters)) {
                continue;
            }
            for (Object parameter : parameters) {
                if (parameter instanceof Map<?, ?> p && "limit".equals(p.get("name"))) {
                    checked.add(path.getKey());
                    Map<?, ?> schema = p.get("schema") instanceof Map<?, ?> m ? m : Map.of();
                    if (!sameNumber(schema.get("minimum"), 1) || !sameNumber(schema.get("maximum"), 200) || !sameNumber(schema.get("default"), 50)) {
                        wrong.add(path.getKey() + " " + schema);
                    }
                }
            }
        }

        assertThat(checked).as("GET paths that declare a limit parameter").contains(LEGAL_ENTITIES, AUDIT_ENTRIES, SOURCE_REFERENCES, WORK_CALENDARS);
        assertThat(wrong).as("limit parameters without minimum 1 / maximum 200 / default 50").isEmpty();
    }

    private static boolean sameNumber(Object actual, int expected) {
        return actual instanceof Number n && n.intValue() == expected;
    }
}
