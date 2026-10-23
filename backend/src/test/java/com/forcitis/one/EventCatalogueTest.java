package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.event.DomainEvent;
import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * docs/modernization/events/asyncapi.yaml is composed from the fragments next to it (build_asyncapi.py) and must describe exactly the
 * events in the code. The rules themselves are in EventCataloguePolicy, shown to work by EventCataloguePolicyTest.
 */
class EventCatalogueTest {
    static final Path EVENTS = Path.of("../docs/modernization/events");
    /** Modules whose events are catalogued by their own work stream: remove the entry when its fragment is merged. */
    static final Map<String, String> PENDING_FRAGMENTS = Map.of();

    static final List<Class<?>> ALL = ArchitectureRulesTest.CLASSES.stream().<Class<?>>map(JavaClass::reflect).toList();

    static List<Class<?>> events() {
        return ALL.stream().filter(c -> DomainEvent.class.isAssignableFrom(c) && c != DomainEvent.class)
                .filter(c -> !PENDING_FRAGMENTS.containsKey(EventCataloguePolicy.moduleOf(c))).toList();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> fragmentMessages() throws IOException {
        Map<String, Map<String, Object>> messages = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.equals("asyncapi.yaml") || name.startsWith("_")) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    Map<String, Object> document = new Yaml().load(in);
                    Map<String, Object> components = (Map<String, Object>) document.get("components");
                    ((Map<String, Map<String, Object>>) components.get("messages")).forEach((message, body) -> {
                        assertThat(messages.put(message, body)).as("%s is defined in two fragments", message).isNull();
                    });
                }
            }
        }
        return messages;
    }

    private static void none(String rule, List<String> problems) {
        assertThat(problems).as(rule).isEmpty();
    }

    @Test
    @DisplayName("events live only in the events package of their module, and everything there is an event")
    void eventsOnlyInTheEventsPackage() {
        none("location", EventCataloguePolicy.location(ALL.stream().filter(c -> !PENDING_FRAGMENTS.containsKey(EventCataloguePolicy.moduleOf(c))
                || !EventCataloguePolicy.inEventsPackage(c)).toList()));
    }

    @Test
    @DisplayName("every event is an immutable record implementing DomainEvent with the envelope first and a payload of ids and value objects")
    void shape() {
        assertThat(events()).isNotEmpty();
        List<String> problems = new ArrayList<>();
        events().forEach(event -> problems.addAll(EventCataloguePolicy.shape(event)));
        none("shape", problems);
    }

    @Test
    @DisplayName("the catalogue and the code list the same events, with the same type, publisher and payload fields")
    void catalogueMatchesTheCode() throws IOException {
        none("catalogue", EventCataloguePolicy.catalogue(events(), fragmentMessages(), PENDING_FRAGMENTS.keySet()));
    }

    @Test
    @DisplayName("every event has a publisher test that names it")
    void everyEventHasAPublisherTest() throws IOException {
        none("publisher tests", EventCataloguePolicy.publisherTests(events(), fragmentMessages(), className -> {
            Path file = Path.of("src/test/java", className.replace('.', '/') + ".java");
            try {
                return Files.isRegularFile(file) ? Files.readString(file) : null;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }));
    }

    @Test
    @DisplayName("every event is audited through platform.audit's subscription or says why not, in the record and in the catalogue")
    void everyEventIsAuditedOrSaysWhyNot() throws IOException {
        assertThat(EventCataloguePolicy.auditSubscriptionExists(ALL)).as("platform.audit subscribes to DomainEvent").isTrue();
        none("audit", EventCataloguePolicy.audit(events(), fragmentMessages(), EventCataloguePolicy.auditSubscriptionExists(ALL)));
    }

    @Test
    @DisplayName("the declared consumers are the real listeners; an event without one says 暂无消费方 and why it is published now")
    void consumersAreDeclared() throws IOException {
        none("consumers", EventCataloguePolicy.consumers(events(), fragmentMessages(), ALL));
    }

    @Test
    @DisplayName("a pending module drops out of the exemption as soon as its fragment exists")
    void pendingModulesHaveNoFragmentYet() {
        PENDING_FRAGMENTS.forEach((module, fragment) -> assertThat(Files.exists(EVENTS.resolve(fragment)))
                .as("%s exists: delete %s from PENDING_FRAGMENTS so its events are checked", fragment, module).isFalse());
    }

    @Test
    @DisplayName("asyncapi.yaml is the composition of the fragments: same messages, same x-event blocks")
    @SuppressWarnings("unchecked")
    void composedCatalogueIsCurrent() throws IOException {
        Map<String, Object> composed;
        try (InputStream in = Files.newInputStream(EVENTS.resolve("asyncapi.yaml"))) {
            composed = new Yaml().load(in);
        }
        assertThat(composed.get("asyncapi")).isEqualTo("3.0.0");
        Map<String, Map<String, Object>> fromFragments = fragmentMessages();
        Map<String, Map<String, Object>> messages = (Map<String, Map<String, Object>>) ((Map<String, Object>) composed.get("components")).get("messages");
        assertThat(new TreeSet<>(messages.keySet())).as("run python3 docs/modernization/events/build_asyncapi.py").isEqualTo(new TreeSet<>(fromFragments.keySet()));
        fromFragments.forEach((name, message) -> assertThat(messages.get(name).get("x-event")).as(name).isEqualTo(message.get("x-event")));
        Map<String, Object> channels = (Map<String, Object>) composed.get("channels");
        Set<String> addresses = new TreeSet<>();
        channels.values().forEach(channel -> addresses.add((String) ((Map<String, Object>) channel).get("address")));
        Set<String> types = new TreeSet<>();
        fromFragments.values().forEach(message -> types.add((String) ((Map<String, Object>) message.get("x-event")).get("eventType")));
        assertThat(addresses).isEqualTo(types);
    }

    // ADR-0033 guard 8 (event part) and D-17

    /** The envelope has no source, and its aggregateId, the related aggregates' aggregateId and its actorId are uuids. */
    @SuppressWarnings("unchecked")
    static List<String> envelopeProblems(Map<String, Object> envelope) {
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) envelope.get("properties");
        if (properties.containsKey("source") || ((List<String>) envelope.get("required")).contains("source")) {
            problems.add("the envelope still has source");
        }
        for (String name : List.of("aggregateId", "actorId")) {
            if (properties.get(name) == null || !"uuid".equals(properties.get(name).get("format"))) {
                problems.add("envelope " + name + " is not format: uuid");
            }
        }
        Map<String, Object> related = (Map<String, Object>) ((Map<String, Object>) properties.get("relatedAggregates").get("items")).get("properties");
        if (!"uuid".equals(((Map<String, Object>) related.get("aggregateId")).get("format"))) {
            problems.add("relatedAggregates[].aggregateId is not format: uuid");
        }
        return problems;
    }

    /** Every payload field whose x-type is a typed id (ends with Id) is format: uuid. */
    @SuppressWarnings("unchecked")
    static List<String> typedIdProblems(String where, Object node) {
        List<String> problems = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            if (map.get("x-type") instanceof String type && type.endsWith("Id")) {
                Map<String, Object> schema = (Map<String, Object>) ("array".equals(map.get("type")) && map.get("items") instanceof Map<?, ?> items ? items : map);
                if (!"uuid".equals(schema.get("format"))) {
                    problems.add(where + " (" + type + ") is not format: uuid");
                }
            }
            map.values().forEach(value -> problems.addAll(typedIdProblems(where, value)));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> problems.addAll(typedIdProblems(where, item)));
        }
        return problems;
    }

    /** Every aggregate the catalogue names has an audit subject kind, and the kinds of the database CHECK are the enum's. */
    static String allAuditMigrations() throws IOException {
        StringBuilder all = new StringBuilder();
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration/platform_audit"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).sorted().toList()) {
                all.append(Files.readString(file)).append('\n');
            }
        }
        return all.toString();
    }

    static List<String> auditKindProblems(Set<String> aggregates, String checkSql) {
        List<String> problems = new ArrayList<>();
        for (String aggregate : aggregates) {
            try {
                AuditSubjectKind.ofAggregateType(aggregate);
            } catch (IllegalArgumentException unknown) {
                problems.add("aggregate " + aggregate + " has no AuditSubjectKind: add the kind (enum and registry seed) before the aggregate");
            }
        }
        Matcher seeds = Pattern.compile("'([A-Z][A-Z0-9_]+)', '[a-z.]+', (?:true|false), '(?:ACTIVE|RETIRED)'").matcher(checkSql);
        Set<String> inSql = new TreeSet<>();
        while (seeds.find()) {
            inSql.add(seeds.group(1));
        }
        if (inSql.isEmpty()) {
            problems.add("no audit_subject_kinds seed is in the baseline");
            return problems;
        }
        Set<String> inEnum = new TreeSet<>();
        for (AuditSubjectKind kind : AuditSubjectKind.values()) {
            inEnum.add(kind.name());
        }
        if (!inSql.equals(inEnum)) {
            problems.add("the audit_subject_kinds seeds list " + inSql + " but AuditSubjectKind has " + inEnum);
        }
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static void gatherAggregates(Object node, Set<String> aggregates) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("aggregate") instanceof String aggregate) {
                aggregates.add(aggregate);
            }
            map.values().forEach(value -> gatherAggregates(value, aggregates));
        } else if (node instanceof List<?> list) {
            list.forEach(item -> gatherAggregates(item, aggregates));
        }
    }

    @Test
    @DisplayName("ADR-0033 guard 8 / ADR-0039: the catalogue envelope has no source and uuid ids; every typed id of a payload is a uuid; every aggregate has an audit subject kind that the database CHECK lists")
    @SuppressWarnings("unchecked")
    void ids_and_kinds_of_the_catalogue() throws IOException {
        Map<String, Object> document;
        try (InputStream in = Files.newInputStream(EVENTS.resolve("asyncapi.yaml"))) {
            document = new Yaml().load(in);
        }
        Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("schemas");
        List<String> problems = new ArrayList<>(envelopeProblems((Map<String, Object>) schemas.get("EventEnvelope")));
        Set<String> aggregates = new TreeSet<>();
        for (Map.Entry<String, Map<String, Object>> message : fragmentMessages().entrySet()) {
            problems.addAll(typedIdProblems(message.getKey(), message.getValue().get("payload")));
            gatherAggregates(message.getValue(), aggregates);
        }
        assertThat(aggregates).as("aggregates named by the fragments").isNotEmpty();
        problems.addAll(auditKindProblems(aggregates, allAuditMigrations()));
        none("catalogue ids and audit kinds", problems);
    }

    @Test
    @DisplayName("injected: an envelope with source or a text aggregateId, a typed id without format: uuid, an aggregate without a kind and registry seeds that differ from the enum are caught")
    void catalogueGuardsCatchInjectedSamples() {
        Map<String, Object> envelope = new Yaml().load("""
                required: [eventId, source]
                properties:
                  aggregateId: {type: string}
                  actorId: {type: string, format: uuid}
                  source: {type: object}
                  relatedAggregates:
                    items:
                      properties:
                        aggregateId: {type: string}
                """);
        assertThat(envelopeProblems(envelope)).containsExactlyInAnyOrder("the envelope still has source", "envelope aggregateId is not format: uuid",
                "relatedAggregates[].aggregateId is not format: uuid");

        Object payload = new Yaml().load("""
                properties:
                  roleId: {type: string, x-type: RoleId}
                  roleIds: {type: array, x-type: RoleId, items: {type: string, format: uuid}}
                  loose: {type: array, items: {type: string, x-type: RoleId}}
                  fine: {type: string, format: uuid, x-type: RoleId}
                """);
        assertThat(typedIdProblems("Sample", payload)).containsExactlyInAnyOrder("Sample (RoleId) is not format: uuid", "Sample (RoleId) is not format: uuid");

        String check = "('019a0000-0000-7000-8000-000000000601', 'USER_ACCOUNT', 'platform.identity', true, 'ACTIVE', DATE '2000-01-01'), ('019a0000-0000-7000-8000-000000000605', 'ROLE', 'platform.identity', true, 'ACTIVE', DATE '2000-01-01')";
        assertThat(auditKindProblems(Set.of("NoSuchThing"), check)).anyMatch(p -> p.startsWith("aggregate NoSuchThing has no AuditSubjectKind"))
                .anyMatch(p -> p.startsWith("the audit_subject_kinds seeds list"));
        assertThat(auditKindProblems(Set.of("UserAccount"), "no check here")).containsExactly("no audit_subject_kinds seed is in the baseline");
    }
}
