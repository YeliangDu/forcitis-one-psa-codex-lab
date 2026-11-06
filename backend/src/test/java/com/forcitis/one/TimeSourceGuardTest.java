package com.forcitis.one;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.infrastructure.AmbientTestTimeSample;
import com.forcitis.one.fixture.infrastructure.AmbientTimeSample;
import com.forcitis.one.fixture.web.AmbientTimeWebSample;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One clock for the whole application: the instant comes from the injected Clock and the business day from the business zone, never from
 * the machine's own clock (java.time now(), Clock.system*) and never from the database's (now(), current_timestamp in a SQL string).
 */
class TimeSourceGuardTest {
    static final String CLOCK_BUILDER = "com.forcitis.one.kernel.KernelConfiguration";
    /** Spring Session creates its own table and gives no clock injection point, so expiry must be compared on the framework's own clock; drop it when the C1 session repository is ours. */
    static final String SESSION_STORE_CLOCK = "com.forcitis.one.platform.identity.infrastructure.persistence.SessionStoreClock";
    static final Path MAIN = Path.of("src/main/java");
    static final Path SQL_EXCEPTIONS = Path.of("architecture/sql-database-clock-exceptions.txt");

    static final DescribedPredicate<JavaMethodCall> ASKS_THE_MACHINE = DescribedPredicate.describe(
            "asks the machine for the time (java.time now(...), Clock.system*, System.currentTimeMillis)", call -> {
                String owner = call.getTargetOwner().getName();
                String method = call.getName();
                return (owner.startsWith("java.time.") && method.equals("now"))
                        || (owner.equals("java.time.Clock") && (method.startsWith("system") || method.startsWith("tick")))
                        || (owner.equals("java.lang.System") && method.equals("currentTimeMillis"));
            });

    /** KernelConfiguration builds the one system Clock that everything else is handed. */
    static ArchRule noAmbientTime() {
        return noClasses().that().doNotHaveFullyQualifiedName(CLOCK_BUILDER).and().doNotHaveFullyQualifiedName(SESSION_STORE_CLOCK)
                .should().callMethodWhere(ASKS_THE_MACHINE)
                .because(GuardMessage.of("G25",
                        "a class asks the machine for the time (java.time now(), Clock.system*, System.currentTimeMillis)",
                        "take the instant from the injected java.time.Clock (application passes the instant or business day into domain); only KernelConfiguration builds the system Clock"));
    }

    @Test
    @DisplayName("no class in domain, application, infrastructure, web or anywhere else asks the machine for the time, except the one that builds the Clock and the session store clock")
    void productionCodeNeverAsksTheMachine() {
        noAmbientTime().check(ArchitectureRulesTest.CLASSES);
    }

    @Test
    @DisplayName("injection: every way of asking the machine is caught, in infrastructure and web classes alike")
    void ambientTimeIsCaught() {
        JavaClasses samples = new ClassFileImporter().importClasses(AmbientTimeSample.class, AmbientTimeWebSample.class);

        List<String> details = noAmbientTime().evaluate(samples).getFailureReport().getDetails();

        assertThat(details).hasSize(10);
        assertThat(details).anyMatch(d -> d.contains("AmbientTimeWebSample"));
        for (String call : List.of("ZonedDateTime.now", "OffsetDateTime.now", "LocalDateTime.now", "LocalDate.now", "Instant.now", "Clock.systemUTC",
                "Clock.systemDefaultZone", "System.currentTimeMillis")) {
            assertThat(details).as(call).anyMatch(d -> d.contains(call));
        }
    }

