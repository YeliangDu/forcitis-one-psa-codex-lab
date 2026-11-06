package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DocumentTypeCodeTest {
    static final Path OFFICIAL = Path.of("../docs/glossary/terms.yaml");
    static final Path PROPOSED = Path.of("glossary/proposed/platform-numbering.yaml");

    static final Path MIGRATION = Path.of("src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql");
    private static final Pattern SEED = Pattern.compile("\\('[0-9a-f-]{36}', '([A-Z][A-Z0-9_]+)', '([A-Z]+)', '[a-z.]+', (?:true|false), DATE");

    @Test
    @DisplayName("the baseline registers the number tokens QT, TS, JV, EMP and BI")
    void everyNumberedTypeHasATokenInTheSeed() throws IOException {
        Map<String, String> registered = seededTokens();

        assertThat(registered).containsOnly(Map.entry("QUOTE", "QT"), Map.entry("TIMESHEET", "TS"), Map.entry("JOURNAL_VOUCHER", "JV"), Map.entry("EMPLOYEE", "EMP"), Map.entry("BONUS_INPUT", "BI"));
    }

    @Test
    void tokensAreShortCapitalLettersAndUnique() throws IOException {
        List<String> tokens = new ArrayList<>(seededTokens().values());

        assertThat(tokens).allMatch(c -> c.matches("[A-Z]{2,4}")).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every registered number token is a glossary word")
    void everyTokenIsAGlossaryWord() throws IOException {
        Set<String> snakes = new java.util.HashSet<>();
        for (Path file : List.of(OFFICIAL, PROPOSED)) {
            if (Files.exists(file)) {
                snakes.addAll(snakeNames(file));
            }
        }
        List<String> unregistered = new ArrayList<>();
        seededTokens().forEach((type, token) -> {
            if (!snakes.contains(token.toLowerCase())) {
                unregistered.add(type + " -> " + token);
            }
        });

        assertThat(unregistered).as("tokens without a glossary term (add one to " + PROPOSED + ")").isEmpty();
    }

    private static Map<String, String> seededTokens() throws IOException {
        Matcher seeds = SEED.matcher(Files.readString(MIGRATION));
        Map<String, String> tokens = new java.util.LinkedHashMap<>();
        while (seeds.find()) {
            tokens.put(seeds.group(1), seeds.group(2));
        }
        return tokens;
    }

    @SuppressWarnings("unchecked")
    private static List<String> snakeNames(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            Map<String, Object> root = new Yaml().load(in);
            List<Map<String, Object>> terms = (List<Map<String, Object>>) root.get("terms");
            return terms.stream().map(t -> String.valueOf(t.get("snake"))).toList();
        }
    }
}
