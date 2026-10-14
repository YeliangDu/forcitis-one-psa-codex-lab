package com.forcitis.one;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * The rules an event and its catalogue entry must satisfy. Pure functions over classes and catalogue maps, so the guard test can run
 * them on the real code and the injection tests on deliberately wrong samples.
 */
final class EventCataloguePolicy {
    static final String ROOT = "com.forcitis.one.";
    static final String AUDIT = "platform.audit";
    static final Set<Class<?>> VALUE_TYPES = Set.of(UUID.class, Year.class, YearMonth.class, LocalDate.class, Instant.class);

    private EventCataloguePolicy() {
    }

    static String moduleOf(Class<?> type) {
        if (type.getPackageName().length() < ROOT.length()) {
            return "";
        }
        String[] parts = type.getPackageName().substring(ROOT.length()).split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : parts[0];
    }

    static boolean inEventsPackage(Class<?> type) {
        return type.getPackageName().endsWith(".events");
    }

    /** Events live in a module's events package and nowhere else; everything in an events package is an event. */
    static List<String> location(Collection<Class<?>> allClasses) {
        List<String> problems = new ArrayList<>();
        for (Class<?> type : allClasses) {
            boolean event = DomainEvent.class.isAssignableFrom(type) && type != DomainEvent.class;
            if (event && !inEventsPackage(type)) {
                problems.add(type.getName() + ": a domain event belongs in the events package of its module");
            }
            if (!event && inEventsPackage(type) && !type.getSimpleName().equals("package-info")) {
                problems.add(type.getName() + ": a class in an events package must be a record implementing DomainEvent");
            }
        }
        return problems;
    }

    /** Immutable record, implements DomainEvent, envelope first, payload of ids and value objects only. */
    static List<String> shape(Class<?> event) {
        List<String> problems = new ArrayList<>();
        String where = event.getName() + ": ";
        if (!event.isRecord()) {
            problems.add(where + "an event is an immutable record");
            return problems;
        }
        if (!DomainEvent.class.isAssignableFrom(event)) {
            problems.add(where + "an event implements DomainEvent");
        }
        RecordComponent[] components = event.getRecordComponents();
        if (components.length == 0 || !components[0].getName().equals("envelope") || components[0].getType() != EventEnvelope.class) {
            problems.add(where + "the first component is the EventEnvelope named envelope");
        }
        for (int i = 1; i < components.length; i++) {
            String problem = payloadProblem(components[i].getGenericType());
            if (problem != null) {
                problems.add(where + "payload component " + components[i].getName() + ": " + problem);
            }
        }
        return problems;
    }