    @Test
    @DisplayName("the registered exceptions exist among the scanned classes and are clean under the rule (a stale exception fails here)")
    void theExemptClassesAreClean() {
        JavaClasses exempt = new ClassFileImporter().importPackages("com.forcitis.one.kernel", SESSION_STORE_CLOCK.substring(0, SESSION_STORE_CLOCK.lastIndexOf('.')));

        assertThat(exempt.stream().map(c -> c.getName())).contains(CLOCK_BUILDER, SESSION_STORE_CLOCK);
        assertThat(noAmbientTime().evaluate(exempt).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("injection: a class that merely carries the name SessionStoreClock in another package is still caught")
    void aNamesakeIsNotExempt() {
        JavaClasses namesake = new ClassFileImporter().importPackages("com.forcitis.one.fixture.infrastructure.stray");

        List<String> details = noAmbientTime().evaluate(namesake).getFailureReport().getDetails();

        assertThat(details).anyMatch(d -> d.contains("stray.SessionStoreClock") && d.contains("currentTimeMillis"));
    }

    static final String SESSION_STORE_USER = "com.forcitis.one.platform.identity.infrastructure.persistence.JdbcExpiredSessions";

    static ArchRule onlyTheExpiredSessionsUseTheStoreClock(String clock, String user) {
        return classes().that().haveFullyQualifiedName(clock)
                .should().onlyHaveDependentClassesThat().haveFullyQualifiedName(user)
                .because(GuardMessage.of("CS-2.26",
                        "a class other than JdbcExpiredSessions depends on SessionStoreClock, which is the framework's clock for session expiry only",
                        "take the instant from the injected java.time.Clock instead of SessionStoreClock"));
    }

    @Test
    @DisplayName("only JdbcExpiredSessions depends on SessionStoreClock")
    void onlyTheExpiredSessionsUseTheStoreClock() {
        onlyTheExpiredSessionsUseTheStoreClock(SESSION_STORE_CLOCK, SESSION_STORE_USER).check(ArchitectureRulesTest.CLASSES);
    }

    @Test
    @DisplayName("injection: any other dependent of a session store clock is caught")
    void aStrayUserIsCaught() {
        JavaClasses stray = new ClassFileImporter().importPackages("com.forcitis.one.fixture.infrastructure.stray");

        List<String> details = onlyTheExpiredSessionsUseTheStoreClock("com.forcitis.one.fixture.infrastructure.stray.SessionStoreClock", SESSION_STORE_USER)
                .evaluate(stray).getFailureReport().getDetails();

        assertThat(details).anyMatch(d -> d.contains("StrayClockUser"));
    }

    // ---- the test tree: a test never asks the machine for the time either (#1586) ----

    static final Path TEST_CLOCK_EXCEPTIONS = Path.of("architecture/test-clock-exceptions.txt");
    static final String TEST_ROOT = "com.forcitis.one.";

    static final JavaClasses TEST_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages(ArchitectureRulesTest.ROOT);

    /** now() with no Clock to take the instant from (no argument, or only a zone), Clock.system*, System.currentTimeMillis; now(clock) and System.nanoTime are fine. */
    static boolean testAsksTheMachine(JavaCodeUnitAccess<?> call) {
        String owner = call.getTargetOwner().getName();
        String method = call.getName();
        boolean takesAClock = call.getTarget().getRawParameterTypes().stream().anyMatch(type -> type.getName().equals("java.time.Clock"));
        return (owner.startsWith("java.time.") && method.equals("now") && !takesAClock)
                || (owner.equals("java.time.Clock") && (method.startsWith("system") || method.startsWith("tick")))
                || (owner.equals("java.lang.System") && method.equals("currentTimeMillis"));
    }

    /** The registered path of the top-level class that holds a class, as the exceptions file spells it. */
    static String pathOf(JavaClass holder) {
        String name = holder.getName();
        int nested = name.indexOf('$');
        return (nested < 0 ? name : name.substring(0, nested)).substring(TEST_ROOT.length()).replace('.', '/') + ".java";
    }

    /** Deliberately wrong samples that only the guard tests load. */
    static boolean isSample(JavaClass holder) {
        return holder.getSimpleName().endsWith("Sample") || holder.getPackageName().contains(".stray");
    }

    /** Path of each test class holding a machine-clock reading -> what it reads, samples included (a lambda or nested class counts for its top-level class). */
    static Map<String, Set<String>> machineClockReadings(JavaClasses classes) {
        Map<String, Set<String>> found = new TreeMap<>();
        for (JavaClass holder : classes) {
            Stream.<JavaCodeUnitAccess<?>>concat(holder.getMethodCallsFromSelf().stream(), holder.getMethodReferencesFromSelf().stream())
                    .filter(TimeSourceGuardTest::testAsksTheMachine)
                    .forEach(call -> found.computeIfAbsent(pathOf(holder), k -> new TreeSet<>()).add(call.getTargetOwner().getSimpleName() + "." + call.getName()));
        }
        return found;
    }

    static Set<String> registeredTestClockExceptions() throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        for (String line : Files.readAllLines(TEST_CLOCK_EXCEPTIONS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                assertThat(line).as(GuardMessage.of("CS-3.8", "line '" + line + "' in the test clock exceptions register has no reason",
                        "write each line in backend/architecture/test-clock-exceptions.txt as <path under src/test/java/com/forcitis/one>  # <reason>")).contains("  # ");
                entries.add(line.substring(0, line.indexOf("  #")).trim());
            }
        }
        return entries;
    }

    @Test
    @DisplayName("no test class asks the machine for the time (now() without a Clock, Clock.system*, System.currentTimeMillis): MutableClock or Clock.fixed; registered exceptions only")
    void testsDoNotAskTheMachine() throws IOException {
        Set<String> exceptions = registeredTestClockExceptions();
        Map<String, Set<String>> readings = new TreeMap<>(machineClockReadings(TEST_CLASSES));
        readings.keySet().removeIf(path -> isSampleFile(path));
        List<String> problems = new ArrayList<>();
        readings.forEach((path, calls) -> {
            if (!exceptions.contains(path)) {
                problems.add(path + " asks the machine for the time (" + String.join(", ", calls) + ")");
            }
        });
        exceptions.stream().filter(e -> !readings.containsKey(e)).forEach(e -> problems.add(e + " is registered but reads no machine clock (or no longer exists): remove it from " + TEST_CLOCK_EXCEPTIONS));
        assertThat(problems).as(GuardMessage.of("G25",
                "a test class asks the machine for the time, or a registered exception is stale",
                "take the instant from a MutableClock or Clock.fixed (fixture/doubles), the business day from BusinessDay; for a stale exception delete its line from " + TEST_CLOCK_EXCEPTIONS)).isEmpty();
    }

    static boolean isSampleFile(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.endsWith("Sample.java") || path.contains("/stray/");
    }

    @Test
    @DisplayName("injection: the test-tree scan finds now() with no argument or only a zone, Clock.system*, in a lambda too, and leaves now(clock) and System.nanoTime alone")
    void testClockReadingsAreFound() {
        JavaClasses samples = new ClassFileImporter().importClasses(AmbientTestTimeSample.class);

        Map<String, Set<String>> found = machineClockReadings(samples);

        assertThat(found).containsOnlyKeys("fixture/infrastructure/AmbientTestTimeSample.java");
        assertThat(found.values().iterator().next()).containsExactlyInAnyOrder("LocalDate.now", "YearMonth.now", "Clock.systemUTC", "Clock.systemDefaultZone", "System.currentTimeMillis", "Instant.now");
        assertThat(isSample(samples.get(AmbientTestTimeSample.class))).isTrue();
    }

    @Test
    @DisplayName("the scan sees the real test classes: the one registered reader is found, so an empty scan cannot pass")
    void theTestScanIsNotEmpty() {
        assertThat(machineClockReadings(TEST_CLASSES)).containsKey("BusinessDay.java");
    }

    static final Pattern STRING_LITERAL = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);
    static final Pattern DATABASE_CLOCK = Pattern.compile(
            "(?<![\\w.])(now\\s*\\(\\s*\\)|(?:current_timestamp|current_date|current_time|localtimestamp|localtime|clock_timestamp|statement_timestamp"
                    + "|transaction_timestamp)\\b)",
            Pattern.CASE_INSENSITIVE);

