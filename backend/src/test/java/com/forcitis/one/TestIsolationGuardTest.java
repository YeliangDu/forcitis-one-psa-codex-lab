package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Review H-7 (guard 7): a module's tests prepare and check only their own schema. What another module did is asserted by that
 * module's tests, or by the events it published; its data is prepared through its api or with a double.
 */
class TestIsolationGuardTest {
    static final Path TESTS = Path.of("src/test/java/com/forcitis/one");
    static final Path EXCEPTIONS = Path.of("architecture/test-isolation-exceptions.txt");
    static final Pattern STRING = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);
    static final Pattern SCHEMA_TABLE = Pattern.compile("\\b((?:organization|platform|cost|delivery)_[a-z]+)\\.(?!properties\\b)[a-z_]+\\b");

    static String schemaOf(Path file) {
        Path relative = TESTS.relativize(file);
        if (relative.getNameCount() < 3) {
            return null;
        }
        if (relative.getName(0).toString().equals("acceptance")) {
            return relative.getNameCount() < 4 ? "acceptance" : relative.getName(1) + "_" + relative.getName(2);
        }
        return relative.getName(0) + "_" + relative.getName(1);
    }

    static List<String> foreignSchemas(String ownSchema, String source) {
        List<String> found = new ArrayList<>();
        Matcher string = STRING.matcher(StateWritePolicy.withoutComments(source));
        while (string.find()) {
            String sql = string.group(1) != null ? string.group(1) : string.group(2);
            Matcher reference = SCHEMA_TABLE.matcher(sql);
            while (reference.find()) {
                if (!reference.group(1).equals(ownSchema) && !DdlNamingTest.FRAMEWORK_TABLES.contains(reference.group())) {
                    found.add(reference.group());
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("SQL in a module's tests names only that module's schema")
    void testsStayInTheirOwnSchema() throws IOException {
        Set<String> allowed = new LinkedHashSet<>();
        for (String line : Files.readAllLines(EXCEPTIONS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                allowed.add(line.split("  #")[0].trim());
            }
        }
        Set<String> found = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(TESTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String own = schemaOf(file);
                if (own == null) {
                    continue;
                }
                String name = TESTS.relativize(file).toString();
                for (String reference : foreignSchemas(own, Files.readString(file))) {
                    String key = name + "#" + reference.substring(0, reference.indexOf('.'));
                    found.add(key);
                    if (!allowed.contains(key)) {
                        problems.add(name + ": SQL touches " + reference + ", a schema of another module; use its api or a double");
                    }
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-5.5", "a test of a module names the schema of another module in SQL", "prepare and check only the own schema; use the api of the other module or a double, or register the file#schema key in " + EXCEPTIONS + " with a reason")).isEmpty();
        assertThat(allowed).as(GuardMessage.of("CS-5.5", "an exception of " + EXCEPTIONS + " is no longer needed", "delete the stale line from " + EXCEPTIONS)).isEqualTo(found);
    }

    @Test
    @DisplayName("an acceptance test belongs to the module of its directory, not to a module called acceptance")
    void acceptanceTestsBelongToTheirModule() {
        assertThat(schemaOf(TESTS.resolve("acceptance/platform/audit/AuditSubjectColumnsIT.java"))).isEqualTo("platform_audit");
        assertThat(schemaOf(TESTS.resolve("platform/audit/AuditLogIT.java"))).isEqualTo("platform_audit");
        assertThat(schemaOf(TESTS.resolve("acceptance/P5Names.java"))).isNull();
        assertThat(schemaOf(TESTS.resolve("acceptance/platform/Loose.java"))).as("a file straight under an area is still checked, against no schema").isEqualTo("acceptance");
    }

    @Test
    @DisplayName("injected: another module's schema in a SQL string is caught, a comment and the module's own schema are not")
    void foreignSchemaIsCaught() {
        String source = "// platform_numbering.numbering_counters in a comment\n"
                + "jdbc.sql(\"truncate table platform_numbering.numbering_counters\"); jdbc.sql(\"select 1 from organization_workforce.employees\");";
        assertThat(foreignSchemas("organization_workforce", source)).containsExactly("platform_numbering.numbering_counters");
        assertThat(foreignSchemas("platform_numbering", source)).containsExactly("organization_workforce.employees");
    }
}
