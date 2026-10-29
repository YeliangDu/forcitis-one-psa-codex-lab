package com.forcitis.one;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.forcitis.one.fixture.legacy.LegacyApiSample;
import com.forcitis.one.fixture.legacy.LocalDateTimeInsideSample;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Code standard v2 (#810 item 4): APIs of Boot 3 / Framework 6 and older that agents still write, each failure naming the replacement.
 * Not here because something else already refuses them: @MockBean / @SpyBean (gone from Boot 4.1.1, use @MockitoBean / @MockitoSpyBean),
 * java.sql.Timestamp outside infrastructure (ArchitectureRulesTest.jdbcOnlyInInfrastructure), UUID.randomUUID (IdMakingGuardTest),
 * Instant.now() and friends (TimeSourceGuardTest).
 */
class LegacyApiGuardTest {
    /** javax packages that are the JDK's own and have no jakarta successor. */
    static final String[] JDK_JAVAX = {"javax.crypto..", "javax.sql..", "javax.xml..", "javax.net..", "javax.security..", "javax.naming..",
            "javax.management..", "javax.annotation.processing.."};

    static final ArchRule NO_JAVAX = noClasses().should().dependOnClassesThat(resideInAPackage("javax..").and(not(resideInAnyPackage(JDK_JAVAX))))
            .as("no javax.* outside the JDK's own packages")
            .because("Spring Framework 7 runs on Jakarta EE 11 and no longer supports javax.annotation / javax.inject: use jakarta.*,"
                    + " and org.jspecify.annotations for nullness");

    static final ArchRule NO_REST_TEMPLATE = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .because("RestTemplate is deprecated in Spring Framework 7: use RestClient (or an @HttpExchange interface)");

    static final ArchRule NO_JACKSON_2 = noClasses().should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson.databind..")
            .because("Boot 4 defaults to Jackson 3 and Jackson 2 support is deprecated: use tools.jackson.databind"
                    + " (annotations stay in com.fasterxml.jackson.annotation)");

    static final ArchRule NO_LOCAL_DATE_TIME_FIELDS = noFields().should().haveRawType(LocalDateTime.class)
            .because("a LocalDateTime is no moment and no business day: store an Instant (timestamptz) or a LocalDate in the business zone;"
                    + " a LocalDateTime local to one calculation is fine")
            .allowEmptyShould(true);

    static final ArchRule NO_LOCAL_DATE_TIME_SIGNATURES = codeUnits().should(new ArchCondition<JavaCodeUnit>("not take or return a LocalDateTime") {
        @Override
        public void check(JavaCodeUnit unit, ConditionEvents events) {
            boolean uses = unit.getRawReturnType().isEquivalentTo(LocalDateTime.class)
                    || unit.getRawParameterTypes().stream().anyMatch(type -> type.isEquivalentTo(LocalDateTime.class));
            if (uses) {
                events.add(SimpleConditionEvent.violated(unit, unit.getFullName() + " takes or returns a LocalDateTime"));
            }
        }
    }).because("a LocalDateTime is no moment and no business day: pass an Instant or a LocalDate in the business zone");

    /** On the test classpath (spring-boot-resttestclient comes in transitively) but not loadable without its http client, so a test must not use it. */
    static final ArchRule NO_TEST_REST_TEMPLATE = noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.boot.resttestclient.TestRestTemplate")
            .because("Boot 4 tests call the API through RestTestClient or MockMvc");

    static final List<ArchRule> RULES = List.of(NO_JAVAX, NO_REST_TEMPLATE, NO_JACKSON_2, NO_LOCAL_DATE_TIME_FIELDS, NO_LOCAL_DATE_TIME_SIGNATURES);

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

    @Test
    @DisplayName("injection: each banned API in the sample is caught and the failure names its replacement")
    void eachLegacyApiIsCaught() {
        JavaClasses sample = new ClassFileImporter().importClasses(LegacyApiSample.class);
        for (var expected : List.of(List.of(NO_JAVAX, "javax.swing.Icon", "jakarta.*"), List.of(NO_REST_TEMPLATE, "RestTemplate", "RestClient"),
                List.of(NO_TEST_REST_TEMPLATE, "TestRestTemplate", "RestTestClient"),
                List.of(NO_JACKSON_2, "ObjectMapper", "tools.jackson.databind"), List.of(NO_LOCAL_DATE_TIME_FIELDS, "stamp", "Instant"),
                List.of(NO_LOCAL_DATE_TIME_SIGNATURES, "when(java.time.LocalDateTime)", "LocalDate"))) {
            ArchRule rule = (ArchRule) expected.get(0);
            assertThatThrownBy(() -> rule.check(sample)).isInstanceOf(AssertionError.class)
                    .hasMessageContaining((String) expected.get(1)).hasMessageContaining((String) expected.get(2));
        }
    }

    @Test
    @DisplayName("a LocalDateTime local to one calculation and the JDK's own javax packages stay allowed")
    void allowedUsesPass() {
        JavaClasses inside = new ClassFileImporter().importClasses(LocalDateTimeInsideSample.class);
        RULES.forEach(rule -> rule.check(inside));
        assertThat(ArchitectureRulesTest.CLASSES.stream().anyMatch(type -> type.getDirectDependenciesFromSelf().stream()
                .anyMatch(d -> d.getTargetClass().getPackageName().startsWith("javax.sql")))).as("main uses javax.sql, which is allowed").isTrue();
    }

    @Test
    @DisplayName("libraries replaced in Boot 4 stay off the classpath, so they cannot be used at all")
    void replacedLibrariesAreNotOnTheClasspath() {
        Map<String, String> banned = Map.of(
                "jakarta.persistence.Entity", "JPA: persistence is JdbcClient",
                "org.hibernate.Session", "Hibernate ORM: persistence is JdbcClient",
                "org.springframework.data.jpa.repository.JpaRepository", "Spring Data JPA: persistence is JdbcClient",
                "org.springframework.retry.annotation.Retryable", "spring-retry: use @Retryable from spring-context (@EnableResilientMethods); never retry a non-idempotent write");
        assertAll(banned.entrySet().stream().map(entry -> () -> assertThatThrownBy(() -> Class.forName(entry.getKey()))
                .as(entry.getKey() + " is on the classpath; remove the dependency. " + entry.getValue())
                .isInstanceOf(ClassNotFoundException.class)));
    }
}
