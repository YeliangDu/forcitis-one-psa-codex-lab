package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.web.IdempotencyScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR-0043 section 4 (DATA-REPAIR-timesheet-audit TB-02): an answer is stored under an Idempotency-Key only for the listed POSTs, and the answer of each carries no direct identifier. */
class IdempotentEndpointGuardTest {
    static final Path LIST = Path.of("architecture/idempotent-endpoints.txt");
    static final Path CLASSIFICATION = Path.of("architecture/data-classification.txt");
    static final Set<String> DIRECT_IDENTIFIERS = Set.of("display_name", "email", "wecom_user_id", "employee_code", "login_name", "source_address", "recipient_codes");
    static final Pattern PERSONAL = Pattern.compile("\\bPI\\b");

    /** template -> comment, in file order. */
    static Map<String, String> listed(List<String> lines) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] halves = line.split("#", 2);
            String[] parts = halves[0].strip().split("\\s+");
            assertThat(parts).as("METHOD path: " + line).hasSize(2);
            assertThat(parts[0]).as(line).isEqualTo("POST");
            assertThat(halves.length).as("the comment names the answer: " + line).isEqualTo(2);
            assertThat(rows.put(parts[1], halves[1].strip())).as("two lines for " + parts[1]).isNull();
        }
        return rows;
    }

    /** Names of the columns that are SENSITIVE or RESTRICTED or marked PI in the third segment, plus the direct identifiers; snake_case. */
    static Set<String> forbiddenNames(List<String> classificationLines) {
        Set<String> names = new LinkedHashSet<>(DIRECT_IDENTIFIERS);
        for (String line : classificationLines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", 3);
            String level = parts[1].strip();
            if (level.equals("SENSITIVE") || level.equals("RESTRICTED") || PERSONAL.matcher(parts[2]).find()) {
                String column = parts[0].strip();
                names.add(column.substring(column.lastIndexOf('.') + 1));
            }
        }
        return names;
    }

    static List<String> forbiddenIn(Set<Class<?>> types, Set<String> forbidden) {
        return IdempotentEndpointScan.componentNames(types).stream().filter(name -> forbidden.contains(IdempotentEndpointScan.snake(name))).toList();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the POSTs that accept an Idempotency-Key are exactly the lines of idempotent-endpoints.txt, one handler per line")
    void listedEndpointsAreTheEndpointsThatApply() throws Exception {
        Map<String, String> rows = listed(Files.readAllLines(LIST));
        IdempotencyScope scope = new IdempotencyScope();
        Set<String> applying = new LinkedHashSet<>();
        for (IdempotentEndpointScan.Handler handler : IdempotentEndpointScan.postHandlers()) {
            if (scope.applies("POST", handler.template())) {
                assertThat(applying.add(handler.template())).as("one handler for " + handler.template()).isTrue();
            }
        }
        assertThat(applying).as("a line without a POST handler, or a POST handler the list covers twice").containsExactlyInAnyOrderElementsOf(rows.keySet());
        assertThat(rows).hasSize(20);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: no answer type of a listed endpoint, nested records included, has a component named like a sensitive, personal or direct-identifier column")
    void answersCarryNoDirectIdentifier() throws Exception {
        Set<String> forbidden = forbiddenNames(Files.readAllLines(CLASSIFICATION));
        IdempotencyScope scope = new IdempotencyScope();
        List<String> problems = new ArrayList<>();
        for (IdempotentEndpointScan.Handler handler : IdempotentEndpointScan.postHandlers()) {
            if (scope.applies("POST", handler.template())) {
                List<String> found = forbiddenIn(IdempotentEndpointScan.answerTypes(handler), forbidden);
                if (!found.isEmpty()) {
                    problems.add(handler.template() + " answers with " + found);
                }
            }
        }
        assertThat(problems).as("take the endpoint off idempotent-endpoints.txt, or leave the field out of the answer").isEmpty();
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the comment of each line names every answer record type of its handler")
    void commentsNameTheAnswerTypes() throws Exception {
        Map<String, String> rows = listed(Files.readAllLines(LIST));
        List<String> problems = new ArrayList<>();
        for (IdempotentEndpointScan.Handler handler : IdempotentEndpointScan.postHandlers()) {
            String comment = rows.get(handler.template());
            if (comment == null) {
                continue;
            }
            for (Class<?> type : IdempotentEndpointScan.answerTypes(handler)) {
                if (!comment.contains(type.getSimpleName())) {
                    problems.add(handler.template() + " answers with " + type.getSimpleName() + ", the comment does not name it");
                }
            }
            if (handler.method().getReturnType().equals(void.class) || IdempotentEndpointScan.answerTypes(handler).isEmpty()) {
                assertThat(comment).as(handler.template() + " has no answer body").containsAnyOf("204", "without a body");
            }
        }
        assertThat(problems).isEmpty();
    }

    record Clean(String id, String status) {
    }

    record Leaf(String loginName) {
    }

    record Nested(String id, java.util.List<Leaf> people) {
    }

    record Mail(String email, String sourceId) {
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: a direct identifier, a nested one and a PI-marked column name are caught; a clean record passes")
    void forbiddenComponentsAreCaught() {
        Set<String> forbidden = forbiddenNames(List.of("a.b.c | INTERNAL | x", "organization_workforce.employees.email | SENSITIVE | 个人信息",
                "platform_integration.source_references.source_id | INTERNAL | 用途：x；个人信息 PI", "platform_idempotency.idempotent_responses.response_text | INTERNAL | 用途：y"));

        assertThat(forbiddenIn(Set.of(Clean.class), forbidden)).isEmpty();
        assertThat(forbiddenIn(Set.of(Leaf.class), forbidden)).containsExactly("loginName");
        assertThat(forbiddenIn(Set.of(Nested.class), forbidden)).as("opened up through the list").containsExactly("loginName");
        assertThat(forbiddenIn(Set.of(Mail.class), forbidden)).containsExactly("email", "sourceId");
        assertThat(forbidden).doesNotContain("c", "response_text");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: only the listed method and path templates apply, in the contract form and with real ids; other methods, paths and sessions do not")
    void listLinesAreChecked() {
        IdempotencyScope scope = new IdempotencyScope(List.of("POST /api/v1/x/{id}:go  # Y"));

        assertThat(scope.applies("POST", "/api/v1/x/0192f8a0-0000-7000-8000-000000000001:go")).isTrue();
        assertThat(scope.applies("POST", "/api/v1/x/{id}:go")).as("a template of the contract").isTrue();
        assertThat(scope.applies("PUT", "/api/v1/x/1:go")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/y/1:go")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/sessions:logout")).isFalse();
        assertThat(listed(List.of("# c", "", "POST /a/{id}  # R"))).containsOnlyKeys("/a/{id}");
    }
}
