package com.forcitis.one.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * R-1b P5 on the written contracts (ADR-0033 section 7 item 1, ADR-0039 section 5 item 1; card acceptance items 4 and 5, 3.4 item 4,
 * D-17): the envelope's aggregateId, the related aggregates' ids and actorId are UUIDs in the code and in the event catalogue, the
 * envelope has no source, every id typed in an event payload is a uuid, and every aggregate of the catalogue maps to an audit subject kind.
 */
class EventEnvelopeContractTest {

    static final Path EVENTS = Path.of("../docs/architecture/events");
    static final Path ASYNCAPI = EVENTS.resolve("asyncapi.yaml");
    static final String PACKAGE = "com.forcitis.one.kernel.event.";

    private static Class<?> kernel(String name) throws ClassNotFoundException {
        return Class.forName(PACKAGE + name);
    }

    private static Class<?> componentType(Class<?> record, String name) {
        return Arrays.stream(record.getRecordComponents()).filter(c -> c.getName().equals(name)).map(RecordComponent::getType).findFirst().orElse(null);
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 1 条：EventEnvelope、EventDraft、AggregateReference 的 aggregateId 与 EventEnvelope 的 actorId 的类型是 UUID")
    void theIdsOfTheEnvelopeAreUuids() throws Exception {
        assertThat(componentType(kernel("EventEnvelope"), "aggregateId")).isEqualTo(UUID.class);
        assertThat(componentType(kernel("EventEnvelope"), "actorId")).isEqualTo(UUID.class);
        assertThat(componentType(kernel("EventDraft"), "aggregateId")).isEqualTo(UUID.class);
        assertThat(componentType(kernel("AggregateReference"), "aggregateId")).isEqualTo(UUID.class);
    }

    @Test
    @DisplayName("R-1b P5 ADR-0039 第 5 条第 1 款：信封与草稿没有 source，EventSource 与 SourceSystem 两个类不存在")
    void theEnvelopeHasNoSource() {
        assertThat(componentType(kernelUnchecked("EventEnvelope"), "source")).isNull();
        assertThat(componentType(kernelUnchecked("EventDraft"), "source")).isNull();
        for (String gone : List.of("EventSource", "SourceSystem")) {
            assertThrows(ClassNotFoundException.class, () -> kernel(gone), gone + " is removed");
        }
    }

    private static Class<?> kernelUnchecked(String name) {
        try {
            return kernel(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(missing);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelopeSchema() throws IOException {
        try (InputStream in = Files.newInputStream(ASYNCAPI)) {
            Map<String, Object> document = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) document.get("components")).get("schemas")).get("EventEnvelope");
        }
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 1 条 / ADR-0039 第 5 条第 1 款：asyncapi.yaml 信封 schema 无 source（属性与 required 都无），aggregateId、relatedAggregates[].aggregateId、actorId 为 format: uuid")
    @SuppressWarnings("unchecked")
    void theCatalogueEnvelopeIsTheFinalShape() throws IOException {
        Map<String, Object> envelope = envelopeSchema();
        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) envelope.get("properties");

        assertThat(properties).doesNotContainKey("source");
        assertThat((List<String>) envelope.get("required")).doesNotContain("source");
        assertThat(properties.get("aggregateId")).containsEntry("format", "uuid");
        assertThat(properties.get("actorId")).containsEntry("format", "uuid");
        Map<String, Object> related = (Map<String, Object>) ((Map<String, Object>) properties.get("relatedAggregates").get("items")).get("properties");
        assertThat((Map<String, Object>) related.get("aggregateId")).containsEntry("format", "uuid");
    }

    @Test
    @DisplayName("R-1b P5 build_asyncapi.py 与 EVENT_ANALYTICS_FIELDS.md：生成脚本的信封定义无 source，分析字段文档不再列 source 行")
    void theGeneratorAndTheFieldListDropSource() throws IOException {
        String generator = Files.readString(Path.of("../scripts/codegen/build_asyncapi.py"));
        assertThat(generator).doesNotContain("'source'").doesNotContain("\"source\"").contains("'format': 'uuid'");
        String fields = Files.readString(Path.of("../docs/architecture/events/event-analytics-fields.md"));
        assertThat(fields).as("the source field and its parts").doesNotContain("`source`").doesNotContain("source.system").doesNotContain("source.documentRef")
                .doesNotContain("source.*");
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 守护 8 / 卡 3.4 第 4 条：事件目录各事件中 x-type 以 Id 结尾的类型化 ID 字段都是 format: uuid；approval.yaml 不再写「单据类型:单据标识」")
    @SuppressWarnings("unchecked")
    void everyTypedIdOfTheCatalogueIsAUuid() throws IOException {
        List<String> wrong = new ArrayList<>();
        int found = 0;
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    found += collect(file.getFileName().toString(), new Yaml().load(in), wrong);
                }
            }
        }

        assertThat(found).as("typed id fields in the catalogue").isGreaterThan(20);
        assertThat(wrong).isEmpty();
        assertThat(Files.readString(EVENTS.resolve("approval.yaml"))).doesNotContain("单据类型:单据标识").doesNotContain("如 TIMESHEET:7");
    }

    @SuppressWarnings("unchecked")
    private static int collect(String where, Object node, List<String> wrong) {
        int found = 0;
        if (node instanceof Map<?, ?> map) {
            if (map.get("x-type") instanceof String type && type.endsWith("Id")) {
                found++;
                Map<String, Object> schema = (Map<String, Object>) ("array".equals(map.get("type")) && map.get("items") instanceof Map<?, ?> items ? items : map);
                if (!"uuid".equals(schema.get("format"))) {
                    wrong.add(where + " (" + type + ")");
                }
            }
            for (Object value : map.values()) {
                found += collect(where, value, wrong);
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                found += collect(where, item, wrong);
            }
        }
        return found;
    }

    @Test
    @DisplayName("R-1b P5 D-17：事件目录中每个 aggregate 都能换算到审计对象类别（驼峰转大写下划线），换算结果属于类别枚举")
    @SuppressWarnings("unchecked")
    void everyAggregateMapsToAnAuditSubjectKind() throws IOException {
        Set<String> aggregates = new TreeSet<>();
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml") && !f.endsWith("asyncapi.yaml")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    gather(new Yaml().load(in), aggregates);
                }
            }
        }

        assertThat(aggregates).as("aggregates named by the catalogue").isNotEmpty();
        Set<String> kinds = Set.copyOf(P5Names.KINDS_WITH_OBJECT);
        assertThat(aggregates.stream().map(EventEnvelopeContractTest::kindOf).toList()).as("kinds of %s", aggregates).allMatch(kinds::contains);
    }

    private static String kindOf(String aggregate) {
        return aggregate.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private static void gather(Object node, Set<String> aggregates) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("aggregate") instanceof String aggregate) {
                aggregates.add(aggregate);
            }
            map.values().forEach(value -> gather(value, aggregates));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> gather(item, aggregates));
        }
    }
}
