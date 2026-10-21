package com.forcitis.one.platform.file.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.file.api.FileImports;
import com.forcitis.one.platform.file.api.PlatformFileContent;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * ADR-0060 sections 2 and 7: reading content is for platform modules, importing for the migration orchestration. Reflection and a
 * platform module passing the interface on are not caught here; review covers them.
 */
class PlatformFileContentCallersTest {

    static final ArchRule ONLY_PLATFORM_MODULES_READ_CONTENT = noClasses().that().resideOutsideOfPackage("com.forcitis.one.platform..")
            .should().dependOnClassesThat().areAssignableTo(PlatformFileContent.class)
            .because("business modules check their own permissions and ask StoredFiles.issueDownloadLink instead (ADR-0060 section 2)");

    // The migration orchestration is not named yet (ADR-0066 section 8): until it is, nothing outside the file service may import.
    static final ArchRule ONLY_THE_MIGRATION_IMPORTS = noClasses().that().resideOutsideOfPackage("com.forcitis.one.platform.file..")
            .should().dependOnClassesThat().areAssignableTo(FileImports.class)
            .because("only the migration orchestration imports legacy files (ADR-0060 section 7, ADR-0066 section 8)");

    @Test
    void onlyPlatformModulesUsePlatformFileContent() {
        ONLY_PLATFORM_MODULES_READ_CONTENT.check(production());
    }

    @Test
    void onlyTheMigrationUsesFileImports() {
        ONLY_THE_MIGRATION_IMPORTS.check(production());
    }

    @Test
    void aBusinessModuleUsingEitherInterfaceIsCaught() throws ClassNotFoundException {
        JavaClasses probe = new ClassFileImporter().importClasses(Class.forName("com.forcitis.one.ProbeFileContentReader"));
        assertThatThrownBy(() -> ONLY_PLATFORM_MODULES_READ_CONTENT.check(probe)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProbeFileContentReader");
        assertThatThrownBy(() -> ONLY_THE_MIGRATION_IMPORTS.check(probe)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProbeFileContentReader");
    }

    private static JavaClasses production() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
    }
}