    private static String payloadProblem(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            if (parameterized.getRawType() != List.class) {
                return "only List of ids or value objects may be generic, not " + parameterized.getRawType().getTypeName();
            }
            return payloadProblem(parameterized.getActualTypeArguments()[0]);
        }
        if (type instanceof GenericArrayType) {
            return "arrays are mutable";
        }
        if (!(type instanceof Class<?> raw)) {
            return "unsupported type " + type.getTypeName();
        }
        boolean id = raw.getSimpleName().endsWith("Id") && raw.isRecord();
        boolean valueObject = raw.isAnnotationPresent(ValueObject.class) || raw.isEnum() || VALUE_TYPES.contains(raw);
        if (id || valueObject) {
            return null;
        }
        return raw.getName() + " is neither an id nor a value object (ids, @ValueObject records, enums, UUID, java.time dates)";
    }

    /** Both directions: every event is catalogued and every catalogue entry has its event; pending modules are skipped. */
    static List<String> catalogue(Collection<Class<?>> events, Map<String, Map<String, Object>> messages, Set<String> pendingModules) {
        List<String> problems = new ArrayList<>();
        Set<String> classes = new LinkedHashSet<>();
        for (Class<?> event : events) {
            if (pendingModules.contains(moduleOf(event))) {
                continue;
            }
            classes.add(event.getName());
        }
        Set<String> catalogued = new TreeSet<>();
        messages.forEach((name, message) -> {
            Map<String, Object> x = x(message);
            String className = String.valueOf(x.get("class"));
            catalogued.add(className);
            if (!classes.contains(className)) {
                problems.add("catalogue message " + name + " names " + className + ", which is not an event in the code");
            }
        });
        classes.stream().filter(c -> !catalogued.contains(c)).forEach(c -> problems.add(c + ": not in the event catalogue"));
        for (Class<?> event : events) {
            Map<String, Object> message = messages.get(event.getSimpleName());
            if (message == null) {
                continue;
            }
            Map<String, Object> x = x(message);
            String module = moduleOf(event);
            String where = event.getSimpleName() + ": ";
            expect(problems, where + "eventType", module + "." + event.getSimpleName(), x.get("eventType"));
            expect(problems, where + "publisher", module, x.get("publisher"));
            expect(problems, where + "class", event.getName(), x.get("class"));
            expect(problems, where + "idempotencyKey", "eventId", x.get("idempotencyKey"));
            for (String field : List.of("version", "aggregate", "delivery", "formsFact", "businessDate", "audit", "publisherTest", "consumers")) {
                if (x.get(field) == null || String.valueOf(x.get(field)).isBlank()) {
                    problems.add(where + "x-event." + field + " is missing");
                }
            }
            if (event.isRecord()) {
                Set<String> declared = properties(message);
                Set<String> actual = new TreeSet<>();
                RecordComponent[] components = event.getRecordComponents();
                for (int i = 1; i < components.length; i++) {
                    actual.add(components[i].getName());
                }
                if (!declared.equals(actual)) {
                    problems.add(where + "payload properties " + declared + " differ from the record components " + actual);
                }
            }
        }
        return problems;
    }

    /** Every event needs a publisher test: an existing test class that names the event and contains tests. */
    static List<String> publisherTests(Collection<Class<?>> events, Map<String, Map<String, Object>> messages,
                                       Function<String, String> testSourceOf) {
        List<String> problems = new ArrayList<>();
        for (Class<?> event : events) {
            Map<String, Object> message = messages.get(event.getSimpleName());
            if (message == null) {
                continue;
            }
            String test = String.valueOf(x(message).get("publisherTest"));
            String source = testSourceOf.apply(test);
            if (source == null) {
                problems.add(event.getSimpleName() + ": publisher test " + test + " does not exist");
            } else if (!source.contains(event.getSimpleName()) || !source.contains("@Test")) {
                problems.add(event.getSimpleName() + ": publisher test " + test + " does not exercise the event");
            }
        }
        return problems;
    }

    /** audited: the generic audit subscription must exist; not audited: the record says so with a reason, and the catalogue repeats it. */
    static List<String> audit(Collection<Class<?>> events, Map<String, Map<String, Object>> messages, boolean auditSubscriptionExists) {
        List<String> problems = new ArrayList<>();
        for (Class<?> event : events) {
            Map<String, Object> message = messages.get(event.getSimpleName());
            if (message == null) {
                continue;
            }
            Map<String, Object> x = x(message);
            String where = event.getSimpleName() + ": ";
            String audit = String.valueOf(x.get("audit"));
            NotAudited notAudited = event.getAnnotation(NotAudited.class);
            List<?> consumers = (List<?>) x.get("consumers");
            if (notAudited != null) {
                if (notAudited.reason().isBlank()) {
                    problems.add(where + "@NotAudited needs a reason");
                }
                if (!audit.startsWith("not-audited:") || audit.substring("not-audited:".length()).isBlank()) {
                    problems.add(where + "the record says @NotAudited, so the catalogue says \"not-audited: <reason>\" (was: " + audit + ")");
                }
                if (consumers.contains(AUDIT)) {
                    problems.add(where + "a not audited event does not list platform.audit as consumer");
                }
            } else {
                if (!audit.equals("audited")) {
                    problems.add(where + "no @NotAudited on the record, so the catalogue says \"audited\" (was: " + audit + ")");
                }
                if (!auditSubscriptionExists) {
                    problems.add(where + "audited, but platform.audit has no subscription to DomainEvent");
                }
                if (!consumers.contains(AUDIT)) {
                    problems.add(where + "an audited event lists platform.audit as consumer");
                }
            }
        }
        return problems;
    }

    /** The declared consumers (apart from the audit trail) are the modules that really listen; none means a reason is given. */
    static List<String> consumers(Collection<Class<?>> events, Map<String, Map<String, Object>> messages,
                                  Collection<Class<?>> allClasses) {
        List<String> problems = new ArrayList<>();
        for (Class<?> event : events) {
            Map<String, Object> message = messages.get(event.getSimpleName());
            if (message == null) {
                continue;
            }
            Map<String, Object> x = x(message);
            Set<String> declared = new TreeSet<>();
            for (Object consumer : (List<?>) x.get("consumers")) {
                declared.add(String.valueOf(consumer));
            }
            declared.remove(AUDIT);
            Set<String> actual = new TreeSet<>();
            for (Class<?> type : allClasses) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(ApplicationModuleListener.class) && method.getParameterCount() > 0
                            && method.getParameterTypes()[0] == event) {
                        actual.add(moduleOf(type));
                    }
                }
            }
            if (!declared.equals(actual)) {
                problems.add(event.getSimpleName() + ": declared consumers " + declared + " differ from the listeners in the code " + actual);
            }
            boolean reason = x.get("noConsumer") != null && !String.valueOf(x.get("noConsumer")).isBlank();
            if (declared.isEmpty() && !(reason && String.valueOf(x.get("noConsumer")).startsWith("暂无消费方"))) {
                problems.add(event.getSimpleName() + ": no consumer, so noConsumer starts with \"暂无消费方\" and says why it is published now");
            }
        }
        return problems;
    }

    static boolean auditSubscriptionExists(Collection<Class<?>> allClasses) {
        for (Class<?> type : allClasses) {
            if (!moduleOf(type).equals(AUDIT)) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ApplicationModuleListener.class) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == DomainEvent.class) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> x(Map<String, Object> message) {
        return (Map<String, Object>) message.get("x-event");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> properties(Map<String, Object> message) {
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        Map<String, Object> properties = (Map<String, Object>) payload.get("properties");
        return new TreeSet<>(properties.keySet());
    }

    private static void expect(List<String> problems, String what, Object expected, Object actual) {
        if (!String.valueOf(expected).equals(String.valueOf(actual))) {
            problems.add(what + " should be " + expected + " (was " + actual + ")");
        }
    }
}
