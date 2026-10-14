package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.sample.events.Samples;
import com.forcitis.one.fixture.sample.misplaced.Misplaced;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each guard is shown to catch what it is meant to catch: a deliberately wrong sample fails, the right one passes. */
class EventCataloguePolicyTest {
    static final String GOOD = Samples.GoodEvent.class.getName();

    static Map<String, Object> message(Class<?> event, Map<String, Object> overrides) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("eventType", EventCataloguePolicy.moduleOf(event) + "." + event.getSimpleName());
        x.put("version", 1);
        x.put("class", event.getName());
        x.put("aggregate", "Sample");
        x.put("idempotencyKey", "eventId");
        x.put("publisher", EventCataloguePolicy.moduleOf(event));
        x.put("consumers", List.of("platform.audit"));
        x.put("delivery", "at least once");
        x.put("formsFact", false);
        x.put("businessDate", "无：运维");
        x.put("audit", "audited");
        x.put("publisherTest", EventCataloguePolicyTest.class.getName());
        x.put("noConsumer", "暂无消费方：留痕即可");
        x.putAll(overrides);
        Map<String, Object> properties = new LinkedHashMap<>();
        if (event.isRecord()) {
            var components = event.getRecordComponents();
            for (int i = 1; i < components.length; i++) {
                properties.put(components[i].getName(), Map.of("type", "string"));
            }
        }
        return Map.of("name", event.getSimpleName(), "payload", Map.of("type", "object", "properties", properties), "x-event", x);
    }

    static Map<String, Map<String, Object>> catalogue(Class<?>... events) {
        Map<String, Map<String, Object>> messages = new LinkedHashMap<>();
        for (Class<?> event : events) {
            messages.put(event.getSimpleName(), message(event, Map.of()));
        }
        return messages;
    }

    @Test
    @DisplayName("a record implementing DomainEvent with the envelope first and ids as payload passes")
    void rightShapePasses() {
        assertThat(EventCataloguePolicy.shape(Samples.GoodEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("shape: a class that is not a record, a missing DomainEvent, a misplaced envelope, text / map / array payloads all fail")
    void wrongShapesFail() {
        assertThat(EventCataloguePolicy.shape(Samples.MutableEvent.class)).anyMatch(p -> p.contains("immutable record"));
        assertThat(EventCataloguePolicy.shape(Samples.NotADomainEvent.class)).anyMatch(p -> p.contains("implements DomainEvent"));
        assertThat(EventCataloguePolicy.shape(Samples.EnvelopeNotFirst.class)).anyMatch(p -> p.contains("first component"));
        assertThat(EventCataloguePolicy.shape(Samples.WithText.class)).anyMatch(p -> p.contains("note") && p.contains("neither an id nor a value object"));
        assertThat(EventCataloguePolicy.shape(Samples.WithMap.class)).anyMatch(p -> p.contains("only List"));
        assertThat(EventCataloguePolicy.shape(Samples.WithArray.class)).anyMatch(p -> p.contains("arrays are mutable") || p.contains("neither"));
    }

    @Test
    @DisplayName("location: a DomainEvent outside an events package fails, and so does a plain class inside one")
    void locationRule() {
        assertThat(EventCataloguePolicy.location(List.of(Misplaced.class))).anyMatch(p -> p.contains("belongs in the events package"));
        assertThat(EventCataloguePolicy.location(List.of(Samples.NotADomainEvent.class, Samples.class)))
                .anyMatch(p -> p.contains("NotADomainEvent") && p.contains("must be a record implementing DomainEvent"));
        assertThat(EventCataloguePolicy.location(List.of(Samples.GoodEvent.class))).isEmpty();
    }

    @Test
    @DisplayName("catalogue: an event missing from it, an entry without an event, a wrong type or publisher, and drifting payload fields fail")
    void catalogueConsistency() {
        assertThat(EventCataloguePolicy.catalogue(List.of(Samples.GoodEvent.class), Map.of(), Set.of()))
                .anyMatch(p -> p.contains(GOOD) && p.contains("not in the event catalogue"));
        assertThat(EventCataloguePolicy.catalogue(List.of(), catalogue(Samples.GoodEvent.class), Set.of()))
                .anyMatch(p -> p.contains("not an event in the code"));
        Map<String, Map<String, Object>> wrong = Map.of("GoodEvent", message(Samples.GoodEvent.class, Map.of("eventType", "x.y.Other", "publisher", "x.y")));
        assertThat(EventCataloguePolicy.catalogue(List.of(Samples.GoodEvent.class), wrong, Set.of()))
                .anyMatch(p -> p.contains("eventType should be")).anyMatch(p -> p.contains("publisher should be"));
        Map<String, Object> drifting = new LinkedHashMap<>(message(Samples.GoodEvent.class, Map.of()));
        drifting.put("payload", Map.of("type", "object", "properties", Map.of("userId", Map.of("type", "string"))));
        assertThat(EventCataloguePolicy.catalogue(List.of(Samples.GoodEvent.class), Map.of("GoodEvent", drifting), Set.of()))
                .anyMatch(p -> p.contains("differ from the record components"));
        assertThat(EventCataloguePolicy.catalogue(List.of(Samples.GoodEvent.class), catalogue(Samples.GoodEvent.class), Set.of())).isEmpty();
        assertThat(EventCataloguePolicy.catalogue(List.of(Samples.GoodEvent.class), Map.of(), Set.of("fixture.sample"))).as("pending module").isEmpty();
    }

    @Test
    @DisplayName("publisher test: a missing test class and a test that never names the event fail")
    void publisherTestRule() {
        Map<String, Map<String, Object>> messages = catalogue(Samples.GoodEvent.class);
        assertThat(EventCataloguePolicy.publisherTests(List.of(Samples.GoodEvent.class), messages, name -> null))
                .anyMatch(p -> p.contains("does not exist"));
        assertThat(EventCataloguePolicy.publisherTests(List.of(Samples.GoodEvent.class), messages, name -> "class T { @Test void other() {} }"))
                .anyMatch(p -> p.contains("does not exercise the event"));
        assertThat(EventCataloguePolicy.publisherTests(List.of(Samples.GoodEvent.class), messages,
                name -> "class T { @Test void publishes() { new GoodEvent(); } }")).isEmpty();
    }

    @Test
    @DisplayName("audit: an event is either audited with the subscription in place, or @NotAudited with a reason that the catalogue repeats")
    void auditRule() {
        Map<String, Map<String, Object>> messages = catalogue(Samples.GoodEvent.class, Samples.Quiet.class, Samples.BlankReason.class);
        List<Class<?>> events = List.of(Samples.GoodEvent.class, Samples.Quiet.class, Samples.BlankReason.class);

        List<String> problems = EventCataloguePolicy.audit(events, messages, true);

        assertThat(problems).anyMatch(p -> p.startsWith("Quiet") && p.contains("not-audited"));
        assertThat(problems).anyMatch(p -> p.startsWith("BlankReason") && p.contains("needs a reason"));
        assertThat(problems).noneMatch(p -> p.startsWith("GoodEvent"));
        assertThat(EventCataloguePolicy.audit(List.of(Samples.GoodEvent.class), catalogue(Samples.GoodEvent.class), false))
                .anyMatch(p -> p.contains("no subscription"));
        Map<String, Map<String, Object>> declaredQuiet = Map.of("Quiet", message(Samples.Quiet.class,
                Map.of("audit", "not-audited: noise", "consumers", List.of(), "noConsumer", "暂无消费方：x")));
        assertThat(EventCataloguePolicy.audit(List.of(Samples.Quiet.class), declaredQuiet, true)).isEmpty();
        assertThat(EventCataloguePolicy.audit(List.of(Samples.GoodEvent.class), Map.of("GoodEvent", message(Samples.GoodEvent.class,
                Map.of("audit", "not-audited: because"))), true)).anyMatch(p -> p.contains("no @NotAudited on the record"));
    }

    @Test
    @DisplayName("consumers: a declared consumer nobody implements, an undeclared listener and a missing 暂无消费方 reason fail")
    void consumerRule() {
        Map<String, Map<String, Object>> invented = Map.of("GoodEvent", message(Samples.GoodEvent.class,
                Map.of("consumers", List.of("platform.audit", "platform.identity"))));
        assertThat(EventCataloguePolicy.consumers(List.of(Samples.GoodEvent.class), invented, List.of()))
                .anyMatch(p -> p.contains("differ from the listeners"));
        Map<String, Map<String, Object>> noReason = Map.of("GoodEvent", message(Samples.GoodEvent.class, Map.of("noConsumer", "")));
        assertThat(EventCataloguePolicy.consumers(List.of(Samples.GoodEvent.class), noReason, List.of()))
                .anyMatch(p -> p.contains("暂无消费方"));
        assertThat(EventCataloguePolicy.consumers(List.of(Samples.GoodEvent.class), catalogue(Samples.GoodEvent.class), List.of())).isEmpty();
    }
}
