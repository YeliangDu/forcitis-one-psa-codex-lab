package com.forcitis.one;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

final class Glossary {
    static final Path FILE = Path.of("glossary/terms.yaml");
    static final Path OFFICIAL = Path.of("../docs/glossary/terms.yaml");
    /** Bounded-context register: the names of external systems (kind: system) are registered words, they are no longer terms (B2). */
    static final Path CONTEXTS = Path.of("../docs/glossary/bcs.yaml");
    /** One file per module so parallel work never edits the same file. */
    static final Path PROPOSED_DIR = Path.of("glossary/proposed");
    static final Set<String> STATUSES = Set.of("approved", "proposed", "deprecated");
    static final List<String> FIELDS = List.of("id", "zh", "en", "snake", "kind", "bc", "definition", "standard_ref",
            "confidence", "aliases", "forbidden", "status");

    record Term(String id, String zh, String en, String snake, String status, Map<String, List<String>> forbidden, String kind, String bc) {
        List<String> words() {
            Set<String> words = new LinkedHashSet<>(split(en));
            words.addAll(split(snake));
            return List.copyOf(words);
        }
    }

    private static final List<Term> TERMS = new ArrayList<>();
    private static final Set<String> COMMON_WORDS = new LinkedHashSet<>();
    private static final Set<String> STANDARD_ACTIONS = new LinkedHashSet<>();
    private static final List<String> PROBLEMS = new ArrayList<>();
    private static final Map<String, String> REGISTERED = new LinkedHashMap<>();

