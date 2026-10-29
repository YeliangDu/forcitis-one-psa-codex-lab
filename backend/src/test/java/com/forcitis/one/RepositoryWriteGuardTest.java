package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DATA_DESIGN_CHECKLIST section 14 items 2 and 4 (review H-4, guard 3): nothing changes state behind the aggregate's back. */
class RepositoryWriteGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Path METHOD_EXCEPTIONS = Path.of("architecture/repository-write-exceptions.txt");
    static final Path UNCONDITIONAL_EXCEPTIONS = Path.of("architecture/versioned-table-unconditional-writes.txt");
    static final Path UNVERSIONED_EXCEPTIONS = Path.of("architecture/unversioned-table-writes.txt");

    static List<Path> persistenceFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(f -> f.toString().endsWith(".java")).filter(f -> f.toString().contains("/infrastructure/")).sorted().toList();
        }
    }

    static Set<String> entries(Path file) throws IOException {
        Set<String> entries = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                entries.add(line.split("#", 2)[0].trim() + (line.split("#", 3).length > 2 ? "#" + line.split("#", 3)[1].trim() : ""));
            }
        }
        return entries;
    }

    static Set<String> exceptionKeys(Path file) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                keys.add(line.substring(0, line.indexOf("  #") < 0 ? line.length() : line.indexOf("  #")).trim());
            }
        }
        return keys;
    }

    static Map<String, DdlNamingTest.Table> tables() throws IOException {
        Map<String, DdlNamingTest.Table> all = new java.util.TreeMap<>();
        DdlNamingTest.model(DdlNamingTest.readMigrations()).forEach((schema, model) -> model.tables.forEach((table, t) -> all.put(schema + "." + table, t)));
        return all;
    }

    static Set<String> statusColumns(Map<String, DdlNamingTest.Table> tables, String table) {
        DdlNamingTest.Table model = tables.get(table);
        Set<String> columns = new TreeSet<>();
        if (model != null) {
            model.columns.keySet().stream().filter(c -> c.equals("status") || c.endsWith("_status")).forEach(columns::add);
        }
        return columns;
    }

    @Test
    @DisplayName("an INSERT into a table with a status column lists that column: the database default never decides a status")
    void insertsListTheStatus() throws IOException {
        Map<String, DdlNamingTest.Table> tables = tables();
        List<String> problems = new ArrayList<>();
        for (Path file : persistenceFiles()) {
            problems.addAll(RepositoryWritePolicy.insertsWithoutStatus(MAIN.relativize(file).toString(), Files.readString(file),
                    table -> statusColumns(tables, table)));
        }
        assertThat(problems).as(GuardMessage.of("CS-2.4",
                "an INSERT into a table with a status column leaves the status column out, so the database default decides the status",
                "list the status column in the INSERT and take its value from the aggregate")).isEmpty();
    }

    @Test
    @DisplayName("a persistence adapter changes state only in save / insert / update / upsert / delete methods; any other write is registered with its reason")
    void repositoryWritesAreTheAggregatesSave() throws IOException {
        Set<String> allowed = exceptionKeys(METHOD_EXCEPTIONS);
        Set<String> found = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (Path file : persistenceFiles()) {
            String name = MAIN.relativize(file).toString();
            for (RepositoryWritePolicy.Write write : RepositoryWritePolicy.writes(Files.readString(file))) {
                if (RepositoryWritePolicy.aggregateMethod(write.method())) {
                    continue;
                }
                String key = name + "#" + write.method();
                found.add(key);
                if (!allowed.contains(key)) {
                    problems.add(name + ":" + write.line() + ": " + write.verb() + " " + write.table() + " in " + write.method()
                            + "(): not a save of the aggregate; change it into one or register it in " + METHOD_EXCEPTIONS);
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-2.4",
                "a persistence adapter changes state in a method that is not a save of the aggregate",
                "turn the write into a save/insert/update/upsert/delete method of the aggregate's repository, or register it in " + METHOD_EXCEPTIONS + " with a reason")).isEmpty();
        assertThat(allowed).as(GuardMessage.of("CS-2.4", "an exception in the repository write register is no longer needed",
                "delete that line from " + METHOD_EXCEPTIONS)).isEqualTo(found);
    }

    @Test
    @DisplayName("an UPDATE or DELETE of a table with a version column states the version it expects; any other is registered once per method with its reason")
    void writesOfVersionedTablesAreConditional() throws IOException {
        Map<String, DdlNamingTest.Table> tables = tables();
        Set<String> allowed = exceptionKeys(UNCONDITIONAL_EXCEPTIONS);
        Set<String> found = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (Path file : persistenceFiles()) {
            String name = MAIN.relativize(file).toString();
            for (RepositoryWritePolicy.Write write : RepositoryWritePolicy.writes(Files.readString(file))) {
                DdlNamingTest.Table model = tables.get(write.table());
                if (write.verb().equals("insert") || write.versionConditional() || model == null
                        || DdlNamingTest.FRAMEWORK_TABLES.contains(write.table()) || !model.columns.containsKey("version")) {
                    continue;
                }
                String key = name + "#" + write.method() + "#" + write.table();
                found.add(key);
                if (!allowed.contains(key)) {
                    problems.add(name + ":" + write.line() + ": " + write.verb() + " " + write.table() + " in " + write.method()
                            + "() does not state 'version = :...'; add the condition or register the write in " + UNCONDITIONAL_EXCEPTIONS);
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-2.6",
                "an UPDATE or DELETE of a table with a version column does not state the version it expects",
                "add 'and version = :version' to the statement, or register the write in " + UNCONDITIONAL_EXCEPTIONS + " with a reason")).isEmpty();
        assertThat(allowed).as(GuardMessage.of("CS-2.6", "an exception in the versioned table unconditional writes register is no longer needed",
                "delete that line from " + UNCONDITIONAL_EXCEPTIONS)).isEqualTo(found);
    }

    @Test
    @DisplayName("a write to a table without an optimistic version (links, versions, counters, logs) is registered once per adapter with its reason")
    void unversionedTableWritesAreRegistered() throws IOException {
        Map<String, DdlNamingTest.Table> tables = tables();
        Set<String> allowed = exceptionKeys(UNVERSIONED_EXCEPTIONS);
        Set<String> found = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (Path file : persistenceFiles()) {
            String name = MAIN.relativize(file).toString();
            for (RepositoryWritePolicy.Write write : RepositoryWritePolicy.writes(Files.readString(file))) {
                DdlNamingTest.Table model = tables.get(write.table());
                if (model == null || DdlNamingTest.FRAMEWORK_TABLES.contains(write.table()) || model.columns.containsKey("version")) {
                    continue;
                }
                String key = name + "#" + write.table();
                found.add(key);
                if (!allowed.contains(key)) {
                    problems.add(name + ":" + write.line() + ": " + write.verb() + " " + write.table() + " has no version column; "
                            + "register the write in " + UNVERSIONED_EXCEPTIONS + " with the reason it is safe");
                }
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-2.6",
                "a write goes to a table without an optimistic version column and is not registered",
                "add a version column and condition the write on it, or register the write in " + UNVERSIONED_EXCEPTIONS + " with the reason it is safe")).isEmpty();
        assertThat(allowed).as(GuardMessage.of("CS-2.6", "an exception in the unversioned table writes register is no longer needed",
                "delete that line from " + UNVERSIONED_EXCEPTIONS)).isEqualTo(found);
    }

    @Test
    @DisplayName("injected: an INSERT without the status column, a claim in a method that is not a save and the enclosing method are found")
    void policyFindsWhatItShould() {
        Map<String, DdlNamingTest.Table> tables = new java.util.HashMap<>();
        DdlNamingTest.Table table = new DdlNamingTest.Table();
        table.columns.put("status", new DdlNamingTest.Column("status", "text", true));
        table.columns.put("name", new DdlNamingTest.Column("name", "text", false));
        tables.put("s.things", table);
        String source = """
                class JdbcThingRepository {
                    void insert(Thing t) {
                        jdbc.sql("insert into s.things (name) values (:name)").update();
                    }

                    void claim(long id) {
                        jdbc.sql("update s.things set name = 'x' where id = :id").update();
                    }

                    void saveAll() {
                        jdbc.sql("insert into s.things (name, status) values (:n, :s)").update();
                    }
                }
                """;
        assertThat(RepositoryWritePolicy.insertsWithoutStatus("T.java", source, t -> statusColumns(tables, t))).singleElement()
                .asString().contains("leaves out status");
        assertThat(RepositoryWritePolicy.writes(source)).extracting(RepositoryWritePolicy.Write::method).containsExactly("insert", "claim", "saveAll");
        assertThat(RepositoryWritePolicy.writes("""
                class JdbcThingRepository {
                    void update(Thing t) {
                        jdbc.sql("update s.things set name = :n where id = :id and version = :version").update();
                        jdbc.sql("update s.things set name = :n where id = :id").update();
                    }
                }
                """)).extracting(RepositoryWritePolicy.Write::versionConditional).containsExactly(true, false);
        assertThat(RepositoryWritePolicy.aggregateMethod("claim")).isFalse();
        assertThat(RepositoryWritePolicy.aggregateMethod("saveHandled")).isTrue();
    }
}
