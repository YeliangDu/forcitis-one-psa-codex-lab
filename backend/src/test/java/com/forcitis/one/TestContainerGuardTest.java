package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DDL-11: a test starts PostgreSQL through TestPostgres only, so every test database sorts the way the dev database does. Files that have not moved yet are in architecture/test-container-gaps.txt. */
class TestContainerGuardTest {
    static final Path TESTS = Path.of("src/test/java");
    static final Path GAPS = Path.of("architecture/test-container-gaps.txt");
    static final String CONSTRUCTION = "new PostgreSQL" + "Container(";
    static final String HOME = "src/test/java/com/forcitis/one/TestPostgres.java";

    @Test
    @DisplayName("DDL-11 the PostgreSQL container is constructed in TestPostgres only; the files that still have it are listed, and a listed file that no longer has it is deleted from the list")
    void containersAreStartedThroughTestPostgres() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(TESTS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String path = file.toString().replace('\\', '/');
                if (!path.equals(HOME) && Files.readString(file).contains(CONSTRUCTION)) {
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
        assertThat(closed).as("listed in %s but they no longer have one (delete the line)", GAPS).isEmpty();
    }
}