    /** File#token for every database-clock word inside a Java string literal or text block, comments and identifiers left alone. */
    static Set<String> databaseClockUses(String file, String source) {
        Set<String> found = new LinkedHashSet<>();
        Matcher literal = STRING_LITERAL.matcher(source);
        while (literal.find()) {
            String body = literal.group(1) != null ? literal.group(1) : literal.group(2);
            Matcher clock = DATABASE_CLOCK.matcher(body);
            while (clock.find()) {
                found.add(file + "#" + clock.group(1).toLowerCase().replaceAll("\\s+", ""));
            }
        }
        return found;
    }

    static Set<String> registeredExceptions() throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        for (String line : Files.readAllLines(SQL_EXCEPTIONS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                assertThat(line).as(GuardMessage.of("CS-3.8", "line '" + line + "' in the SQL clock exceptions register has no reason",
                        "write each line in backend/architecture/sql-database-clock-exceptions.txt as <file>#<token>  # <reason>")).contains("  # ");
                entries.add(line.substring(0, line.indexOf("  #")).trim());
            }
        }
        return entries;
    }

    @Test
    @DisplayName("no SQL written in Java reads the database's clock: updated_at and every other instant come from the injected Clock; registered exceptions only")
    void sqlStringsDoNotReadTheDatabaseClock() throws IOException {
        Set<String> exceptions = registeredExceptions();
        Set<String> used = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                used.addAll(databaseClockUses(MAIN.relativize(file).toString(), Files.readString(file)));
            }
        }
        List<String> problems = new ArrayList<>();
        used.stream().filter(u -> !exceptions.contains(u)).forEach(u -> problems.add(u + " reads the database clock: pass the Clock's instant as a parameter"));
        exceptions.stream().filter(e -> !used.contains(e)).forEach(e -> problems.add(e + " is registered but no longer needed: remove it from " + SQL_EXCEPTIONS));
        assertThat(problems).as(GuardMessage.of("CS-3.8",
                "SQL in Java reads the database clock, or a registered exception is stale",
                "pass the Clock's instant to the SQL as a bound parameter; for a stale exception delete its line from " + SQL_EXCEPTIONS)).isEmpty();
    }

    @Test
    @DisplayName("injection: now(), current_timestamp, clock_timestamp and friends are found in literals and text blocks, not in comments, calls or names")
    void sqlClockScannerFindsOnlyLiterals() {
        String source = """
                class A {
                    // updated_at = now() in a comment is not SQL
                    Instant t = time.now();
                    String one = "update t set updated_at = now()";
                    String two = \"\"\"
                            update t set a = CURRENT_TIMESTAMP, b = clock_timestamp()
                            \"\"\";
                    String name = "nowhere";
                    String column = "current_time_zone_offset";
                }
                """;

        assertThat(databaseClockUses("A.java", source)).containsExactlyInAnyOrder("A.java#now()", "A.java#current_timestamp", "A.java#clock_timestamp");
    }
}
