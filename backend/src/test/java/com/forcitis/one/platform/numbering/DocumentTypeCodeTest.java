package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.DocumentType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DocumentTypeCodeTest {
    static final Path OFFICIAL = Path.of("../docs/modernization/glossary/terms.yaml");
    static final Path PROPOSED = Path.of("glossary/proposed/platform-numbering.yaml");

    @Test
    @DisplayName("Phase 1a registers the codes QT, TS and JV")
    void everyNumberedTypeHasACode() {
        Map<DocumentType, String> registered = Arrays.stream(DocumentType.values()).filter(t -> t.code().isPresent())
                .collect(Collectors.toMap(t -> t, t -> t.code().orElseThrow()));

        assertThat(registered).containsOnly(Map.entry(DocumentType.QUOTE, "QT"), Map.entry(DocumentType.TIMESHEET, "TS"),
                Map.entry(DocumentType.JOURNAL_VOUCHER, "JV"));
    }

    @Test
    void codesAreShortCapitalLettersAndUnique() {
        List<String> codes = Arrays.stream(DocumentType.values()).flatMap(t -> t.code().stream()).toList();

        assertThat(codes).allMatch(c -> c.matches("[A-Z]{2,4}")).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every registered code is a glossary word")
    void everyCodeIsAGlossaryWord() throws IOException {
        Set<String> snakes = new java.util.HashSet<>();
        for (Path file : List.of(OFFICIAL, PROPOSED)) {
            if (Files.exists(file)) {
                snakes.addAll(snakeNames(file));
            }
        }
        List<String> unregistered = new ArrayList<>();
        for (DocumentType type : DocumentType.values()) {
            type.code().filter(c -> !snakes.contains(c.toLowerCase())).ifPresent(c -> unregistered.add(type + " -> " + c));
        }

        assertThat(unregistered).as("codes without a glossary term (add one to " + PROPOSED + ")").isEmpty();
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
