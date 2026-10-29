package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * D-1a-174: the actions that must leave an audit entry are listed by category in `docs/architecture/must-audit-actions.yaml`. An
 * action written in code, or an event the catalogue marks as audited, that is not on the list fails the build; so does a listed action
 * that does not exist. What is not built yet is listed as planned, with the reason.
 */
class MustAuditActionsTest {
    static final Path LIST = Path.of("../docs/architecture/must-audit-actions.yaml");
    static final Pattern LITERAL = Pattern.compile("new\\s+AuditEntry\\(\\s*\"([^\"]+)\"");

    @SuppressWarnings("unchecked")
    static Set<String> listed(boolean planned) throws IOException {
        Set<String> codes = new TreeSet<>();
        try (InputStream in = Files.newInputStream(LIST)) {
            Map<String, Object> document = new Yaml().load(in);
            for (Map<String, Object> category : (List<Map<String, Object>>) document.get("categories")) {
                for (Object entry : (List<Object>) category.get("actions")) {
                    if (entry instanceof Map<?, ?> plannedEntry) {
                        if (planned) {
                            codes.add(String.valueOf(plannedEntry.get("planned")).split("\\|")[0].trim());
                        }
                    } else if (!planned) {
                        codes.add(String.valueOf(entry));
                    }
                }
            }
        }
        return codes;
    }

    static Set<String> writtenInCode(Path root) throws IOException {
        Set<String> actions = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = LITERAL.matcher(StateWritePolicy.withoutComments(Files.readString(file)));
                while (m.find()) {
                    actions.add(m.group(1));
                }
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    static Set<String> auditedEvents() throws IOException {
        Set<String> events = new TreeSet<>();
        for (Map<String, Object> message : EventCatalogueTest.fragmentMessages().values()) {
            Map<String, Object> event = (Map<String, Object>) message.get("x-event");
            if ("audited".equals(String.valueOf(event.get("audit")))) {
                events.add(String.valueOf(event.get("eventType")));
            }
        }
        return events;
    }

    @Test
    @DisplayName("every action written to the audit trail and every audited event is on the must-audit list, and every listed action exists")
    void listAndCodeAgree() throws IOException {
        Set<String> actual = writtenInCode(Path.of("src/main/java"));
        actual.addAll(auditedEvents());
        Set<String> listed = listed(false);

        List<String> problems = new ArrayList<>();
        actual.stream().filter(a -> !listed.contains(a)).forEach(a -> problems.add(a + ": audited in code but not on " + LIST + "; add it under a category"));
        listed.stream().filter(l -> !actual.contains(l)).forEach(l -> problems.add(l + ": on " + LIST + " but nothing writes it; remove it or mark it planned"));
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("a planned action is not yet written anywhere: once it exists it must move to the real list")
    void plannedActionsDoNotExistYet() throws IOException {
        Set<String> actual = writtenInCode(Path.of("src/main/java"));
        actual.addAll(auditedEvents());
        assertThat(listed(true)).isNotEmpty().doesNotContainAnyElementsOf(actual);
    }

    @Test
    @DisplayName("injected: an action literal is found in code and a comment is not")
    void literalsAreRead() throws IOException {
        Path dir = Files.createTempDirectory("must-audit");
        Files.writeString(dir.resolve("A.java"), "class A { void f() { audit.record(new AuditEntry(\"x.y.z\", null, null, \"k\", \"d\")); } // new AuditEntry(\"ghost\"\n }");
        assertThat(writtenInCode(dir)).containsExactly("x.y.z");
    }
}
