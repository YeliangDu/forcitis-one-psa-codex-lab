package com.forcitis.one;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.CatalogueBusinessDates;
import com.forcitis.one.fixture.domain.ClockReadingSample;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.event.EventDraft;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What this guards: a domain class cannot look at the clock, so the day on an event it records can only come from an argument or from
 * state the aggregate already holds. It does not prove the day is the right fact; each event's publisher test asserts that.
 */
class EventBusinessDateGuardTest {
    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ArchitectureRulesTest.ROOT);

    static final DescribedPredicate<JavaMethodCall> JAVA_TIME_NOW = DescribedPredicate.describe("is a now(...) of a java.time class",
            call -> call.getName().equals("now") && call.getTargetOwner().getPackageName().equals("java.time"));

    static ArchRule domainDoesNotHoldTheClock(String packageIdentifier) {
        return noClasses().that().resideInAPackage(packageIdentifier)
                .should().dependOnClassesThat().haveFullyQualifiedName(Clock.class.getName())
                .orShould().dependOnClassesThat().haveFullyQualifiedName(BusinessZone.class.getName())
                .because(GuardMessage.of("CS-2.5 / CS-2.26", "a domain class depends on Clock or BusinessZone", "remove the Clock or BusinessZone from the domain class and pass the day in as an argument from the application use case that knows it"));
    }

    static ArchRule domainDoesNotCallNow(String packageIdentifier) {
        return noClasses().that().resideInAPackage(packageIdentifier)
                .should().callMethodWhere(JAVA_TIME_NOW)
                .because(GuardMessage.of("CS-2.5 / CS-2.26", "a domain class calls now() of a java.time class, which would date an event with the moment it is recorded", "take the instant or day as a parameter from the application layer, which reads the injected Clock"));
    }

    @Test
    @DisplayName("no domain class depends on the clock or the business zone")
    void domainClassesDoNotHoldTheClock() {
        domainDoesNotHoldTheClock("..domain..").check(CLASSES);
    }

    @Test
    @DisplayName("no domain class calls now() of any java.time class")
    void domainClassesDoNotCallNow() {
        domainDoesNotCallNow("..domain..").check(CLASSES);
    }

    @Test
    @DisplayName("injection: a domain class that holds a clock and one that calls now() are caught")
    void clockUseInADomainPackageIsCaught() {
        JavaClasses sample = new ClassFileImporter().importClasses(ClockReadingSample.class);

        assertThat(domainDoesNotHoldTheClock("..domain..").evaluate(sample).hasViolation()).isTrue();
        assertThat(domainDoesNotCallNow("..domain..").evaluate(sample).getFailureReport().getDetails())
                .hasSize(4).allSatisfy(detail -> assertThat(detail).contains("ClockReadingSample"));
    }

    @Test
    @DisplayName("injection: the rule does not fire outside domain packages, so a violation above is the package, not the sample")
    void otherPackagesAreNotJudged() {
        JavaClasses sample = new ClassFileImporter().importClasses(ClockReadingSample.class);

        assertThat(domainDoesNotCallNow("..application..").allowEmptyShould(true).evaluate(sample).hasViolation()).isFalse();
    }

    static final Map<String, String> DECLARATIONS = Map.of(
            "Undated", "无：运维或配置变更",
            "Dated", "归属的生效日（effectiveFrom）");

    static EventDraft draft(String name, LocalDate businessDate) {
        return new EventDraft("organization.workforce." + name, 1, "X", TestIds.uuid(1), 1, null, businessDate,
                List.of());
    }

    @Test
    @DisplayName("an event the catalogue declares undated must carry no businessDate, and a dated one must carry one")
    void catalogueDeclarationAndEnvelopeAgree() {
        LocalDate day = LocalDate.of(2026, 10, 14);

        assertThat(CatalogueBusinessDates.problem(DECLARATIONS, draft("Undated", null))).isNull();
        assertThat(CatalogueBusinessDates.problem(DECLARATIONS, draft("Dated", day))).isNull();
        assertThat(CatalogueBusinessDates.problem(DECLARATIONS, draft("Undated", day))).contains("declares no businessDate");
        assertThat(CatalogueBusinessDates.problem(DECLARATIONS, draft("Dated", null))).contains("declares a businessDate");
        assertThat(CatalogueBusinessDates.problem(DECLARATIONS, draft("NotInCatalogue", null))).isNull();
    }

    @Test
    @DisplayName("the shared test envelope factories apply that check to every event the aggregates' tests seal")
    void realCatalogueIsLoaded() {
        assertThatThrownBy(() -> CatalogueBusinessDates.check(draft("LegalEntityRegistered", LocalDate.of(2026, 10, 14))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("LegalEntityRegistered");
        assertThatThrownBy(() -> CatalogueBusinessDates.check(draft("LegalEntityAttributed", null)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("LegalEntityAttributed");
    }
}
