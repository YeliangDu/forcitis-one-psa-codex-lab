package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * R-1b P5 on the written contracts (ADR-0033 section 6 item 2 and section 8 item 3, G-A1; card acceptance item 9): an audit entry names
 * its object by a kind (an enumeration) and an id (string, uuid) and there is no subjectKey anywhere in the audit contract; the ids that
 * end in "By" are uuids; the generated validators treat them as strings; the OpenAPI guard knows the new fields. Written like
 * ApprovalIdContractTest (P4).
 */
class AuditIdContractTest {

    static final Path VALIDATORS = Path.of("../frontend/packages/api/src/generated/validators.ts");
    static final String LIST = "/api/v1/platform/audit-entries";
    static final String EXPORT = "/api/v1/platform/audit-entries:export";
    static final List<String> BY_PROPERTIES = List.of("requestedBy", "confirmedBy", "financeConfirmedBy");
    // releasedBy left with SyncRunResponse, its only carrier, when #934 removed the old sync; no other schema has it.

    private static JsonNode schema(String name) throws IOException {
        return UserIdContractTest.openApi().path("components").path("schemas").path(name);
    }

    private static JsonNode resolved(JsonNode node) throws IOException {
        String ref = node.path("$ref").asString(null);
        if (ref == null) {
            return node;
        }
        return schema(ref.substring(ref.lastIndexOf('/') + 1));
    }

    private static JsonNode parameter(String path, String name) throws IOException {
        for (JsonNode operation : UserIdContractTest.openApi().path("paths").path(path)) {
            for (JsonNode parameter : operation.path("parameters")) {
                if (name.equals(parameter.path("name").asString(null))) {
                    return parameter;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("R-1b P5 G-A1 / ADR-0033 第 6 项第 2 条：AuditEntryResponse 有 subjectKind（枚举，取值为已定稿的 19 个类别）与 subjectId（string / uuid），无 subjectKey")
    void theAuditEntryNamesItsSubjectInTwoFields() throws IOException {
        JsonNode properties = schema("AuditEntryResponse").path("properties");

        assertThat(properties.has("subjectKey")).as("subjectKey is gone").isFalse();
        JsonNode kind = resolved(properties.path("subjectKind"));
        List<String> values = new ArrayList<>();
        kind.path("enum").forEach(value -> values.add(value.asString()));
        assertThat(values).containsExactlyInAnyOrderElementsOf(concat(P5Names.KINDS_WITH_OBJECT, P5Names.KINDS_WITHOUT_OBJECT));
        assertThat(UserIdContractTest.isStringUuid(properties.path("subjectId"))).as("subjectId = %s", properties.path("subjectId")).isTrue();
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    @Test
    @DisplayName("R-1b P5 G-A1：审计列表与导出的查询参数 subjectKind（枚举）+ subjectId（string / uuid），查询参数 subjectKey 已不存在；actorId 仍是 uuid")
    void theAuditQueriesFilterBySubjectKindAndId() throws IOException {
        for (String path : List.of(LIST, EXPORT)) {
            assertThat(parameter(path, "subjectKey")).as("subjectKey on " + path).isNull();
            JsonNode kind = parameter(path, "subjectKind");
            assertThat(kind).as("subjectKind on " + path).isNotNull();
            assertThat(resolved(kind.path("schema")).path("enum").size()).as("subjectKind is an enumeration on " + path).isGreaterThanOrEqualTo(P5Names.KINDS_WITH_OBJECT.size());
            JsonNode id = parameter(path, "subjectId");
            assertThat(id).as("subjectId on " + path).isNotNull();
            assertThat(UserIdContractTest.isStringUuid(id.path("schema"))).as("subjectId on %s = %s", path, id.path("schema")).isTrue();
            assertThat(UserIdContractTest.isStringUuid(parameter(path, "actorId").path("schema"))).isTrue();
            assertThat(parameter(path, "onBehalfOfId")).as("onBehalfOfId on " + path).isNotNull();
            assertThat(UserIdContractTest.isStringUuid(parameter(path, "onBehalfOfId").path("schema"))).as("onBehalfOfId on " + path).isTrue();
        }
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 6 项第 2 条：OpenAPI 中名字以 By 结尾的属性（requestedBy、confirmedBy、financeConfirmedBy 等）都是 string / uuid")
    void thePropertiesEndingInByAreUuids() throws IOException {
        List<String> wrong = new ArrayList<>();
        Set<String> found = new HashSet<>();
        UserIdContractTest.openApi().path("components").path("schemas").properties().forEach(schema -> schema.getValue().path("properties").properties().forEach(property -> {
            if (property.getKey().endsWith("By")) {
                found.add(property.getKey());
                JsonNode value = property.getValue();
                if (!UserIdContractTest.isStringUuid("array".equals(value.path("type").asString(null)) ? value.path("items") : value)) {
                    wrong.add(schema.getKey() + "." + property.getKey() + " = " + value);
                }
            }
        }));

        assertThat(found).containsAll(BY_PROPERTIES);
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("R-1b P5 RULING-P4-2 口径：生成的 validators.ts 中 AuditEntryResponse 有 subjectKind 与 subjectId（字符串校验）、无 subjectKey，By 结尾的字段不是数字校验")
    void generatedValidatorsDoNotTreatThemAsNumbers() throws IOException {
        String validators = Files.readString(VALIDATORS);
        Matcher start = Pattern.compile("export const AuditEntryResponseSchema = z\\.object\\(\\{").matcher(validators);
        assertThat(start.find()).as("AuditEntryResponseSchema in validators.ts").isTrue();
        String block = validators.substring(start.end(), validators.indexOf("\n});", start.end()));
        assertThat(block).contains("get subjectKind()").contains("subjectId:").doesNotContain("subjectKey:");
        Matcher subjectId = Pattern.compile("(?m)^\\s*subjectId:\\s*(.*),\\s*$").matcher(block);
        assertThat(subjectId.find()).isTrue();
        assertThat(subjectId.group(1)).startsWith("z.string(");

        List<String> wrong = new ArrayList<>();
        Matcher line = Pattern.compile("(?m)^\\s*(\\w*By):\\s*(.*),\\s*$").matcher(validators);
        while (line.find()) {
            if (line.group(2).contains("number")) {
                wrong.add(line.group(1) + " = " + line.group(2));
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("R-1b P5 卡 3.4 第 3 条：OpenApiExportTest 的 isIdName 认以 By 结尾的名字；OWN_ID_FIELDS 含 AuditEntryResponse.subjectId 与 parameter subjectId，不再含 AuditEntryResponse.subjectKey")
    void theGuardKnowsTheNewFields() throws IOException {
        String source = Files.readString(Path.of("src/test/java/com/forcitis/one/OpenApiExportTest.java"));
        Matcher ownFields = Pattern.compile("OWN_ID_FIELDS\\s*=\\s*[^;]*?Set\\.of\\(([^;]*?)\\);", Pattern.DOTALL).matcher(source);

        assertThat(ownFields.find()).isTrue();
        assertThat(ownFields.group(1)).contains("\"AuditEntryResponse.subjectId\"", "\"parameter subjectId\"").doesNotContain("subjectKey");
        Matcher isIdName = Pattern.compile("boolean isIdName\\(String name\\)\\s*\\{([^}]*)\\}", Pattern.DOTALL).matcher(source);
        assertThat(isIdName.find()).isTrue();
        assertThat(isIdName.group(1)).contains("\"By\"");
    }
}
