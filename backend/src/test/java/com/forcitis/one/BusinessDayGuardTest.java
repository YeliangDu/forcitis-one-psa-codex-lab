package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests run against database triggers that read the real clock, so a constant that stands for "today" or "now" must come
 * from {@link BusinessDay}, not from a calendar date typed into the file (that is a time bomb the day it falls into the past).
 * Fixed dates stay fine for historical facts and for pure unit tests that inject their own Clock; only constants named TODAY or NOW*
 * in database-backed test classes (IT, IntegrationTest, Support) are checked.
 */
class BusinessDayGuardTest {
    static final Path TESTS = Path.of("src/test/java");
    static final Pattern FIXED_TODAY = Pattern.compile(
            "\\b(TODAY|NOW)\\w*\\s*=\\s*(?:\\w+\\s*\\.\\s*)*(LocalDate\\s*\\.\\s*(of|parse)|Instant\\s*\\.\\s*(parse|ofEpoch\\w*)|OffsetDateTime\\s*\\.\\s*(of|parse)|ZonedDateTime\\s*\\.\\s*(of|parse))\\b");

    static boolean databaseBacked(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith("IT.java") || name.endsWith("IntegrationTest.java") || name.endsWith("Support.java");
    }

    static List<String> offenders(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).filter(BusinessDayGuardTest::databaseBacked).sorted()
                    .flatMap(p -> {
                        try {
                            List<String> lines = Files.readAllLines(p);
                            return Stream.iterate(0, i -> i + 1).limit(lines.size())
                                    .filter(i -> FIXED_TODAY.matcher(lines.get(i)).find())
                                    .map(i -> p + ":" + (i + 1) + "  " + lines.get(i).strip());
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
        }
    }

    @Test
    @DisplayName("no database-backed test class declares TODAY or NOW as a typed-in calendar date; use BusinessDay.today() / BusinessDay.tenOClock()")
    void todayComesFromTheBusinessDay() throws IOException {
        assertThat(offenders(TESTS)).as("TODAY/NOW constants typed in as fixed dates (they go stale when the real date passes them; take them from BusinessDay)").isEmpty();
    }

    @Test
    @DisplayName("injection: every way of typing a fixed today or now is caught, relative and business-day forms are not")
    void theFixedFormsAreCaught() {
        for (String bad : List.of(
                "static final LocalDate TODAY = LocalDate.of(2026, 11, 2);",
                "private static final LocalDate TODAY = LocalDate.parse(\"2026-12-01\");",
                "static final Instant NOW = Instant.parse(\"2026-11-02T02:00:00Z\");",
                "static final Instant NOW_LATER = Instant.ofEpochSecond(1_000);",
                "static final ZonedDateTime NOW = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZONE);")) {
            assertThat(FIXED_TODAY.matcher(bad).find()).as(bad).isTrue();
        }
        for (String good : List.of(
                "static final LocalDate TODAY = BusinessDay.today();",
                "static final Instant NOW = BusinessDay.tenOClock();",
                "static final LocalDate HIRED = LocalDate.of(2026, 1, 1);",
                "static final LocalDate TODAY_PLUS = TODAY.plusDays(3);")) {
            assertThat(FIXED_TODAY.matcher(good).find()).as(good).isFalse();
        }
    }

    @Test
    @DisplayName("injection: the scan finds an offending file in a database-backed class and ignores the same line in a plain unit test")
    void theScanLooksAtDatabaseBackedClassesOnly(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("SomeIT.java"), "class SomeIT {\n    static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 1, 1);\n}\n");
        Files.writeString(dir.resolve("SomeTest.java"), "class SomeTest {\n    static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 1, 1);\n}\n");

        List<String> found = offenders(dir);

        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("SomeIT.java:2");
    }
}
