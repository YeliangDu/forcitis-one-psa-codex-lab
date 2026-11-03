package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;

/**
 * R-1b P3b on the written contracts (ADR-0033 section 1 and section 6 item 2, G-A1): employee, org unit and position ids are own UUIDs,
 * so every one of them in the OpenAPI document (property, path parameter, the assigneeId of transfer and reassign), in the event
 * catalogue and in the generated front-end validators is a string of format uuid, never a number. Written like UserIdContractTest (P2).
 */
class EmployeeIdContractTest {

    static final Path VALIDATORS = Path.of("../frontend/packages/api/src/generated/validators.ts");

    /** Schema.property: the employee and org unit ids in response and request bodies (card R-1b P3b section 3.2 item 12). */
    static final List<String> OWN_ID_PROPERTIES = List.of("OrgUnitResponse.orgUnitId", "OrgUnitResponse.headEmployeeId",
            "OrgUnitResponse.parentOrgUnitId", "OrgUnitResponse.regionalReceivablesManagerId", "OrgUnitDetailResponse.ancestorOrgUnitIds",
            "OrgUnitDetailResponse.orgUnitAndBelowIds", "CurrentUserResponse.orgUnitId", "LegalEntityAttributionResponse.subjectId",
            "ReassignTaskRequest.assigneeId", "TransferTaskRequest.assigneeId");

    /** path, parameter: the 7 path templates and 9 operations that take an employee or org unit id (RULING-P3B-D7). */
    static final List<String[]> OWN_ID_PARAMETERS = List.of(
            new String[] {"/api/v1/organization/employee-legal-entities/{employeeId}", "employeeId"},
            new String[] {"/api/v1/organization/employee-legal-entities/{employeeId}:correct", "employeeId"},
            new String[] {"/api/v1/organization/employee-legal-entities/{employeeId}:end", "employeeId"},
            new String[] {"/api/v1/organization/org-unit-legal-entities/{orgUnitId}", "orgUnitId"},
            new String[] {"/api/v1/organization/org-unit-legal-entities/{orgUnitId}:correct", "orgUnitId"},
            new String[] {"/api/v1/organization/org-unit-legal-entities/{orgUnitId}:end", "orgUnitId"},
            new String[] {"/api/v1/organization/org-units/{orgUnitId}", "orgUnitId"});

    static final int OWN_ID_OPERATIONS = 9;

    /**
     * The ids OpenApiExportTest still lets through as "other", by full name: InboxItemResponse.taskId (RULING-P4-D6, goes with ADR-0039
     * item 6) and, since RULING-P5-1 (card R-1b P5 D-13), SyncDiffResponse.subjectKey (a "type:id" text key, goes with split a).
     */
    static final Set<String> OTHER_ID_NAMES_AFTER_P4 = Set.of("InboxItemResponse.taskId", "SyncDiffResponse.subjectKey");

    /** The sync difference goes with the old sync (#934): once its response type is gone, so is its exception, and only the inbox one is left. */
    static Set<String> expectedOtherIdNames() {
        try {
            Class.forName("com.forcitis.one.organization.workforce.web.SyncDiffResponse");
            return OTHER_ID_NAMES_AFTER_P4;
        } catch (ClassNotFoundException gone) {
            return Set.of("InboxItemResponse.taskId");
        }
    }

    static final Set<String> OWN_ID_TYPES = Set.of("EmployeeId", "OrgUnitId", "PositionId");

    /** RULING-P3B-D5: the events of a legal entity attribution name their subject in two flat fields. */
    static final List<String> ATTRIBUTION_EVENTS = List.of("LegalEntityAttributed", "LegalEntityAttributionEnded", "LegalEntityAttributionCorrected");
    static final String SUBJECT_KIND = "kind";
    static final String SUBJECT_ID = "subjectId";

    /** Fields of the generated validators that hold an employee or org unit id. */
    static final List<String> VALIDATOR_FIELDS = List.of("assigneeId", "orgUnitId", "headEmployeeId", "parentOrgUnitId",
            "regionalReceivablesManagerId", "subjectId", "employeeId");

    static JsonNode schemaOf(JsonNode field) {
        return "array".equals(field.path("type").asString(null)) ? field.path("items") : field;
    }

