package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Every event in the catalogue is sealed in some test, so CatalogueBusinessDates (wired into every envelope factory of the tests)
 * has checked its businessDate at least once. A test sealing the event names its record type.
 */
class EventCatalogueCoverageTest {

    @Test
    @DisplayName("every catalogue event is constructed in at least one test, so its businessDate declaration is exercised")
    @SuppressWarnings("unchecked")
    void everyCatalogueEventIsExercised() throws IOException {
        String tests;
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            tests = files.filter(file -> file.toString().endsWith(".java")).map(file -> {
                try {
                    return Files.readString(file);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).reduce("", String::concat);
        }
        List<String> unexercised = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("../docs/architecture/events"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml") && !f.getFileName().toString().equals("asyncapi.yaml")
                    && !f.getFileName().toString().startsWith("_")).toList()) {
                try (InputStream in = Files.newInputStream(file)) {
                    Map<String, Object> document = new Yaml().load(in);
                    Map<String, Object> messages = (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("messages");
                    messages.keySet().stream().filter(name -> !tests.contains(name)).forEach(name -> unexercised.add(file.getFileName() + ": " + name));
                }
            }
        }
        assertThat(unexercised).as(GuardMessage.of("CS-2.5", "a catalogue event is sealed by no test, so its businessDate declaration is never checked", "construct the event in its publisher test through an envelope factory that calls CatalogueBusinessDates, naming the event record type")).isEmpty();
    }
}
