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
        assertThat(problems).isEmpty();
        assertThat(allowed).as("an exception that is no longer needed must be deleted from " + EXCEPTIONS).isEqualTo(found);
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
