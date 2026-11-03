package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * R-1b P2 on the written contracts (ADR-0033 section 6 item 2, NAMING_STANDARD 2.6 A-7, G-A1): every id of a user account in the OpenAPI
 * document, as a property, a path parameter or a query parameter, is "type: string, format: uuid"; so is every UserId field of the event
 * catalogue and the envelope's actorId. The employee ids stay numbers until P3b and are not listed here (the transfer and reassign
 * requests' assigneeId is an employee, ADR-0033 section 3 item 3).
 */
class UserIdContractTest {

    static final Path OPENAPI = Path.of("../frontend/packages/api/openapi.json");
    static final Path EVENTS = Path.of("../docs/architecture/events");

    /** Schema.property: the user ids in response and request bodies. */
    static final List<String> USER_ID_PROPERTIES = List.of("AuditEntryResponse.actorId", "AuditEntryResponse.onBehalfOfId",
            "AuthorizationRequestResponse.requestedBy", "AuthorizationRequestResponse.subjectUserId", "CurrentUserResponse.userId", "LoginResponse.userId",
            "UserAccountResponse.userId", "HandledTaskResponse.actorId", "HistoryEntry.actorId", "PendingTemplate.authorId", "PendingTemplate.confirmedBy",
            "PendingTemplate.financeConfirmedBy", "TaskDetailResponse.assignee", "TaskDetailResponse.candidates",
            "TaskOverviewResponse.assignee");

    /** path, parameter: the user ids in paths and query strings. */
    static final List<String[]> USER_ID_PARAMETERS = List.of(
            new String[] {"/api/v1/platform/user-accounts/{userId}", "userId"},
            new String[] {"/api/v1/platform/user-accounts/{userId}:replace-roles", "userId"},
            new String[] {"/api/v1/platform/user-accounts/{userId}:enable", "userId"},
            new String[] {"/api/v1/platform/user-accounts/{userId}:disable", "userId"},
            new String[] {"/api/v1/platform/user-accounts/{userId}:force-logout", "userId"},
            new String[] {"/api/v1/platform/audit-entries", "actorId"},
            new String[] {"/api/v1/platform/audit-entries", "onBehalfOfId"},
            new String[] {"/api/v1/platform/audit-entries:export", "actorId"},
            new String[] {"/api/v1/platform/audit-entries:export", "onBehalfOfId"});

    static boolean isStringUuid(JsonNode schema) {
        JsonNode type = schema.path("type");
        boolean string = type.isArray() ? type.toString().contains("\"string\"") : "string".equals(type.asString(null));
        return string && "uuid".equals(schema.path("format").asString(null));
    }

    static JsonNode openApi() throws IOException {
        return JsonMapper.builder().build().readTree(Files.readString(OPENAPI));
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 6 项第 2 条 / NAMING_STANDARD A-7：OpenAPI 中用户 ID 的属性（审计操作人、授权申请人与受益人、当前用户、账号、办理人与候选人、模板作者与确认人）为 type: string, format: uuid")
    void userIdPropertiesAreStringUuid() throws IOException {
        JsonNode schemas = openApi().path("components").path("schemas");
        List<String> wrong = new ArrayList<>();
        for (String property : USER_ID_PROPERTIES) {
            String[] parts = property.split("\\.");
            JsonNode field = schemas.path(parts[0]).path("properties").path(parts[1]);
            JsonNode schema = "array".equals(field.path("type").asString(null)) ? field.path("items") : field;
            if (field.isMissingNode() || !isStringUuid(schema)) {
                wrong.add(property + " = " + (field.isMissingNode() ? "missing" : schema.toString()));
            }
        }

        assertThat(wrong).as("user id properties that are not string / uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P2 G-A1 / ADR-0033 第 6 项第 2 条：OpenAPI 中路径参数 {userId}（5 条账号路径）与审计查询参数 actorId、onBehalfOfId 为 type: string, format: uuid")
    void userIdParametersAreStringUuid() throws IOException {
        JsonNode paths = openApi().path("paths");
        List<String> wrong = new ArrayList<>();
        for (String[] expected : USER_ID_PARAMETERS) {
            JsonNode path = paths.path(expected[0]);
            boolean found = false;
            for (JsonNode operation : path) {
                for (JsonNode parameter : operation.path("parameters")) {
                    if (expected[1].equals(parameter.path("name").asString(null))) {
                        found = true;
                        if (!isStringUuid(parameter.path("schema"))) {
                            wrong.add(expected[0] + " " + expected[1] + " = " + parameter.path("schema"));
                        }
                    }
                }
            }
            if (!found) {
                wrong.add(expected[0] + " " + expected[1] + " = missing");
            }
        }

        assertThat(wrong).as("user id parameters that are not string / uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 6 项第 2 条 / 第 7 项第 1 条：事件目录中 x-type 为 UserId 的字段与信封 actorId 声明 format: uuid")
    @SuppressWarnings("unchecked")
    void eventCatalogueDeclaresUserIdsAsUuid() throws IOException {
        List<String> userIdFields = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    collect(file.getFileName().toString(), new Yaml().load(in), userIdFields, wrong);
                }
            }
        }
        Map<String, Object> asyncapi;
        try (InputStream in = Files.newInputStream(EVENTS.resolve("asyncapi.yaml"))) {
            asyncapi = new Yaml().load(in);
        }
        List<Map<String, Object>> actorIds = new ArrayList<>();
        findProperty(asyncapi, "actorId", actorIds);

        assertThat(userIdFields).as("UserId fields in the event catalogue").isNotEmpty();
        assertThat(wrong).as("UserId fields without format: uuid").isEmpty();
        assertThat(actorIds).as("the envelope's actorId").isNotEmpty().allSatisfy(actorId -> assertThat(actorId.get("format")).isEqualTo("uuid"));
    }

    @SuppressWarnings("unchecked")
    private static void collect(String where, Object node, List<String> found, List<String> wrong) {
        if (node instanceof Map<?, ?> map) {
            if ("UserId".equals(map.get("x-type"))) {
                found.add(where);
                Object target = "array".equals(map.get("type")) && map.get("items") instanceof Map<?, ?> items ? items : map;
                if (!"uuid".equals(((Map<String, Object>) target).get("format"))) {
                    wrong.add(where);
                }
            }
            ((Map<String, Object>) map).forEach((key, value) -> collect(where + "." + key, value, found, wrong));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collect(where, item, found, wrong));
        }
    }

    @SuppressWarnings("unchecked")
    private static void findProperty(Object node, String name, List<Map<String, Object>> found) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("properties") instanceof Map<?, ?> properties && properties.get(name) instanceof Map<?, ?> property) {
                found.add((Map<String, Object>) property);
            }
            map.values().forEach(value -> findProperty(value, name, found));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> findProperty(item, name, found));
        }
    }
}
