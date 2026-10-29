package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * R-1b P4 on the written contracts (ADR-0033 section 6 item 2, section 8 item 3, G-A1; card 3.2 item 10 and acceptance item 9): the
 * ids of approval tasks, process instances and documents are "type: string, format: uuid" in the OpenAPI document and the event
 * catalogue, and the generated front-end validators no longer treat them as numbers. The one id that stays a plain string is
 * InboxItemResponse.taskId, which still carries the old system's task key (decision D-6, until the inbox contract is neutralised).
 */
class ApprovalIdContractTest {

    static final Path OPENAPI = Path.of("../frontend/packages/api/openapi.json");
    static final Path VALIDATORS = Path.of("../frontend/packages/api/src/generated/validators.ts");
    static final Path EVENTS = Path.of("../docs/architecture/events");
    static final Path OPENAPI_GUARD = Path.of("src/test/java/com/forcitis/one/OpenApiExportTest.java");

    /** The one id left as it is (RULING-P4-D6), by full name. */
    static final String D6_EXCEPTION = "InboxItemResponse.taskId";

    /** RULING-P5-1 (card R-1b P5 D-13): the "type:id" text key of a sync difference, until split a. */
    static final String P5_EXCEPTION = "SyncDiffResponse.subjectKey";

    static final List<String> TASK_PATHS = List.of("/api/v1/platform/approval-tasks/{taskId}", "/api/v1/platform/approval-tasks/{taskId}:claim",
            "/api/v1/platform/approval-tasks/{taskId}:decide", "/api/v1/platform/approval-tasks/{taskId}:reassign",
            "/api/v1/platform/approval-tasks/{taskId}:release", "/api/v1/platform/approval-tasks/{taskId}:return",
            "/api/v1/platform/approval-tasks/{taskId}:transfer");

    /** Schema.property: the ids of approval tasks, instances and documents in the response bodies. */
    static final List<String> UUID_PROPERTIES = List.of("DecisionResponse.instanceId", "HandledTaskResponse.taskId", "HandledTaskResponse.instanceId",
            "HandledTaskResponse.documentId", "HistoryEntry.taskId", "TaskDetailResponse.taskId", "TaskDetailResponse.instanceId",
            "TaskDetailResponse.documentId", "TaskOverviewResponse.taskId", "TaskOverviewResponse.documentId", "InboxItemResponse.documentId");

    static final List<String> EVENT_ID_TYPES = List.of("ApprovalTaskId", "ApprovalInstanceId", "DocumentId");

    static boolean isStringUuid(JsonNode schema) {
        JsonNode type = schema.path("type");
        boolean string = type.isArray() ? type.toString().contains("\"string\"") : "string".equals(type.asString(null));
        return string && "uuid".equals(schema.path("format").asString(null));
    }

    static JsonNode openApi() throws IOException {
        return JsonMapper.builder().build().readTree(Files.readString(OPENAPI));
    }

    @Test
    @DisplayName("R-1b P4 G-A1 / ADR-0033 第 6 项第 2 条：OpenAPI 中 {taskId} 路径参数（7 个操作）为 type: string, format: uuid")
    void taskIdPathParametersAreStringUuid() throws IOException {
        JsonNode paths = openApi().path("paths");
        List<String> wrong = new ArrayList<>();
        for (String path : TASK_PATHS) {
            boolean found = false;
            for (JsonNode operation : paths.path(path)) {
                for (JsonNode parameter : operation.path("parameters")) {
                    if ("taskId".equals(parameter.path("name").asString(null))) {
                        found = true;
                        if (!isStringUuid(parameter.path("schema"))) {
                            wrong.add(path + " = " + parameter.path("schema"));
                        }
                    }
                }
            }
            if (!found) {
                wrong.add(path + " = no taskId parameter");
            }
        }

        assertThat(wrong).as("taskId parameters that are not string / uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P4 G-A1 / ADR-0033 第 8 项第 3 条：OpenAPI 中任务、实例、单据 ID 的属性（决定、已办、历史、详情、总览、收件箱单据）为 type: string, format: uuid")
    void taskInstanceAndDocumentIdPropertiesAreStringUuid() throws IOException {
        JsonNode schemas = openApi().path("components").path("schemas");
        List<String> wrong = new ArrayList<>();
        for (String property : UUID_PROPERTIES) {
            String[] parts = property.split("\\.");
            JsonNode field = schemas.path(parts[0]).path("properties").path(parts[1]);
            if (field.isMissingNode() || !isStringUuid(field)) {
                wrong.add(property + " = " + (field.isMissingNode() ? "missing" : field.toString()));
            }
        }

        assertThat(wrong).as("approval id properties that are not string / uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P4 D-6：OpenAPI 中名为 taskId、instanceId、documentId 的属性与参数，除 InboxItemResponse.taskId 外没有一个不是 string / uuid")
    void noOtherApprovalIdIsLeftInAnotherShape() throws IOException {
        JsonNode document = openApi();
        List<String> wrong = new ArrayList<>();
        document.path("components").path("schemas").properties().forEach(schema -> schema.getValue().path("properties").properties().forEach(property -> {
            String name = property.getKey();
            String full = schema.getKey() + "." + name;
            if (List.of("taskId", "instanceId", "documentId").contains(name) && !full.equals(D6_EXCEPTION) && !isStringUuid(property.getValue())) {
                wrong.add(full + " = " + property.getValue());
            }
        }));
        document.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> operation.getValue().path("parameters").forEach(parameter -> {
            String name = parameter.path("name").asString("");
            if (List.of("taskId", "instanceId", "documentId").contains(name) && !path.getKey().contains("legacy-numbers") && !isStringUuid(parameter.path("schema"))) {
                wrong.add("parameter " + name + " " + path.getKey() + " = " + parameter.path("schema"));
            }
        })));

        assertThat(wrong).isEmpty();
        JsonNode exception = document.path("components").path("schemas").path("InboxItemResponse").path("properties").path("taskId");
        assertThat(exception.path("format").asString(null)).as("the old system's task key is not declared uuid").isNotEqualTo("uuid");
    }

