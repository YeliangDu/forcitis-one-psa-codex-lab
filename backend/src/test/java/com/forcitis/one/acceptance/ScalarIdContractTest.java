package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * G-C5 on the written contracts: the event catalogue (docs/architecture/events/*.yaml) and the OpenAPI document describe an id as a
 * scalar, never as an object with a "value" property. An id field is one whose name says so (…Id, …Ids) or that refers to a person.
 */
class ScalarIdContractTest {

    static final Path EVENTS = Path.of("../docs/architecture/events");
    static final Path OPENAPI = Path.of("../frontend/packages/api/openapi.json");
    static final Pattern ID_FIELD = Pattern.compile(
            "(?i)(.*Ids?|userId|employee|employees|changedEmployees|separatedEmployees|initiator|projectManager|claimedBy|releasedBy|assignee|candidates|holders|escalatedTo|from|to)");
    static final Pattern ID_TYPE = Pattern.compile(".*(Id|Ids)");

    record IdField(String where, Object schema) {
    }

    @SuppressWarnings("unchecked")
    static void collect(String where, Object node, List<IdField> found) {
        if (node instanceof Map<?, ?> schema) {
            Object properties = schema.get("properties");
            if (properties instanceof Map<?, ?> fields) {
                ((Map<String, Object>) fields).forEach((name, field) -> {
                    String path = where + "." + name;
                    if (field instanceof Map<?, ?> described) {
                        Object xType = described.get("x-type");
                        boolean named = ID_FIELD.matcher(name).matches();
                        boolean typed = xType instanceof String type && ID_TYPE.matcher(type).matches();
                        Object items = described.get("items");
                        Object target = "array".equals(described.get("type")) && items != null ? items : field;
                        if (named || typed) {
                            found.add(new IdField(path, target));
                        }
                    }
                    collect(path, field, found);
                });
            }
            schema.forEach((key, value) -> {
                if (!"properties".equals(key)) {
                    collect(where, value, found);
                }
            });
        } else if (node instanceof List<?> list) {
            list.forEach(item -> collect(where, item, found));
        }
    }

    @SuppressWarnings("unchecked")
    static boolean isValueWrapper(Object schema) {
        return schema instanceof Map<?, ?> described && described.get("properties") instanceof Map<?, ?> fields
                && ((Map<String, Object>) fields).containsKey("value");
    }

    @SuppressWarnings("unchecked")
    static List<IdField> catalogueIdFields() throws IOException {
        List<IdField> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.equals("asyncapi.yaml") || name.startsWith("_")) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    Map<String, Object> document = new Yaml().load(in);
                    Map<String, Map<String, Object>> messages = (Map<String, Map<String, Object>>) ((Map<String, Object>) document.get("components")).get("messages");
                    messages.forEach((message, body) -> collect(name + "#" + message, body.get("payload"), found));
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("R-1b P1 / G-C5 事件目录中 ID 字段为标量类型：没有一个 ID 字段被描述成带 value 子属性的对象")
    void catalogueDescribesIdsAsScalars() throws IOException {
        List<IdField> idFields = catalogueIdFields();
        assertThat(idFields.size()).as("id fields found in the event catalogue fragments").isGreaterThan(60);
        assertThat(idFields.stream().map(IdField::where).filter(where -> where.endsWith(".initiator")).count())
                .as("the person reference initiator is among the id fields examined").isGreaterThan(0);

        List<String> wrapped = idFields.stream().filter(field -> isValueWrapper(field.schema())).map(IdField::where).toList();

        assertThat(wrapped).as("id fields described as an object with a value property").isEmpty();
    }

    @Test
    @DisplayName("R-1b P1 / G-C5 OpenAPI 中没有以 {value: …} 包装的 ID 或任何其他对象（特征测试，预期现在即绿）")
    void openApiHasNoValueWrapper() throws IOException {
        JsonNode document = JsonMapper.builder().build().readTree(Files.readString(OPENAPI));
        List<String> wrappers = new ArrayList<>();
        findWrappers("", document, wrappers);

        assertThat(document.path("paths").size()).as("paths in the OpenAPI document").isGreaterThan(40);
        assertThat(wrappers).as("schemas whose only property is value").isEmpty();
    }

    private static void findWrappers(String path, JsonNode node, List<String> wrappers) {
        if (node.isObject()) {
            JsonNode properties = node.path("properties");
            if (properties.isObject() && properties.size() == 1 && properties.has("value")) {
                wrappers.add(path);
            }
            node.properties().forEach(field -> findWrappers(path + "/" + field.getKey(), field.getValue(), wrappers));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                findWrappers(path + "/" + i, node.get(i), wrappers);
            }
        }
    }
}
