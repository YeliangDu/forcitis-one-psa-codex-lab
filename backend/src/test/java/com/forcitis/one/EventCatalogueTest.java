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
}
