package com.forcitis.one;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.forcitis.one.fixture.legacy.LegacyApiSample;
import com.forcitis.one.fixture.legacy.LocalDateTimeInsideSample;
import com.forcitis.one.fixture.legacy.ThreadSleepSample;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Code standard v2 (#810 item 4): APIs of Boot 3 / Framework 6 and older that agents still write, each failure naming the replacement.
 * Not here because something else already refuses them: @MockBean / @SpyBean (gone from Boot 4.1.1, use @MockitoBean / @MockitoSpyBean),
 * java.sql.Timestamp outside infrastructure (ArchitectureRulesTest.jdbcOnlyInInfrastructure), UUID.randomUUID (IdMakingGuardTest),
 * Instant.now() and friends (TimeSourceGuardTest).
 */
class LegacyApiGuardTest {
    /** javax packages that are the JDK's own and have no jakarta successor; any other JDK javax package that is needed goes here with a reason. */
    static final String[] JDK_JAVAX = {"javax.crypto..", "javax.sql..", "javax.xml..", "javax.net..", "javax.security..", "javax.naming..",
            "javax.management..", "javax.annotation.processing.."};

    static final ArchRule NO_JAVAX = noClasses().should().dependOnClassesThat(resideInAPackage("javax..").and(not(resideInAnyPackage(JDK_JAVAX))))
            .as("LA-1 javax.* outside the JDK packages in JDK_JAVAX (Spring Framework 7 dropped javax.annotation / javax.inject)")
            .because("fix: use jakarta.*, and org.jspecify.annotations for nullness");

    static final ArchRule NO_REST_TEMPLATE = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .as("LA-2 RestTemplate (deprecated in Spring Framework 7)").because("fix: use RestClient (or an @HttpExchange interface)");

    static final ArchRule NO_JACKSON_2 = noClasses().should()
            .dependOnClassesThat(resideInAPackage("com.fasterxml.jackson..").and(not(resideInAPackage("com.fasterxml.jackson.annotation.."))))
            .as("LA-3 Jackson 2 com.fasterxml.jackson.* (Boot 4 defaults to Jackson 3)")
            .because("fix: use tools.jackson.*; annotations stay in com.fasterxml.jackson.annotation");

    static boolean involvesLocalDateTime(JavaType type) {
        return type.getAllInvolvedRawTypes().stream().anyMatch(raw -> raw.isEquivalentTo(LocalDateTime.class));
    }

    static final ArchRule NO_LOCAL_DATE_TIME_FIELDS = fields().should(new ArchCondition<JavaField>("not hold a LocalDateTime") {
        @Override
        public void check(JavaField field, ConditionEvents events) {
            if (involvesLocalDateTime(field.getType())) {
                events.add(SimpleConditionEvent.violated(field, field.getFullName() + " holds a LocalDateTime"));
            }
        }
    }).as("LA-4 a field holds a LocalDateTime (no moment and no business day)")
            .because("fix: store an Instant (timestamptz) or a LocalDate in the business zone; a LocalDateTime local to one calculation is fine")
            .allowEmptyShould(true);

    static final ArchRule NO_LOCAL_DATE_TIME_SIGNATURES = codeUnits().should(new ArchCondition<JavaCodeUnit>("not take or return a LocalDateTime") {
        @Override
        public void check(JavaCodeUnit unit, ConditionEvents events) {
            if (involvesLocalDateTime(unit.getReturnType()) || unit.getParameterTypes().stream().anyMatch(LegacyApiGuardTest::involvesLocalDateTime)) {
                events.add(SimpleConditionEvent.violated(unit, unit.getFullName() + " takes or returns a LocalDateTime"));
            }
        }
    }).as("LA-5 a method or constructor takes or returns a LocalDateTime (no moment and no business day)")
            .because("fix: pass an Instant or a LocalDate in the business zone");

    /** On the test classpath (spring-boot-resttestclient comes in transitively), but Class.forName on it fails with NoClassDefFoundError. */
    static final ArchRule NO_TEST_REST_TEMPLATE = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.boot.resttestclient.TestRestTemplate")
            .as("LA-6 a test uses TestRestTemplate").because("fix: call the API through RestTestClient or MockMvc");

    static final ArchRule NO_SPRING_NULLNESS = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.lang.Nullable")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.lang.NonNull")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.lang.NonNullApi")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.lang.NonNullFields")
            .as("LA-9 Spring's nullness annotations org.springframework.lang.* (deprecated in Spring Framework 7; OpenApiRequiredProperties reads only JSpecify)")
            .because("fix: use org.jspecify.annotations.Nullable / @NullMarked; on a qualified type write java.time.@Nullable Instant");

    /** Tests that still sleep, until their owner changes them, with the reason. An entry that no longer sleeps fails the stale check. */
    static final Map<String, String> SLEEP_ALLOWED = Map.of();

    static boolean sleepAllowed(JavaClass type) {
        return SLEEP_ALLOWED.keySet().stream().anyMatch(name -> type.getName().equals(name) || type.getName().startsWith(name + "$"));
    }

    static final ArchRule NO_THREAD_SLEEP = noClasses().should(new ArchCondition<JavaClass>("call Thread.sleep") {
        @Override
        public void check(JavaClass type, ConditionEvents events) {
            if (sleepAllowed(type)) {
                return;
            }
            type.getMethodCallsFromSelf().stream()
                    .filter(call -> call.getTargetOwner().isEquivalentTo(Thread.class) && call.getName().equals("sleep"))
                    .forEach(call -> events.add(SimpleConditionEvent.satisfied(call, call.getDescription())));
        }
    }).as("LA-8 a test calls Thread.sleep (a fixed wait is slow when the condition is met early and flaky when it is late)")
            .because("fix: wait for the condition with Awaitility, await().atMost(...).until(...); to assert that nothing happens, await().during(...)");

    static final List<ArchRule> RULES = List.of(NO_JAVAX, NO_REST_TEMPLATE, NO_JACKSON_2, NO_LOCAL_DATE_TIME_FIELDS, NO_LOCAL_DATE_TIME_SIGNATURES, NO_SPRING_NULLNESS);

    @Test
    @DisplayName("production code uses none of the banned legacy APIs (every rule reports, not only the first that fails)")
    void productionCodeUsesNoLegacyApi() {
        assertAll(RULES.stream().map(rule -> () -> rule.check(ArchitectureRulesTest.CLASSES)));
    }

    @Test
    @DisplayName("tests use no TestRestTemplate")
    void testsUseNoTestRestTemplate() {
        NO_TEST_REST_TEMPLATE.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/fixture/legacy/")).importPackages(ArchitectureRulesTest.ROOT));
    }

    static JavaClasses testClasses() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("/fixture/legacy/")).importPackages(ArchitectureRulesTest.ROOT);
    }

    @Test
    @DisplayName("tests do not call Thread.sleep (only the entries of SLEEP_ALLOWED, each with its reason)")
    void testsDoNotSleep() {
        NO_THREAD_SLEEP.check(testClasses());
    }

    @Test
    @DisplayName("injection: every form of Thread.sleep is caught and the failure names Awaitility; a stale SLEEP_ALLOWED entry fails")
    void threadSleepIsCaught() {
        assertThatThrownBy(() -> NO_THREAD_SLEEP.check(new ClassFileImporter().importClasses(ThreadSleepSample.class)))
                .hasMessageContaining("Thread.sleep(long)").hasMessageContaining("Thread.sleep(long, int)").hasMessageContaining("Thread.sleep(java.time.Duration)")
                .hasMessageContaining("fix: wait for the condition with Awaitility");
        JavaClasses tests = testClasses();
        assertAll(SLEEP_ALLOWED.keySet().stream().map(name -> () -> assertThat(tests.stream().filter(type -> type.getName().equals(name) || type.getName().startsWith(name + "$"))
                .flatMap(type -> type.getMethodCallsFromSelf().stream()).anyMatch(call -> call.getTargetOwner().isEquivalentTo(Thread.class) && call.getName().equals("sleep")))
                .as("LA-8 SLEEP_ALLOWED lists " + name + ", which no longer calls Thread.sleep. fix: delete the entry")
                .isTrue()));
    }

    @Test
    @DisplayName("every rule here fails with its id, what is wrong and how to fix it (coding-core D)")
    void everyFailureNamesIdProblemAndFix() {
        for (ArchRule rule : List.of(NO_JAVAX, NO_REST_TEMPLATE, NO_JACKSON_2, NO_LOCAL_DATE_TIME_FIELDS, NO_LOCAL_DATE_TIME_SIGNATURES, NO_TEST_REST_TEMPLATE, NO_THREAD_SLEEP, NO_SPRING_NULLNESS)) {
            assertThat(rule.getDescription()).matches("LA-\\d+ .+, because fix: .+");
        }
    }

    @Test
    @DisplayName("injection: RULES, as the production check runs them, catch each banned API in the sample and the failure names its replacement")
    void eachLegacyApiIsCaught() {
        JavaClasses sample = new ClassFileImporter().importClasses(LegacyApiSample.class);
        List<String> reports = RULES.stream().map(rule -> rule.evaluate(sample).getFailureReport().toString()).toList();
        String sampleName = LegacyApiSample.class.getName();
        Map<String, String> caught = Map.of(
                "javax.swing.Icon", "fix: use jakarta.*",
                "org.springframework.web.client.RestTemplate", "fix: use RestClient",
                "com.fasterxml.jackson.databind.ObjectMapper", "fix: use tools.jackson.*",
                "org.springframework.lang.Nullable", "fix: use org.jspecify.annotations.Nullable",
                sampleName + ".stamp holds", "fix: store an Instant (timestamptz) or a LocalDate in the business zone",
                sampleName + ".stamps holds", "fix: store an Instant (timestamptz) or a LocalDate in the business zone",
                sampleName + ".at(java.time.LocalDateTime) takes", "fix: pass an Instant or a LocalDate in the business zone",
                sampleName + ".made() takes", "fix: pass an Instant or a LocalDate in the business zone",
                sampleName + ".later() takes", "fix: pass an Instant or a LocalDate in the business zone");
        assertAll(caught.entrySet().stream().map(entry -> () -> assertThat(reports)
                .as("one failure report names " + entry.getKey() + " and its replacement")
                .anyMatch(report -> report.contains(entry.getKey()) && report.contains(entry.getValue()))));
        assertThatThrownBy(() -> NO_TEST_REST_TEMPLATE.check(sample)).hasMessageContaining("org.springframework.boot.resttestclient.TestRestTemplate")
                .hasMessageContaining("RestTestClient");
    }

    @Test
    @DisplayName("a LocalDateTime local to one calculation and the JDK's own javax packages stay allowed")
    void allowedUsesPass() {
        JavaClasses inside = new ClassFileImporter().importClasses(LocalDateTimeInsideSample.class);
        RULES.forEach(rule -> rule.check(inside));
        assertThat(ArchitectureRulesTest.CLASSES.stream().anyMatch(type -> type.getDirectDependenciesFromSelf().stream()
                .anyMatch(d -> d.getTargetClass().getPackageName().startsWith("javax.sql")))).as("main uses javax.sql, which is allowed").isTrue();
    }

    /** The class file of a preview-feature build carries minor version 0xFFFF; a normal build carries 0. */
    static List<String> previewClassFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".class")).filter(file -> {
                try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
                    in.readInt();
                    return in.readUnsignedShort() == 0xFFFF;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).map(Path::toString).toList();
        }
    }

    @Test
    @DisplayName("no class file is compiled with Java preview features")
    void noPreviewFeatures() throws IOException {
        List<String> preview = new java.util.ArrayList<>(previewClassFiles(Path.of("target", "classes")));
        preview.addAll(previewClassFiles(Path.of("target", "test-classes")));
        assertThat(Path.of("target", "classes")).as("run from backend/, where the compiled classes are").isDirectory();
        assertThat(preview).as("LA-10 class files compiled with --enable-preview (preview features change or vanish in the next JDK)."
                + " fix: drop --enable-preview from pom.xml and rewrite the code without preview features").isEmpty();
    }

    @Test
    @DisplayName("injection: a class file with minor version 0xFFFF is reported as preview")
    void aPreviewClassFileIsCaught(@TempDir Path dir) throws IOException {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(dir.resolve("Preview.class")))) {
            out.writeInt(0xCAFEBABE);
            out.writeShort(0xFFFF);
            out.writeShort(69);
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(dir.resolve("Normal.class")))) {
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(69);
        }
        assertThat(previewClassFiles(dir)).singleElement().asString().endsWith("Preview.class");
    }

    @Test
    @DisplayName("the build declares no starter that Boot 4 deprecated")
    void noDeprecatedStarter() throws IOException {
        assertThat(Files.readString(Path.of("pom.xml"))).as("LA-11 pom.xml declares spring-boot-starter-web (deprecated in Boot 4)."
                + " fix: declare spring-boot-starter-webmvc").doesNotContain("<artifactId>spring-boot-starter-web</artifactId>");
    }

    @Test
    @DisplayName("libraries replaced in Boot 4 stay off the classpath, so they cannot be used at all")
    void replacedLibrariesAreNotOnTheClasspath() {
        Map<String, String> banned = Map.of(
                "jakarta.persistence.Entity", "persistence is JdbcClient (JPA)",
                "org.hibernate.Session", "persistence is JdbcClient (Hibernate ORM)",
                "org.springframework.data.jpa.repository.JpaRepository", "persistence is JdbcClient (Spring Data JPA)",
                "org.springframework.retry.annotation.Retryable", "use @Retryable from spring-context (@EnableResilientMethods); never retry a non-idempotent write");
        assertAll(banned.entrySet().stream().map(entry -> () -> assertThatThrownBy(() -> Class.forName(entry.getKey()))
                .as("LA-7 " + entry.getKey() + " is on the classpath. fix: remove the dependency; " + entry.getValue())
                .isInstanceOf(ClassNotFoundException.class)));
    }
}
