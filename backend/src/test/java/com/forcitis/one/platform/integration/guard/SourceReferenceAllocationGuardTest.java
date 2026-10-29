package com.forcitis.one.platform.integration.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.GuardMessage;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.SourceReferences;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Review P3B-L8: until P6 imports the old system's references, own ids are allocated on first sight for the organisation source only,
 * and only its adapter does that; every other caller looks up and never allocates.
 */
class SourceReferenceAllocationGuardTest {

    static final String ORG_SOURCE_ADAPTER = "com.forcitis.one.platform.integration.infrastructure.seeyon..";

    static final ArchRule ONLY_THE_ORG_SOURCE_ADAPTER_ALLOCATES = noClasses().that().resideOutsideOfPackage(ORG_SOURCE_ADAPTER)
            .should().callMethod(SourceReferences.class, "allocate", ExternalSource.class, SourceObjectKind.class, long.class)
            .because(GuardMessage.of("P3B-L8 / ADR-0033 section 2", "a class outside platform.integration.infrastructure.seeyon calls SourceReferences.allocate, but only the organisation source allocates own ids on first sight",
                    "call SourceReferences.allocate only from platform.integration.infrastructure.seeyon, and look up with findOwnIds / findOwnId / findSourceId everywhere else"));

    static final String ALLOCATE_OWN_ID = "com.forcitis.one.platform.integration.application.AllocateOwnId";

    static final ArchRule ONLY_SOURCE_REFERENCES_USES_ALLOCATE_OWN_ID = noClasses()
            .that().haveNameNotMatching("com\\.forcitis\\.one\\.platform\\.integration\\.application\\.(SourceReferences|AllocateOwnId)(\\$.*)?")
            .should().dependOnClassesThat().haveFullyQualifiedName(ALLOCATE_OWN_ID).allowEmptyShould(true)
            .because(GuardMessage.of("P3B-L8", "a class other than SourceReferences depends on AllocateOwnId, the writing half of SourceReferences.allocate, which would walk around the allocation rule",
                    "call SourceReferences.allocate from platform.integration.infrastructure.seeyon instead of using AllocateOwnId directly"));

    @Test
    @DisplayName("P3B-L8 AllocateOwnId is used by SourceReferences only")
    void onlySourceReferencesUsesAllocateOwnId() {
        JavaClasses production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one");
        ONLY_SOURCE_REFERENCES_USES_ALLOCATE_OWN_ID.check(production);
    }

    @Test
    @DisplayName("injected: a class other than SourceReferences that holds AllocateOwnId is caught")
    void anotherUserOfAllocateOwnIdIsCaught() throws ClassNotFoundException {
        JavaClasses offender = new ClassFileImporter().importClasses(Class.forName("com.forcitis.one.platform.integration.application.AllocateOwnIdMisuse"));

        assertThatThrownBy(() -> ONLY_SOURCE_REFERENCES_USES_ALLOCATE_OWN_ID.check(offender)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("AllocateOwnIdMisuse");
    }

    @Test
    @DisplayName("counter-example: SourceReferences itself holds AllocateOwnId and passes")
    void sourceReferencesPasses() {
        JavaClasses holder = new ClassFileImporter().importClasses(SourceReferences.class);

        ONLY_SOURCE_REFERENCES_USES_ALLOCATE_OWN_ID.check(holder);
    }

    @Test
    @DisplayName("P3B-L8 SourceReferences.allocate is called by the organisation source adapter only")
    void onlyTheOrgSourceAdapterAllocates() {
        JavaClasses production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one");
        ONLY_THE_ORG_SOURCE_ADAPTER_ALLOCATES.check(production);
    }

    static final class AllocatesElsewhere {
        Object allocate(SourceReferences references) {
            return references.allocate(ExternalSource.LEGACY_SYSTEM, SourceObjectKind.EMPLOYEE, 1);
        }
    }

    @Test
    @DisplayName("injected: an allocation outside the organisation source adapter is caught and the message says where it belongs")
    void allocationElsewhereIsCaught() {
        JavaClasses offender = new ClassFileImporter().importClasses(AllocatesElsewhere.class);

        assertThatThrownBy(() -> ONLY_THE_ORG_SOURCE_ADAPTER_ALLOCATES.check(offender)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("AllocatesElsewhere").hasMessageContaining("platform.integration.infrastructure.seeyon");
    }
}
