package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DDL-11: a test starts PostgreSQL through TestPostgres only, so every test database sorts the way the dev database does.
 * Judged on the compiled test classes (constructor calls, constructor references, subclasses), so generics, full names, line breaks and
 * PostgreSQLContainer::new do not get round it; a Testcontainers JDBC URL is found by text. Files that have not moved yet are in architecture/test-container-gaps.txt.
 */
class TestContainerGuardTest {
    static final Path TEST_SOURCES = Path.of("src/test");
    static final Path TEST_CLASSES = Path.of("target/test-classes");
    static final Path GAPS = Path.of("architecture/test-container-gaps.txt");
    static final String CONTAINER = "org.testcontainers.postgresql.PostgreSQLContainer";
    static final String HOME = "com.forcitis.one.TestPostgres";
    static final String JDBC_URL = "jdbc:" + "tc:";

    @Test
    @DisplayName("DDL-11 the PostgreSQL container is built, subclassed or referenced as a constructor in TestPostgres only; the files that still do are listed, and a listed file that no longer does is deleted from the list")
    void containersAreStartedThroughTestPostgres() throws IOException {
        Set<String> found = new TreeSet<>();
        JavaClasses compiled = new ClassFileImporter().importPath(TEST_CLASSES);
        assertThat(compiled.size()).as("compiled test classes (run test-compile first)").isGreaterThan(100);
        for (JavaClass type : compiled) {
            String top = type.getName().replaceAll("\\$.*", "");
            if (top.equals(HOME)) {
                continue;
            }
            boolean builds = type.getConstructorCallsFromSelf().stream().anyMatch(call -> call.getTargetOwner().getName().equals(CONTAINER))
                    || type.getCodeUnits().stream().flatMap(unit -> unit.getConstructorReferencesFromSelf().stream())
                            .anyMatch(reference -> reference.getTargetOwner().getName().equals(CONTAINER))
                    || type.getAllRawSuperclasses().stream().anyMatch(parent -> parent.getName().equals(CONTAINER));
            if (builds) {
                found.add("src/test/java/" + top.replace('.', '/') + ".java");
            }
        }
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            for (Path file : files.filter(Files::isRegularFile).filter(f -> f.toString().matches(".*\\.(java|properties|yml|yaml)")).toList()) {
                String path = file.toString().replace('\\', '/');
                if (!path.endsWith("/TestContainerGuardTest.java") && Files.readString(file).contains(JDBC_URL)) {
                    found.add(path);
                }
            }
        }

        Set<String> listed = new TreeSet<>();
        for (String line : Files.readAllLines(GAPS)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split(" \\| ", -1);
            assertThat(columns).as("%s: path | owner | deadline", line).hasSize(3);
            assertThat(columns[1].strip()).as("owner of %s", line).isNotEmpty();
            assertThat(columns[2].strip()).as("deadline of %s", line).isNotEmpty();
            assertThat(listed.add(columns[0])).as("%s is listed twice", columns[0]).isTrue();
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(listed);
        Set<String> closed = new TreeSet<>(listed);
        closed.removeAll(found);

        assertThat(added).as("test sources that start a PostgreSQL container themselves: use TestPostgres.container()").isEmpty();
        assertThat(closed).as("listed in %s but they no longer do (delete the line)", GAPS).isEmpty();
    }
}
