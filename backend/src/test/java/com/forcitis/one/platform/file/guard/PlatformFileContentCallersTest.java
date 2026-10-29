package com.forcitis.one.platform.file.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.GuardMessage;
import com.forcitis.one.platform.file.api.FileImports;
import com.forcitis.one.platform.file.api.FileSubjectKind;
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
            .because(GuardMessage.of("ADR-0060 section 2", "a class outside platform.. depends on PlatformFileContent", "ask StoredFiles.issueDownloadLink after checking the own permissions; only platform modules read file content"));

    // The migration orchestration is not named yet (ADR-0066 section 8): until it is, nothing outside the file service may import.
    static final ArchRule ONLY_THE_MIGRATION_IMPORTS = noClasses().that().resideOutsideOfPackage("com.forcitis.one.platform.file..")
            .should().dependOnClassesThat().areAssignableTo(FileImports.class)
            .because(GuardMessage.of("ADR-0060 section 7 / ADR-0066 section 8", "a class outside platform.file depends on FileImports", "import legacy files only from the migration orchestration, which is not named yet, so nothing outside the file service may import"));

    // A platform module may link any file it is handed (BindFile), so a business module must not speak as one.
    static final ArchRule ONLY_PLATFORM_MODULES_ACT_AS_ONE = noClasses().that().resideOutsideOfPackage("com.forcitis.one.platform..")
            .should().accessField(FileSubjectKind.class, FileSubjectKind.PLATFORM_MODULE.name())
            .because(GuardMessage.of("ADR-0060 section 2", "a class outside platform.. uses FileSubjectKind.PLATFORM_MODULE", "link files as the user or as the link the module acts for (FileSubjectKind.USER or the link subject), not as a platform module"));

    @Test
    void onlyPlatformModulesActAsAPlatformModule() {
        ONLY_PLATFORM_MODULES_ACT_AS_ONE.check(production());
    }

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
        JavaClasses subject = new ClassFileImporter().importClasses(Class.forName("com.forcitis.one.ProbePlatformModuleSubject"));
        assertThatThrownBy(() -> ONLY_PLATFORM_MODULES_ACT_AS_ONE.check(subject)).isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProbePlatformModuleSubject");
    }

    private static JavaClasses production() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.forcitis.one");
    }
}
