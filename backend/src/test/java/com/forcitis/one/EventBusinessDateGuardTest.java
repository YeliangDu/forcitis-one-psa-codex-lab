package com.forcitis.one;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.BusinessZone;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An event's businessDate is a fact the aggregate was told or holds, never today's date: aggregates cannot reach the clock at all. */
class EventBusinessDateGuardTest {
    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ArchitectureRulesTest.ROOT);

    static ArchRule aggregatesDoNotReadTheClock(String packageIdentifier) {
        return noClasses().that().resideInAPackage(packageIdentifier)
                .should().dependOnClassesThat().haveFullyQualifiedName(Clock.class.getName())
                .orShould().dependOnClassesThat().haveFullyQualifiedName(BusinessZone.class.getName())
                .because("the day of a business fact is passed in by the use case that knows it, so an event cannot silently be dated today");
    }

    @Test
    @DisplayName("no domain class depends on the clock or the business zone")
    void domainClassesDoNotReadTheClock() {
        aggregatesDoNotReadTheClock("..domain..").check(CLASSES);
    }

    @Test
    @DisplayName("injection: a class that holds a clock is caught")
    void aClockHolderIsCaught() {
        JavaClasses sample = new ClassFileImporter().importClasses(ClockHolder.class);

        assertThat(aggregatesDoNotReadTheClock("com.forcitis.one..").evaluate(sample).hasViolation()).isTrue();
    }

    static final class ClockHolder {
        final Clock clock = Clock.systemUTC();
    }
}
