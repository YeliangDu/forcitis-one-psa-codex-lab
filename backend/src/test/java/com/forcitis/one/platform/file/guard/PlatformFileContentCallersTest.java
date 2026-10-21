package com.forcitis.one.platform.file.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.file.api.PlatformFileContent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** ADR-0060 section 2: reading file content is for platform modules only; business modules hand out download links. */
class PlatformFileContentCallersTest {

    static final ArchRule ONLY_PLATFORM_MODULES_READ_CONTENT = noClasses().that().resideOutsideOfPackage("com.forcitis.one.platform..")
            .should().dependOnClassesThat().areAssignableTo(PlatformFileContent.class).allowEmptyShould(true)
            .because("business modules check their own permissions and ask StoredFiles.issueDownloadLink instead (ADR-0060 section 2)");

    @Test
    void onlyPlatformModulesUsePlatformFileContent() {
        ONLY_PLATFORM_MODULES_READ_CONTENT.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one"));
    }

    @Test
    void aBusinessModuleUsingPlatformFileContentIsCaught() throws ClassNotFoundException {
        JavaClasses probe = probe();
        assertThatThrownBy(() -> ONLY_PLATFORM_MODULES_READ_CONTENT.check(probe))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProbeFileContentReader");
    }

    private static JavaClasses probe() throws ClassNotFoundException {
        return new ClassFileImporter().importClasses(Class.forName("com.forcitis.one.ProbeFileContentReader"));
    }
}
