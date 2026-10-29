package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.AggregateRoot;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DDD-3: every aggregate root has its event numbers checked; a new root, in any module, fails here until a test whose class name contains
 * EventSequence names it (EventSequenceContinuityTest drives the roots of the organisation and platform modules, the persistence ITs the rest).
 */
class EventSequenceCoverageTest {

    @Test
    @DisplayName("DDD-3 every AggregateRoot subclass of every module is named in a test whose class name contains EventSequence")
    void everyAggregateRootIsCovered() throws IOException {
        List<JavaClass> roots = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one").stream()
                .filter(type -> type.isAssignableTo(AggregateRoot.class) && !type.isEquivalentTo(AggregateRoot.class)).toList();
        assertThat(roots).as(GuardMessage.of("DDD-3", "no AggregateRoot subclass was found, so the coverage check proves nothing", "check that the production classes are compiled and AggregateRoot is still their base type")).isNotEmpty();
        String tests;
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            tests = files.filter(file -> file.getFileName().toString().contains("EventSequence") && file.toString().endsWith(".java"))
                    .map(file -> {
                        try {
                            return Files.readString(file);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).reduce("", String::concat);
        }
        for (JavaClass root : roots) {
            assertThat(tests).as(GuardMessage.of("DDD-3", root.getSimpleName() + " is an aggregate root that no EventSequence test names", "add " + root.getSimpleName() + " to EventSequenceContinuityTest or to an EventSequence*IT test of its module so its event numbers are checked")).contains(root.getSimpleName());
        }
    }
}
