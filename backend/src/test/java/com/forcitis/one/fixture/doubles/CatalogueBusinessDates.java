package com.forcitis.one.fixture.doubles;

import com.forcitis.one.kernel.event.EventDraft;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

/**
 * Checks, whenever a test seals an event, that its businessDate agrees with what the event catalogue declares: "无：..." means null,
 * anything else means a date. Event types the catalogue does not know (test-only events) are not checked.
 */
public final class CatalogueBusinessDates {
    private static final Path EVENTS = Path.of("../docs/architecture/events");
    private static final Map<String, String> DECLARED = load();

    private CatalogueBusinessDates() {
    }

    /** Throws when the draft's businessDate contradicts the catalogue; returns the draft so it can be used inline. */
    public static EventDraft check(EventDraft draft) {
        String problem = problem(DECLARED, draft);
        if (problem != null) {
            throw new AssertionError(problem);
        }
        return draft;
    }

    public static String problem(Map<String, String> declared, EventDraft draft) {
        String name = draft.eventType().substring(draft.eventType().lastIndexOf('.') + 1);
        String declaration = declared.get(name);
        if (declaration == null) {
            return null;
        }
        boolean declaredNone = declaration.startsWith("无");
        if (declaredNone && draft.businessDate() != null) {
            return name + ": the catalogue declares no businessDate (" + declaration + ") but the event carries " + draft.businessDate();
        }
        if (!declaredNone && draft.businessDate() == null) {
            return name + ": the catalogue declares a businessDate (" + declaration + ") but the event carries none";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> load() {
        Map<String, String> declared = new HashMap<>();
        try (Stream<Path> files = Files.list(EVENTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("asyncapi.yaml") || name.startsWith("_")) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    Map<String, Object> document = new Yaml().load(in);
                    Map<String, Map<String, Object>> messages =
                            (Map<String, Map<String, Object>>) ((Map<String, Object>) document.get("components")).get("messages");
                    messages.forEach((message, body) ->
                            declared.put(message, String.valueOf(((Map<String, Object>) body.get("x-event")).get("businessDate"))));
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return declared;
    }
}
