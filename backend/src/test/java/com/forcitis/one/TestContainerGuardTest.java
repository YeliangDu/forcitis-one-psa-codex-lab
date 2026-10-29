package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    static final String MSSQL_CONTAINER = "org.testcontainers.mssqlserver.MSSQLServerContainer";
    static final String MSSQL_HOME = "com.forcitis.one.TestMssql";
    static final Set<String> MSSQL_IMAGE_SOURCES = Set.of("java/com/forcitis/one/TestMssql.java", "java/com/forcitis/one/TestImages.java",
            "java/com/forcitis/one/TestContainerGuardTest.java");
    static final List<String> MSSQL_IMAGE_MARKS = List.of("MSSQL_PREFIX", "mssql/server", "mcr.microsoft.com/mssql");

    @Test
    @DisplayName("DDL-11 the PostgreSQL container is built, subclassed or referenced as a constructor in TestPostgres only; the files that still do are listed, and a listed file that no longer does is deleted from the list")
    void containersAreStartedThroughTestPostgres() throws IOException {
        Set<String> found = new TreeSet<>();
        JavaClasses compiled = new ClassFileImporter().importPath(TEST_CLASSES);
        assertThat(compiled.size()).as(GuardMessage.of("DDL-11", "fewer than 100 compiled test classes were found in target/test-classes", "run ./mvnw test-compile in backend first so the guard can read the compiled tests")).isGreaterThan(100);
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
            assertThat(columns).as(GuardMessage.of("DDL-11", "the line '" + line + "' of " + GAPS + " has not three columns", "write it as path | owner | deadline")).hasSize(3);
            assertThat(columns[1].strip()).as(GuardMessage.of("DDL-11", "the line '" + line + "' of " + GAPS + " has no owner", "fill the owner column")).isNotEmpty();
            assertThat(columns[2].strip()).as(GuardMessage.of("DDL-11", "the line '" + line + "' of " + GAPS + " has no deadline", "fill the deadline column")).isNotEmpty();
            assertThat(listed.add(columns[0])).as(GuardMessage.of("DDL-11", columns[0] + " is listed twice in " + GAPS, "delete the duplicate line")).isTrue();
        }
        Set<String> added = new TreeSet<>(found);
        added.removeAll(listed);
        Set<String> closed = new TreeSet<>(listed);
        closed.removeAll(found);

        assertThat(added).as(GuardMessage.of("DDL-11", "these test sources start a PostgreSQL container themselves", "get the container from TestPostgres.container() (or TestPostgres.database) instead of PostgreSQLContainer or a jdbc:tc: URL; do not add a line to the gaps file")).isEmpty();
        assertThat(closed).as(GuardMessage.of("DDL-11", "these files are listed in " + GAPS + " but no longer start a PostgreSQL container", "delete their lines from " + GAPS)).isEmpty();
    }

    @Test
    @DisplayName("SQL Server containers are built, subclassed or referenced as a constructor in TestMssql only, and the image prefix is read there only (one shared container per fork)")
    void sqlServerContainersAreStartedThroughTestMssql() throws IOException {
        Set<String> found = new TreeSet<>();
        JavaClasses compiled = new ClassFileImporter().importPath(TEST_CLASSES);
        assertThat(compiled.size()).as(GuardMessage.of("DDL-11", "fewer than 100 compiled test classes were found in target/test-classes", "run ./mvnw test-compile in backend first so the guard can read the compiled tests")).isGreaterThan(100);
        for (JavaClass type : compiled) {
            String top = type.getName().replaceAll("\\$.*", "");
            if (top.equals(MSSQL_HOME)) {
                continue;
            }
            boolean builds = type.getConstructorCallsFromSelf().stream().anyMatch(call -> call.getTargetOwner().getName().equals(MSSQL_CONTAINER))
                    || type.getCodeUnits().stream().flatMap(unit -> unit.getConstructorReferencesFromSelf().stream())
                            .anyMatch(reference -> reference.getTargetOwner().getName().equals(MSSQL_CONTAINER))
                    || type.getAllRawSuperclasses().stream().anyMatch(parent -> parent.getName().equals(MSSQL_CONTAINER));
            if (builds) {
                found.add("src/test/java/" + top.replace('.', '/') + ".java");
            }
        }
        // a constant is inlined by javac, so the read of the image prefix is found in the source text
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            for (Path file : files.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".java")).toList()) {
                String relative = TEST_SOURCES.relativize(file).toString().replace('\\', '/');
                String text = Files.readString(file);
                if (!MSSQL_IMAGE_SOURCES.contains(relative) && MSSQL_IMAGE_MARKS.stream().anyMatch(text::contains)) {
                    found.add("src/test/" + relative);
                }
            }
        }

        assertThat(found).as(GuardMessage.of("DDL-11", "these test sources start SQL Server themselves, read TestImages.MSSQL_PREFIX or name the SQL Server image", "use TestMssql.shared() or TestMssql.database() instead"))
                .isEmpty();
    }
}
