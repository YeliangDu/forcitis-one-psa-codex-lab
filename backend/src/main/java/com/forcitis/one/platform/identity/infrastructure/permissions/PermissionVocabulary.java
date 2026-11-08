package com.forcitis.one.platform.identity.infrastructure.permissions;

import com.forcitis.one.kernel.PermissionCode;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

/** Packaged copies of the existing G43 vocabulary, not a separate permission register. */
final class PermissionVocabulary {
    private static final Set<String> WORDS = new HashSet<>();
    private static final Set<String> ACTIONS = new HashSet<>();
    private static final Set<String> MODULES = new HashSet<>();
    private static final Map<String, List<Map<?, ?>>> RESOURCES = new HashMap<>();
    private static final Set<String> RESOURCE_KINDS = Set.of("aggregate", "entity", "value_object", "read_model");

    static {
        try {
            var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:permission-vocabulary/**/*.yaml");
            if (resources.length == 0) { throw new IllegalArgumentException("Missing permission vocabulary"); }
            for (var resource : resources) {
                try (var stream = resource.getInputStream()) {
                    Object loaded = new Yaml().load(stream);
                    if (!(loaded instanceof Map<?, ?> root)) { throw new IllegalArgumentException("Invalid permission vocabulary"); }
                    for (Object word : list(root.get("common_words"))) { words(String.valueOf(word)); }
                    for (Object action : list(root.get("standard_actions"))) {
                        ACTIONS.add(String.valueOf(action));
                        words(String.valueOf(action));
                    }
                    for (Object value : list(root.get("terms"))) {
                        if (!(value instanceof Map<?, ?> term)) { throw new IllegalArgumentException("Invalid vocabulary term"); }
                        registerResource(term);
                        words(String.valueOf(term.get("en")));
                        words(String.valueOf(term.get("snake")));
                        for (Object attribute : list(term.get("attributes"))) {
                            if (attribute instanceof Map<?, ?> fields) {
                                words(String.valueOf(fields.get("en")));
                                words(String.valueOf(fields.get("snake")));
                            }
                        }
                    }
                    for (Object value : list(root.get("bcs"))) {
                        if (value instanceof Map<?, ?> bc) {
                            if ("bc".equals(bc.get("kind"))) { MODULES.add(String.valueOf(bc.get("id"))); }
                            if ("system".equals(bc.get("kind"))) { words(String.valueOf(bc.get("en"))); }
                        }
                    }
                }
            }
            if (ACTIONS.isEmpty() || MODULES.isEmpty()) { throw new IllegalArgumentException("Incomplete permission vocabulary"); }
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot load permission vocabulary", error);
        }
    }

    private PermissionVocabulary() {}

    static void validate(PermissionCode code) {
        String[] parts = code.value().split(":");
        if (!MODULES.contains(parts[0]) || !ACTIONS.contains(parts[2])
                || Arrays.stream(parts[1].split("-")).anyMatch(word -> !WORDS.contains(singular(word)))) {
            throw new IllegalArgumentException("G43 unregistered permission module, resource or action: " + code.value());
        }
    }

    /** New resources match the complete owning term, never independently registered tokens. */
    static void validateResource(PermissionCode code) {
        String[] parts = code.value().split(":");
        String key = parts[0] + ":" + parts[1].replace('-', '_');
        List<Map<?, ?>> concepts = RESOURCES.get(key);
        if (concepts == null || concepts.size() != 1) {
            throw new IllegalArgumentException("Missing or ambiguous owning permission resource: " + code.value());
        }
    }

    private static void registerResource(Map<?, ?> term) {
        if (!"domain".equals(term.get("scope")) || !(term.get("kind") instanceof String kind) || !RESOURCE_KINDS.contains(kind)
                || !("approved".equals(term.get("status")) || "proposed".equals(term.get("status")))) {
            return;
        }
        if (!(term.get("id") instanceof String id) || id.isBlank()
                || !(term.get("bc") instanceof String owner) || !owner.matches("[a-z]+\\.[a-z]+")
                || !(term.get("snake") instanceof String snake) || !snake.matches("[a-z]+(?:_[a-z]+)*")
                || !(term.get("en") instanceof String name) || name.isBlank()) {
            // General glossary concepts may have shapes that cannot name a
            // permission resource. They remain words, not resource registrations.
            return;
        }
        List<Map<?, ?>> concepts = RESOURCES.computeIfAbsent(owner + ":" + snake, ignored -> new ArrayList<>());
        // Only identical semantic maps with the same stable id collapse;
        // a second id or changed metadata for the same resource remains ambiguous.
        Map<?, ?> registration = Collections.unmodifiableMap(new LinkedHashMap<>(term));
        if (!concepts.contains(registration)) { concepts.add(registration); }
    }

    private static List<?> list(@org.jspecify.annotations.Nullable Object value) { return value instanceof List<?> items ? items : List.of(); }

    private static void words(String identifier) {
        String spaced = identifier.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
        Arrays.stream(spaced.split("[^A-Za-z0-9]+|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])"))
                .map(word -> word.toLowerCase(Locale.ROOT)).filter(word -> !word.isEmpty() && !word.chars().allMatch(Character::isDigit))
                .forEach(word -> WORDS.add(singular(word)));
    }

    private static String singular(String word) {
        if (word.endsWith("ies") && word.length() > 4) { return word.substring(0, word.length() - 3) + "y"; }
        if (word.endsWith("sses") || word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && word.length() > 3) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