    @Test
    @DisplayName("R-1b P4 D-6：守护 OpenApiExportTest 的 OTHER_ID_NAMES 收窄为只按全名放行 InboxItemResponse.taskId；taskId、instanceId、documentId 三个裸名不再放行")
    void theGuardOnlyLetsTheInboxTaskIdThrough() throws IOException {
        String source = Files.readString(OPENAPI_GUARD);
        Matcher declared = Pattern.compile("OTHER_ID_NAMES\\s*=\\s*[^;]*?Set\\.of\\(([^;]*?)\\);", Pattern.DOTALL).matcher(source);
        assertThat(declared.find()).as("OTHER_ID_NAMES is declared in OpenApiExportTest").isTrue();

        List<String> allowed = new ArrayList<>();
        Matcher literal = Pattern.compile("\"([^\"]*)\"").matcher(declared.group(1));
        while (literal.find()) {
            allowed.add(literal.group(1));
        }

        assertThat(allowed).as("names the guard lets through").doesNotContain("taskId", "instanceId", "documentId").allMatch(name -> name.equals(D6_EXCEPTION) || name.equals(P5_EXCEPTION));
        Matcher ownFields = Pattern.compile("OWN_ID_FIELDS\\s*=\\s*[^;]*?Set\\.of\\(([^;]*?)\\);", Pattern.DOTALL).matcher(source);
        assertThat(ownFields.find()).isTrue();
        for (String property : UUID_PROPERTIES) {
            assertThat(ownFields.group(1)).as("%s is an id of this system in OWN_ID_FIELDS", property).contains("\"" + property + "\"");
        }
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 6 项第 2 条 / 第 8 项第 3 条：事件目录中 x-type 为 ApprovalTaskId / ApprovalInstanceId / DocumentId 的字段声明 format: uuid，描述不再写「如 TIMESHEET:7」")
    @SuppressWarnings("unchecked")
    void eventCatalogueDeclaresTheApprovalIdsAsUuid() throws IOException {
        List<String> found = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    collect(file.getFileName().toString(), new Yaml().load(in), found, wrong);
                }
            }
        }

        assertThat(found).as("fields typed with the approval ids").isNotEmpty();
        assertThat(wrong).as("approval id fields without format: uuid or with a text-key description").isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static void collect(String where, Object node, List<String> found, List<String> wrong) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("x-type") instanceof String type && EVENT_ID_TYPES.contains(type)) {
                found.add(where);
                Object target = "array".equals(map.get("type")) && map.get("items") instanceof Map<?, ?> items ? items : map;
                Map<String, Object> schema = (Map<String, Object>) target;
                String description = String.valueOf(map.get("description"));
                if (!"uuid".equals(schema.get("format")) || description.matches(".*(TIMESHEET|QUOTE):\\d.*")) {
                    wrong.add(where + " (" + type + ")");
                }
            }
            ((Map<String, Object>) map).forEach((key, value) -> collect(where + "." + key, value, found, wrong));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collect(where, item, found, wrong));
        }
    }

    private static String schemaBlock(String validators, String schema) {
        Matcher start = Pattern.compile("export const " + schema + "Schema = z\\.object\\(\\{").matcher(validators);
        assertThat(start.find()).as("%sSchema in validators.ts", schema).isTrue();
        int end = validators.indexOf("\n});", start.end());
        return validators.substring(start.end(), end);
    }

    @Test
    @DisplayName("R-1b P4 G-A1：生成的 validators.ts 中任务、实例、单据 ID 字段是字符串校验、不是数字（RULING-P4-2：生成器不出 uuid 校验，不要求）")
    void generatedValidatorsTreatTheIdsAsStrings() throws IOException {
        String validators = Files.readString(VALIDATORS);
        List<String> wrong = new ArrayList<>();
        for (String property : UUID_PROPERTIES) {
            String[] parts = property.split("\\.");
            String block = schemaBlock(validators, parts[0]);
            Matcher line = Pattern.compile("(?m)^\\s*" + parts[1] + ":\\s*(.*),\\s*$").matcher(block);
            if (!line.find()) {
                wrong.add(property + " is missing");
            } else if (line.group(1).contains("number") || !line.group(1).startsWith("z.string(")) {
                wrong.add(property + " = " + line.group(1));
            }
        }

        assertThat(wrong).as("validators of the approval ids that are not strings").isEmpty();
    }
}
