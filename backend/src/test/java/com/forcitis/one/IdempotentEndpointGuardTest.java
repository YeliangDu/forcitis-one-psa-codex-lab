package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    static final Set<String> DIRECT_IDENTIFIERS = Set.of("display_name", "email", "wecom_user_id", "employee_code", "login_name", "source_address", "recipient_codes",
            "ip", "ip_address", "user_agent", "mobile", "phone");
    /** A component named like a person's name is refused; these two are the names of things (a legal entity, a role), not of people. */
    static final Set<String> NAMES_OF_THINGS = Set.of("name", "code");
    static final Pattern NAME_LIKE = Pattern.compile("(?:.+_)?name");
    /** A component of one of these types holds a person's name or contact data whatever it is called. */
    static final Set<String> PERSON_TYPES = Set.of("WecomUserId", "LoginName", "EmployeeProfile", "Employee", "SourcePerson");
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

    /** What in the answer is refused: a component by name (Java and JSON), a person type, and any type the guard cannot open up (that is a failure, not a pass). */
    static List<String> forbiddenIn(Set<Class<?>> types, Set<String> forbidden) {
        IdempotentEndpointScan.Reach reach = IdempotentEndpointScan.reach(types);
        List<String> found = new ArrayList<>();
        reach.names().stream().filter(name -> {
            String snake = IdempotentEndpointScan.snake(name);
            return forbidden.contains(snake) || NAME_LIKE.matcher(snake).matches() && !NAMES_OF_THINGS.contains(snake);
        }).forEach(found::add);
        reach.typeNames().stream().filter(PERSON_TYPES::contains).forEach(found::add);
        reach.unsupported().forEach(type -> found.add("cannot open up " + type));
        return found;
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: the POSTs of the code that the list names are exactly the lines of idempotent-endpoints.txt, one handler per line")
    void listedEndpointsAreTheEndpointsThatApply() throws Exception {
        Map<String, String> rows = listed(Files.readAllLines(LIST));
        Set<String> applying = new LinkedHashSet<>();
        for (IdempotentEndpointScan.Handler handler : IdempotentEndpointScan.postHandlers()) {
            if (rows.containsKey(handler.template())) {
                assertThat(applying.add(handler.template())).as("one handler for " + handler.template()).isTrue();
            }
        }
        assertThat(applying).as("a line without a POST handler, or a POST handler the list covers twice").containsExactlyInAnyOrderElementsOf(rows.keySet());
        assertThat(rows).hasSize(21);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: no answer type of a listed endpoint, nested records included, has a component named like a sensitive, personal or direct-identifier column")
    void answersCarryNoDirectIdentifier() throws Exception {
        Set<String> forbidden = forbiddenNames(Files.readAllLines(CLASSIFICATION));
        Set<String> listed = listed(Files.readAllLines(LIST)).keySet();
        List<String> problems = new ArrayList<>();
        for (IdempotentEndpointScan.Handler handler : IdempotentEndpointScan.postHandlers()) {
            if (listed.contains(handler.template())) {
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

    record Titled(String id, String operatorName) {
    }

    record Renamed(@com.fasterxml.jackson.annotation.JsonProperty("login_name") String label) {
    }

    record Named(String name, String code) {
    }

    record Holder(com.forcitis.one.kernel.WecomUserId who) {
    }

    record Many(Leaf[] people) {
    }

    record Contact(String mobile, String ip, String userAgent) {
    }

    sealed interface Either permits Fine, Leaky {
    }

    record Fine(String id) implements Either {
    }

    record Leaky(String loginName) implements Either {
    }

    record Wrap(Either either) {
    }

    record Loose(java.util.Map<String, String> bag) {
    }

    record Plain(Object any) {
    }

    record Boxed(tools.jackson.databind.JsonNode node) {
    }

    static class Pojo {
        public String x;
    }

    record Pojoed(Pojo pojo) {
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
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: arrays, sealed alternatives, renamed properties, name-like components, contact data and person types are caught; the name or code of a thing is not")
    void moreFormsAreCaught() {
        Set<String> forbidden = forbiddenNames(List.of());

        assertThat(forbiddenIn(Set.of(Titled.class), forbidden)).containsExactly("operatorName");
        assertThat(forbiddenIn(Set.of(Renamed.class), forbidden)).as("by the JSON name").containsExactly("login_name");
        assertThat(forbiddenIn(Set.of(Named.class), forbidden)).as("name and code are names of things").isEmpty();
        assertThat(forbiddenIn(Set.of(Holder.class), forbidden)).as("by type, whatever the component is called").containsExactly("WecomUserId");
        assertThat(forbiddenIn(Set.of(Many.class), forbidden)).as("through an array").containsExactly("loginName");
        assertThat(forbiddenIn(Set.of(Contact.class), forbidden)).containsExactlyInAnyOrder("mobile", "ip", "userAgent");
        assertThat(forbiddenIn(Set.of(Wrap.class), forbidden)).as("through the alternatives of a sealed interface").containsExactly("loginName");
    }

    static class Page<T> {
        public List<T> items;
    }

    record Paged(Page<Clean> page) {
    }

    private static final String WILDCARD_SOURCE = "ResponseEntity<?> probe() { return ResponseEntity.ok().body(new %s(\"x\")); }";

    private static IdempotentEndpointScan.Handler probeHandler() throws NoSuchMethodException {
        return new IdempotentEndpointScan.Handler("/probe", IdempotentEndpointGuardTest.class.getDeclaredMethod("probe"), IdempotentEndpointGuardTest.class);
    }

    void probe() {
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: a ResponseEntity of a wildcard is opened up by the record its source names; a named class that is no record fails")
    void wildcardAnswersAreOpenedUpOrFail() throws Exception {
        Set<Class<?>> types = new LinkedHashSet<>();
        IdempotentEndpointScan.wildcardRecords(probeHandler(), String.format(WILDCARD_SOURCE, "ProbeLeaky"), "probe", types);
        assertThat(forbiddenIn(types, forbiddenNames(List.of()))).as("the record named in .body(...)").containsExactly("loginName");

        assertThatThrownBy(() -> IdempotentEndpointScan.wildcardRecords(probeHandler(), String.format(WILDCARD_SOURCE, "ProbePlain"), "probe", new LinkedHashSet<>()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("neither a record nor a sealed interface");
        assertThatThrownBy(() -> IdempotentEndpointScan.wildcardRecords(probeHandler(), "ResponseEntity<?> probe() { return ResponseEntity.ok().build(); }", "probe", new LinkedHashSet<>()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("names no record");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: a generic class that is no record, as the answer or inside one, is a failure")
    void genericClassesFail() throws Exception {
        assertThat(forbiddenIn(Set.of(Paged.class), forbiddenNames(List.of()))).anyMatch(found -> found.startsWith("cannot open up"));
        IdempotentEndpointScan.Handler handler = new IdempotentEndpointScan.Handler("/page", IdempotentEndpointGuardTest.class.getDeclaredMethod("pageOf"), IdempotentEndpointGuardTest.class);
        assertThatThrownBy(() -> IdempotentEndpointScan.answerTypes(handler)).isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot open up");
    }

    Page<Clean> pageOf() {
        return null;
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02 injected: a map, an Object, a JSON tree or a plain class in an answer is a failure, not a pass")
    void typesThatCannotBeOpenedUpFail() {
        Set<String> forbidden = forbiddenNames(List.of());

        for (Class<?> opaque : List.of(Loose.class, Plain.class, Boxed.class, Pojoed.class)) {
            assertThat(forbiddenIn(Set.of(opaque), forbidden)).as(opaque.getSimpleName()).anyMatch(found -> found.startsWith("cannot open up"));
        }
    }
}
