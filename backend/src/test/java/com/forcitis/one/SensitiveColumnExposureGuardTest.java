package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NAMING_STANDARD section 2.5: a web or api response does not carry a column classified SENSITIVE or RESTRICTED in
 * architecture/data-classification.txt. A response field is taken to carry such a column when its snake_case name is the column's name.
 * A field that must be sent anyway is registered in architecture/sensitive-response-exceptions.txt with the reason.
 */
class SensitiveColumnExposureGuardTest {
    static final Path CLASSIFICATION = Path.of("architecture/data-classification.txt");
    static final Path EXCEPTIONS = Path.of("architecture/sensitive-response-exceptions.txt");

    static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    static Map<String, List<String>> sensitiveColumns() throws IOException {
        return sensitiveColumns(ClassificationRegister.read());
    }

    /** column name -> every "schema.table.column" with that name at level SENSITIVE or RESTRICTED; only the access level decides, the mark does not (ADR-0043 guard rule 6). */
    static Map<String, List<String>> sensitiveColumns(Map<String, ClassificationRegister.Row> register) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        register.forEach((qualified, row) -> {
            if (row.atLeast("SENSITIVE")) {
                columns.computeIfAbsent(qualified.substring(qualified.lastIndexOf('.') + 1), k -> new ArrayList<>()).add(qualified);
            }
        });
        return columns;
    }

    static boolean isResponse(Class<?> type) {
        return type.isRecord() && type.getSimpleName().endsWith("Response");
    }

    /** "fully.qualified.Response#field" for every response field named like a sensitive column. */
    static Set<String> exposures(Iterable<Class<?>> classes, Map<String, List<String>> sensitive) {
        Set<String> found = new TreeSet<>();
        for (Class<?> type : classes) {
            if (isResponse(type)) {
                for (RecordComponent component : type.getRecordComponents()) {
                    if (sensitive.containsKey(snake(component.getName()))) {
                        found.add(type.getName() + "#" + component.getName());
                    }
                }
            }
        }
        return found;
    }

    static Set<String> registered() throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        for (String line : Files.readAllLines(EXCEPTIONS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                assertThat(line).as(GuardMessage.of("CS-2.13", "line '" + line + "' in the sensitive response exceptions register has no reason",
                        "write each line in " + EXCEPTIONS + " as <Response class>#<field>  # <reason>")).contains("  # ");
                entries.add(line.substring(0, line.indexOf("  #")).trim());
            }
        }
        return entries;
    }

    @Test
    @DisplayName("no response record has a field named like a SENSITIVE or RESTRICTED column, except the registered ones, and every registered one is still needed")
    void responsesDoNotCarrySensitiveColumns() throws IOException {
        List<Class<?>> responses = ArchitectureRulesTest.CLASSES.stream().<Class<?>>map(JavaClass::reflect).toList();
        Set<String> found = exposures(responses, sensitiveColumns());
        Set<String> registered = registered();

        List<String> problems = new ArrayList<>();
        found.stream().filter(f -> !registered.contains(f)).forEach(f -> problems.add(f + " sends a classified column: drop it, or register it in " + EXCEPTIONS + " with the reason"));
        registered.stream().filter(r -> !found.contains(r)).forEach(r -> problems.add(r + " is registered but is gone or no longer matches: remove it from " + EXCEPTIONS));
        assertThat(problems).as(GuardMessage.of("CS-2.13",
                "a web or api *Response record has a field named like a SENSITIVE or RESTRICTED column (architecture/data-classification.txt), or a registered exception is stale",
                "remove the field from the response, or register it in " + EXCEPTIONS + " with the reason it must be sent; for a stale line delete it")).isEmpty();
    }

    record LeakyResponse(String id, String passwordHash, String lastLoginAddress, String accountName, String idNumber, String email) {
    }

    record HarmlessResponse(String id, String title) {
    }

    record NotAResponseRecord(String email) {
    }

    /** Fixed table: the injection keeps its strength whatever the real register says (ADR-0043). */
    static final Map<String, ClassificationRegister.Row> FIXED = Map.of(
            "m.t.password_hash", new ClassificationRegister.Row("RESTRICTED", "NONE", "credential"),
            "m.t.last_login_address", new ClassificationRegister.Row("SENSITIVE", "PI", "用途：x"),
            "m.t.account_name", new ClassificationRegister.Row("SENSITIVE", "NONE", "x"),
            "m.t.id_number", new ClassificationRegister.Row("SENSITIVE", "SPI", "用途：x docs/privacy/x.md"),
            "m.t.email", new ClassificationRegister.Row("INTERNAL", "PI", "用途：x"));

    @Test
    @DisplayName("injection: fields named like SENSITIVE or RESTRICTED columns (any mark) are found in any *Response record, camelCase or not; an INTERNAL personal column and other records are left alone")
    void scannerFindsClassifiedFields() {
        Set<String> found = exposures(List.of(LeakyResponse.class, HarmlessResponse.class, NotAResponseRecord.class), sensitiveColumns(FIXED));

        String leaky = LeakyResponse.class.getName();
        assertThat(found).containsExactlyInAnyOrder(leaky + "#passwordHash", leaky + "#lastLoginAddress", leaky + "#accountName", leaky + "#idNumber");
    }

    @Test
    @DisplayName("the classification lists sensitive columns to guard against, and an INTERNAL personal column is not among them")
    void classificationIsRead() throws IOException {
        assertThat(sensitiveColumns()).as(GuardMessage.of("CS-2.13",
                "architecture/data-classification.txt no longer lists password_hash, last_login_address and login_name as SENSITIVE or RESTRICTED",
                "restore those classifications in " + CLASSIFICATION)).containsKeys("password_hash", "last_login_address", "login_name").doesNotContainKey("email");
    }
}