    @Test
    @DisplayName("R-1b P3b G-A1 / ADR-0033 第 6 项第 2 条：OpenAPI 中员工、部门 ID 的属性（部门、上级、负责人、区域应收负责人、祖先与本级及以下、当前用户部门、法人归属对象）与改派、转交的 assigneeId 为 type: string, format: uuid")
    void ownIdPropertiesAreStringUuid() throws IOException {
        JsonNode schemas = UserIdContractTest.openApi().path("components").path("schemas");
        List<String> wrong = new ArrayList<>();
        for (String property : OWN_ID_PROPERTIES) {
            String[] parts = property.split("\\.");
            JsonNode field = schemas.path(parts[0]).path("properties").path(parts[1]);
            if (field.isMissingNode() || !UserIdContractTest.isStringUuid(schemaOf(field))) {
                wrong.add(property + " = " + (field.isMissingNode() ? "missing" : field.toString()));
            }
        }

        assertThat(wrong).as("employee and org unit id properties that are not string / uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b G-A1 / ADR-0033 第 6 项第 2 条 / RULING-P3B-D7：OpenAPI 中 7 个路径模板、9 个操作的 {employeeId}、{orgUnitId} 为 type: string, format: uuid")
    void ownIdPathParametersAreStringUuid() throws IOException {
        JsonNode paths = UserIdContractTest.openApi().path("paths");
        List<String> wrong = new ArrayList<>();
        int operations = 0;
        for (String[] expected : OWN_ID_PARAMETERS) {
            boolean found = false;
            for (JsonNode operation : paths.path(expected[0])) {
                for (JsonNode parameter : operation.path("parameters")) {
                    if (expected[1].equals(parameter.path("name").asString(null))) {
                        found = true;
                        operations++;
                        if (!UserIdContractTest.isStringUuid(parameter.path("schema"))) {
                            wrong.add(expected[0] + " " + expected[1] + " = " + parameter.path("schema"));
                        }
                    }
                }
            }
            if (!found) {
                wrong.add(expected[0] + " " + expected[1] + " = missing");
            }
        }

        assertThat(wrong).as("employee and org unit path parameters that are not string / uuid").isEmpty();
        assertThat(operations).as("operations that take an employee or org unit id in the path").isEqualTo(OWN_ID_OPERATIONS);
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 6 项第 2 条：OpenAPI 中任何名为 *employeeId / *orgUnitId / positionId / assigneeId 的属性或参数都不是整数")
    void noEmployeeOrOrgUnitIdIsANumber() throws IOException {
        Pattern ownIdName = Pattern.compile("(?i).*(employeeid|orgunitid|orgunitids|orgunitandbelowids|positionid|assigneeid|managerid)$");
        List<String> numbers = new ArrayList<>();
        JsonNode root = UserIdContractTest.openApi();
        root.path("components").path("schemas").properties().forEach(schema -> schema.getValue().path("properties").properties().forEach(property -> {
            if (ownIdName.matcher(property.getKey()).matches() && isNumber(schemaOf(property.getValue()))) {
                numbers.add(schema.getKey() + "." + property.getKey());
            }
        }));
        root.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
            for (JsonNode parameter : operation.getValue().path("parameters")) {
                if (ownIdName.matcher(parameter.path("name").asString("")).matches() && isNumber(parameter.path("schema"))) {
                    numbers.add(operation.getKey() + " " + path.getKey() + " " + parameter.path("name").asString(""));
                }
            }
        }));