    static {
        try {
            load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        COMMON_WORDS.forEach(w -> REGISTERED.put(singular(w), "approved"));
        STANDARD_ACTIONS.forEach(w -> REGISTERED.put(singular(w), "approved"));
        TERMS.stream().filter(t -> t.status().equals("approved"))
                .forEach(t -> t.words().forEach(w -> REGISTERED.put(singular(w), "approved")));
        TERMS.stream().filter(t -> !t.status().equals("approved"))
                .forEach(t -> t.words().forEach(w -> REGISTERED.putIfAbsent(singular(w), t.id())));
    }

    private Glossary() {
    }

    static List<Term> terms() {
        return TERMS;
    }

    static Set<String> commonWords() {
        return COMMON_WORDS;
    }

    static Set<String> standardActions() {
        return STANDARD_ACTIONS;
    }

    static List<String> problems() {
        return PROBLEMS;
    }

    /** "approved", the id of the proposed term that covers the word, or null when the word is not registered. */
    static String status(String word) {
        return REGISTERED.get(singular(word.toLowerCase(Locale.ROOT)));
    }

    static boolean isRegistered(String word) {
        return status(word) != null;
    }

    static List<String> unregisteredWords(String identifier) {
        return split(identifier).stream().filter(w -> !isRegistered(w)).distinct().toList();
    }

    static String registerHint(List<String> words) {
        return "unregistered word(s) " + words + ": add a term to " + FILE + " (status: proposed is enough, ADR-0018) or use the glossary word";
    }

    private static void load() throws IOException {
        List<Path> files = new ArrayList<>(List.of(FILE));
        if (Files.exists(OFFICIAL)) {
            files.add(OFFICIAL);
        }
        if (Files.isDirectory(PROPOSED_DIR)) {
            try (var stream = Files.list(PROPOSED_DIR)) {
                stream.filter(f -> f.toString().endsWith(".yaml")).sorted().forEach(files::add);
            }
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Path file : files) {
            load(file, ids);
        }
        if (!Files.exists(CONTEXTS)) {
            PROBLEMS.add(CONTEXTS + ": missing");
        } else {
            loadContexts();
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadContexts() throws IOException {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(CONTEXTS)) {
            root = new Yaml().load(in);
        }
        for (Map<String, Object> raw : (List<Map<String, Object>>) root.getOrDefault("bcs", List.of())) {
            Object id = raw.get("id");
            // the segments of every context id (bc, subdomain, kernel) are registered words
            for (String part : String.valueOf(id).split("\\.")) {
                COMMON_WORDS.add(part.toLowerCase(Locale.ROOT));
            }
            if ("system".equals(raw.get("kind"))) {
                if (raw.get("en") == null) {
                    PROBLEMS.add(CONTEXTS + " " + id + ": system entry needs en");
                    continue;
                }
                String en = String.valueOf(raw.get("en"));
                String status = String.valueOf(raw.getOrDefault("status", "approved"));
                TERMS.add(new Term("bcs:" + id, String.valueOf(raw.get("zh")), en, String.join("_", split(en)), status, Map.of(), "system", null));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void load(Path file, Set<String> ids) throws IOException {
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(file)) {
            root = new Yaml().load(in);
        }
        ((List<Object>) root.getOrDefault("common_words", List.of())).forEach(w -> COMMON_WORDS.add(String.valueOf(w).toLowerCase(Locale.ROOT)));
        ((List<Object>) root.getOrDefault("standard_actions", List.of())).forEach(w -> STANDARD_ACTIONS.add(String.valueOf(w).toLowerCase(Locale.ROOT)));
        for (Map<String, Object> raw : (List<Map<String, Object>>) root.getOrDefault("terms", List.of())) {
            String id = String.valueOf(raw.get("id"));
            for (String field : FIELDS) {
                if (!raw.containsKey(field)) {
                    PROBLEMS.add(id + ": missing field " + field);
                }
            }
            for (String list : List.of("standard_ref", "aliases", "forbidden")) {
                if (raw.containsKey(list) && !(raw.get(list) instanceof List)) {
                    PROBLEMS.add(id + ": " + list + " must be a list");
                }
            }
            if (!ids.add(id)) {
                PROBLEMS.add(id + ": duplicate id");
            }
            String en = String.valueOf(raw.get("en"));
            String snake = String.valueOf(raw.get("snake"));
            String status = String.valueOf(raw.get("status"));
            if (!STATUSES.contains(status)) {
                PROBLEMS.add(id + ": status must be approved, proposed or deprecated, was " + status);
            }
            if (!String.join("_", split(en)).equals(snake)) {
                PROBLEMS.add(id + ": snake '" + snake + "' must be the snake_case of en '" + en + "'");
            }
            Map<String, List<String>> forbidden = new LinkedHashMap<>();
            Object rawForbidden = raw.getOrDefault("forbidden", List.of());
            if (rawForbidden instanceof List<?> list) {
                list.forEach(f -> {
                    if (!split(String.valueOf(f)).isEmpty()) {
                        forbidden.put(String.valueOf(f), split(String.valueOf(f)));
                    }
                });
            }
            TERMS.add(new Term(id, String.valueOf(raw.get("zh")), en, snake, status, forbidden, String.valueOf(raw.get("kind")), String.valueOf(raw.get("bc"))));
            for (Map<String, Object> attr : (List<Map<String, Object>>) raw.getOrDefault("attributes", List.of())) {
                if (attr.get("en") == null || attr.get("snake") == null || attr.get("zh") == null) {
                    PROBLEMS.add(id + ": attribute needs zh, en and snake");
                    continue;
                }
                String attrEn = String.valueOf(attr.get("en"));
                TERMS.add(new Term(id + "." + attr.get("snake"), String.valueOf(attr.get("zh")), attrEn, String.valueOf(attr.get("snake")), status, Map.of(), "value_object", String.valueOf(raw.get("bc"))));
            }
        }
    }

    static List<String> split(String identifier) {
        String spaced = identifier.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
        return Arrays.stream(spaced.split("[^A-Za-z0-9]+|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])"))
                .map(w -> w.toLowerCase(Locale.ROOT))
                .filter(w -> !w.isEmpty() && !w.chars().allMatch(Character::isDigit))
                .toList();
    }

    static String singular(String word) {
        if (word.endsWith("ies") && word.length() > 4) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.endsWith("sses") || word.endsWith("xes") || word.endsWith("ches") || word.endsWith("shes")) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && word.length() > 3) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
