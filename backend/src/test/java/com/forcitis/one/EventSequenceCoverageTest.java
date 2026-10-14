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
 * DDD-3: every aggregate root has its event numbers checked through the database; a new root fails here until a test says so. The
 * packages are those whose roots EventSequencePersistenceIT covers; the other modules widen the list with their own EventSequence test.
 */
class EventSequenceCoverageTest {

    @Test
    @DisplayName("DDD-3 every AggregateRoot subclass of cost and platform.approval is named in a test whose class name contains EventSequence")
    void everyAggregateRootIsCovered() throws IOException {
        List<JavaClass> roots = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.forcitis.one.cost", "com.forcitis.one.platform.approval").stream()
                .filter(type -> type.isAssignableTo(AggregateRoot.class) && !type.isEquivalentTo(AggregateRoot.class)).toList();
        assertThat(roots).as("aggregate roots found").isNotEmpty();
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
            assertThat(tests).as("%s is checked by an EventSequence*IT", root.getSimpleName()).contains(root.getSimpleName());
        }
    }
}