        assertThat(numbers).as("employee, org unit and position ids that the contract still declares as numbers").isEmpty();
    }

    private static boolean isNumber(JsonNode schema) {
        String type = schema.path("type").toString();
        return type.contains("integer") || type.contains("number");
    }

    @Test
    @DisplayName("RULING-P4-6 / RULING-P4-D6 / RULING-P5-1：OpenApiExportTest.OTHER_ID_NAMES 只按全名放行 InboxItemResponse.taskId 与 SyncDiffResponse.subjectKey")
    @SuppressWarnings("unchecked")
    void otherIdNamesKeepOnlyTheTwoRuledOnes() throws ReflectiveOperationException {
        Field field = Class.forName("com.forcitis.one.OpenApiExportTest").getDeclaredField("OTHER_ID_NAMES");
        field.setAccessible(true);

        assertThat((Set<String>) field.get(null)).containsExactlyInAnyOrderElementsOf(expectedOtherIdNames());
    }

    @Test
    @DisplayName("R-1b P3b ADR-0033 第 6 项第 2 条 / 第 7 项：事件目录中 x-type 为 EmployeeId、OrgUnitId、PositionId 的字段声明 format: uuid")
    void eventCatalogueDeclaresOwnIdsAsUuid() throws IOException {
        List<String> found = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, Object> file : eventFiles().entrySet()) {
            collect(file.getKey(), file.getValue(), found, wrong);
        }

        assertThat(found).as("EmployeeId / OrgUnitId / PositionId fields in the event catalogue").isNotEmpty();
        assertThat(wrong).as("EmployeeId / OrgUnitId / PositionId fields without format: uuid").isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b DA-5 / RULING-P3B-D5 / RULING-P3B-5：法人归属三个事件的 subject 扁平为 kind 与 subjectId（format: uuid），不再有 AttributionSubject 字段")
    @SuppressWarnings("unchecked")
    void attributionEventsNameTheirSubjectInTwoFields() throws IOException {
        Map<String, Map<String, Object>> payloads = new java.util.HashMap<>();
        for (Object document : eventFiles().values()) {
            findMessages(document, payloads);
        }
        List<String> wrong = new ArrayList<>();
        for (String event : ATTRIBUTION_EVENTS) {
            Map<String, Object> payload = payloads.get(event);
            if (payload == null) {
                wrong.add(event + ": missing");
                continue;
            }
            Map<String, Object> properties = (Map<String, Object>) payload.getOrDefault("properties", Map.of());
            if (properties.containsKey("subject")) {
                wrong.add(event + ": still has subject");
            }
            if (!(properties.get(SUBJECT_KIND) instanceof Map<?, ?>)) {
                wrong.add(event + ": no " + SUBJECT_KIND);
            }
            if (!(properties.get(SUBJECT_ID) instanceof Map<?, ?> id) || !"uuid".equals(id.get("format"))) {
                wrong.add(event + ": " + SUBJECT_ID + " missing or not format: uuid");
            }
        }

        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("R-1b P3b RULING-P3B-5：法人归属接口沿用 kind + subjectId，不改名为 subjectKind")
    void theAttributionResponseKeepsItsFieldNames() throws IOException {
        JsonNode properties = UserIdContractTest.openApi().path("components").path("schemas").path("LegalEntityAttributionResponse").path("properties");

        assertThat(properties.has(SUBJECT_KIND)).as("LegalEntityAttributionResponse.%s", SUBJECT_KIND).isTrue();
        assertThat(properties.has(SUBJECT_ID)).as("LegalEntityAttributionResponse.%s", SUBJECT_ID).isTrue();
        assertThat(properties.has("subjectKind")).as("no subjectKind field").isFalse();
    }

    @Test
    @DisplayName("R-1b P3b G-A1：生成的前端校验器中 assigneeId 与员工、部门 ID 字段不是数字")
    void generatedValidatorsTakeTheIdsAsStrings() throws IOException {
        String validators = Files.readString(VALIDATORS);
        List<String> numbers = new ArrayList<>();
        for (String name : VALIDATOR_FIELDS) {
            Matcher declaration = Pattern.compile("(?m)^\\s*" + name + ": (.*)$").matcher(validators);
            while (declaration.find()) {
                if (declaration.group(1).contains("z.number")) {
                    numbers.add(name + ": " + declaration.group(1));
                }
            }
        }

        assertThat(validators).as("the validators declare an assigneeId").contains("assigneeId:");
        assertThat(numbers).as("generated validators that still take an employee or org unit id as a number").isEmpty();
    }

    static Map<String, Object> eventFiles() throws IOException {
        Map<String, Object> documents = new java.util.TreeMap<>();
        try (Stream<Path> files = Files.list(UserIdContractTest.EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml") && !f.getFileName().toString().equals("asyncapi.yaml")).sorted().toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    documents.put(file.getFileName().toString(), new Yaml().load(in));
                }
            }
        }
        try (InputStream in = Files.newInputStream(UserIdContractTest.EVENTS.resolve("asyncapi.yaml"))) {
            documents.put("asyncapi.yaml", new Yaml().load(in));
        }
        return documents;
    }

    @SuppressWarnings("unchecked")
    private static void collect(String where, Object node, List<String> found, List<String> wrong) {
        if (node instanceof Map<?, ?> map) {
            if (OWN_ID_TYPES.contains(String.valueOf(map.get("x-type")))) {
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
    private static void findMessages(Object node, Map<String, Map<String, Object>> payloads) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("name") instanceof String name && ATTRIBUTION_EVENTS.contains(name) && map.get("payload") instanceof Map<?, ?> payload) {
                payloads.putIfAbsent(name, (Map<String, Object>) payload);
            }
            map.values().forEach(value -> findMessages(value, payloads));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> findMessages(item, payloads));
        }
    }
}
