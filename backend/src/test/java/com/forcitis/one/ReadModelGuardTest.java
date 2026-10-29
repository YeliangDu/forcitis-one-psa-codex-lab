package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Read projections (organisation browsing, the sync run log, the audit trail, the work calendar) are served by query services that
 * read through a *Queries port; they never go through the repository that loads and saves aggregates.
 */
class ReadModelGuardTest {
    static final List<String> PROJECTION_PACKAGES = List.of("com.forcitis.one.organization.workforce.application",
            "com.forcitis.one.platform.audit.application", "com.forcitis.one.organization.attendance.application");
    static final Pattern PROJECTION = Pattern.compile("(List|Get|Search).*|.*DirectoryService|WorkCalendarService");

    static boolean isProjection(JavaClass type, List<String> packages) {
        return packages.contains(type.getPackageName()) && PROJECTION.matcher(type.getSimpleName()).matches();
    }

    static List<String> violations(JavaClasses classes, List<String> packages) {
        java.util.Set<String> problems = new java.util.LinkedHashSet<>();
        for (JavaClass type : classes) {
            if (!isProjection(type, packages)) {
                continue;
            }
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (target.getSimpleName().endsWith("Repository") && target.getPackageName().endsWith(".domain")) {
                    problems.add(type.getSimpleName() + " depends on the write repository " + target.getSimpleName() + ": read through a *Queries port");
                }
            }
        }
        return new ArrayList<>(problems);
    }

    @Test
    @DisplayName("organisation browsing, sync runs, the audit trail and the work calendar are read through query services, not the write repositories")
    void projectionsDoNotUseWriteRepositories() {
        assertThat(ArchitectureRulesTest.CLASSES.stream().filter(c -> isProjection(c, PROJECTION_PACKAGES)).count()).as(GuardMessage.of("CS-2.8",
                "the guard found no more than 5 projection use cases, so it is scanning the wrong packages",
                "update PROJECTION_PACKAGES and PROJECTION in ReadModelGuardTest to the current projection packages")).isGreaterThan(5);
        assertThat(violations(ArchitectureRulesTest.CLASSES, PROJECTION_PACKAGES)).as(GuardMessage.of("CS-2.8",
                "a projection query service depends on the write repository that loads and saves aggregates",
                "read through a *Queries port (a domain interface implemented in infrastructure.persistence) instead of the *Repository")).isEmpty();
    }

    @Test
    @DisplayName("injected: a projection that depends on a repository is caught, one that uses a queries port is not")
    void aProjectionOnARepositoryIsCaught() {
        JavaClasses fixture = new ClassFileImporter().withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.forcitis.one.fixture.readmodel");
        List<String> problems = violations(fixture, List.of("com.forcitis.one.fixture.readmodel.application"));

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).contains("ListThings").contains("ThingRepository");
    }
}
