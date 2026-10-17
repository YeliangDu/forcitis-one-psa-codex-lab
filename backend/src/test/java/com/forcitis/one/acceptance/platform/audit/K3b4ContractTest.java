package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.ListLimits;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * K-3b-4 on the written contracts (card sections 5 to 7), read from the files that the build checks in: the generated OpenAPI document,
 * the audit configuration, the paging gap list, the audit error enumeration and the front-end message table. No database.
 */
class K3b4ContractTest {

    static final Path OPENAPI = Path.of("../frontend/packages/api/openapi.json");
    static final Path FRONTEND_SOURCES = Path.of("../frontend/packages");
    static final Path ERROR_MESSAGES = Path.of("../frontend/packages/core/src/error-messages.ts");
    static final Path PROPERTIES = Path.of("src/main/resources/config/platform_audit.properties");
    static final Path GAPS = Path.of("architecture/list-paging-gaps.txt");
    static final String LIST = "/api/v1/platform/audit-entries";
    static final String EXPORT = "/api/v1/platform/audit-entries:export";
    static final String OWN = "/api/v1/platform/own-login-records";
    static final String RETIRED_CODE = "PLATFORM_AUDIT_QUERY_INVALID";
    static final Set<String> FILTERS = Set.of("actorId", "onBehalfOfId", "action", "actionPrefix", "sourceAddress", "outcome", "subjectKind", "subjectId", "from", "to");

    private static JsonNode openApi() throws IOException {
        return JsonMapper.builder().build().readTree(Files.readString(OPENAPI));
    }

    private static JsonNode getOperation(String path) throws IOException {
        JsonNode operation = openApi().path("paths").path(path).path("get");
        assertThat(operation.isMissingNode()).as("GET %s in openapi.json", path).isFalse();
        return operation;
    }

    /** Parameter name to its schema as text, so that type, format, enumeration and bounds are all compared. */
    private static Map<String, String> parameters(String path) throws IOException {
        Map<String, String> found = new TreeMap<>();
        for (JsonNode parameter : getOperation(path).path("parameters")) {
            found.put(parameter.path("name").asString(), parameter.path("schema").toString());
        }
        return found;
    }

    @Test
    @DisplayName("K-3b-4 导出与列表的查询参数名集合相同（OpenAPI 对比，列表另有 limit、cursor）")
    void exportAndListDeclareTheSameFilterParameters() throws IOException {
        Map<String, String> list = parameters(LIST);
        Map<String, String> export = parameters(EXPORT);

        assertThat(export.keySet()).containsExactlyInAnyOrderElementsOf(FILTERS);
        assertThat(list.keySet()).containsExactlyInAnyOrderElementsOf(concat(FILTERS, List.of("limit", "cursor")));
        for (String name : FILTERS) {
            assertThat(export.get(name)).as("schema of %s on the export and on the list", name).isEqualTo(list.get(name));
        }
        assertThat(parameters(OWN).keySet()).as("own login records take only the paging parameters").containsExactlyInAnyOrder("limit", "cursor");
    }

    @Test
    @DisplayName("K-3b-4 列表与导出的 OpenAPI 描述写明固定顺序 created_at desc, id desc，且不声明 sort")
    void bothOperationsDescribeTheFixedOrder() throws IOException {
        for (String path : List.of(LIST, EXPORT)) {
            JsonNode operation = getOperation(path);
            String text = operation.path("summary").asString("") + " " + operation.path("description").asString("");

            assertThat(text).as("description of GET %s", path).contains("created_at desc, id desc");
            assertThat(parameters(path)).doesNotContainKey("sort");
        }
    }

    @Test
    @DisplayName("K-3b-4 配置的导出上限不超过 ListLimits.EXPORT_MAX（读 platform_audit.properties），并断言缺口清单不再有这三行")
    void theConfiguredCeilingIsTheSharedExportMaxAndTheGapsAreClosed() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(PROPERTIES)));

        assertThat(properties.getProperty("forcitis.platform.audit.export.max-rows")).as("the key stays").isNotNull();
        assertThat(Integer.parseInt(properties.getProperty("forcitis.platform.audit.export.max-rows").trim())).isEqualTo(10_000).isLessThanOrEqualTo(ListLimits.EXPORT_MAX);
        List<String> entries = Files.readAllLines(GAPS).stream().filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        assertThat(entries).noneMatch(line -> line.contains("K-3b-4"));
        assertThat(entries).noneMatch(line -> line.startsWith("GET " + OWN));
        assertThat(entries).noneMatch(line -> line.contains("AuditQuery.MAX_LIMIT"));
        assertThat(entries).noneMatch(line -> line.contains("forcitis.platform.audit.export.max-rows"));
    }

    @Test
    @DisplayName("K-3b-4 错误码枚举与前端错误文案表都不再含 PLATFORM_AUDIT_QUERY_INVALID")
    void theRetiredErrorCodeIsGoneEverywhere() throws Exception {
        Class<?> errors = Class.forName("com.forcitis.one.platform.audit.api.AuditError");
        List<String> constants = Arrays.stream(errors.getEnumConstants()).map(Object::toString).toList();

        assertThat(constants).contains("PLATFORM_AUDIT_EXPORT_TOO_LARGE").doesNotContain(RETIRED_CODE);
        assertThat(Files.readString(ERROR_MESSAGES)).doesNotContain(RETIRED_CODE);
        assertThat(Files.readString(OPENAPI)).doesNotContain(RETIRED_CODE);
        assertThat(Files.readString(FRONTEND_SOURCES.resolve("api/src/generated/schema.ts"))).doesNotContain(RETIRED_CODE);
        assertThat(Files.readString(FRONTEND_SOURCES.resolve("api/src/generated/validators.ts"))).doesNotContain(RETIRED_CODE);
    }

    @Test
    @DisplayName("K-3b-4 本人登录记录的 OpenAPI 响应是带 items 与 nextCursor 的对象，不再是数组")
    void ownRecordsResponseIsAPageObject() throws IOException {
        JsonNode schema = getOperation(OWN).path("responses").path("200").path("content").path("*/*").path("schema");
        if (schema.isMissingNode()) {
            schema = getOperation(OWN).path("responses").path("200").path("content").path("application/json").path("schema");
        }
        String ref = schema.path("$ref").asString("");
        JsonNode resolved = ref.isEmpty() ? schema : openApiSchema(ref.substring(ref.lastIndexOf('/') + 1));

        assertThat(resolved.path("type").asString("object")).isNotEqualTo("array");
        assertThat(resolved.path("properties").propertyNames()).contains("items", "nextCursor");
    }

    private static JsonNode openApiSchema(String name) throws IOException {
        return openApi().path("components").path("schemas").path(name);
    }

    private static List<String> concat(Set<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
