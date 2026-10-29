package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DATA_DESIGN_CHECKLIST section 14, item 2: nothing but the aggregate decides a status. */
class StateWriteGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Path EXCEPTIONS = Path.of("architecture/status-write-exceptions.txt");

    static List<Path> layerFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> f.toString().contains("/application/") || f.toString().contains("/infrastructure/")).sorted().toList();
        }
    }

    /** One reviewed line per file: path, then why it may still write a status. Each entry must still be needed. */
    static Set<String> exceptions() throws IOException {
        Set<String> files = new LinkedHashSet<>();
        for (String line : Files.readAllLines(EXCEPTIONS)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                files.add(line.split("#")[0].trim());
            }
        }
        return files;
    }

    @Test
    @DisplayName("application and infrastructure never construct or write a status: no enum constant outside a comparison, no valueOf, no SQL literal")
    void noStatusWritesOutsideTheAggregate() throws IOException {
        Set<String> allowed = exceptions();
        List<String> problems = new ArrayList<>();
        Set<String> needed = new LinkedHashSet<>();
        for (Path file : layerFiles()) {
            String name = MAIN.relativize(file).toString();
            String source = Files.readString(file);
            List<String> found = new ArrayList<>(StateWritePolicy.enumWrites(name, source));
            found.addAll(StateWritePolicy.sqlWrites(name, source));
            if (found.isEmpty()) {
                continue;
            }
            needed.add(name);
            if (!allowed.contains(name)) {
                problems.addAll(found);
            }
        }
        assertThat(problems).as(GuardMessage.of("CS-2.4",
                "application or infrastructure code constructs or writes a status",
                "move the status change into a method of the aggregate; the repository writes what the aggregate holds, or register the file in " + EXCEPTIONS + " with a reason")).isEmpty();
        assertThat(allowed).as(GuardMessage.of("CS-2.4",
                "a file in the status write exceptions register no longer writes a status",
                "delete that line from " + EXCEPTIONS)).isEqualTo(needed);
    }

    @Test
    @DisplayName("injected: a status constant in an assignment, an argument or a parse is caught; a comparison and a switch are not")
    void enumWritesAreCaught() {
        String comparison = "boolean a = run.status() == SyncRunStatus.HELD; boolean b = x != SyncRunStatus.FAILED; boolean c = s.equals(DeliveryStatus.SENT);";
        assertThat(StateWritePolicy.enumWrites("A.java", comparison)).isEmpty();
        assertThat(StateWritePolicy.enumWrites("A.java", "switch (s) { case SyncRunStatus.HELD -> 1; default -> 2; }")).isEmpty();
        assertThat(StateWritePolicy.enumWrites("A.java", "this.status = SyncRunStatus.HELD;")).singleElement().asString().contains("SyncRunStatus.HELD");
        assertThat(StateWritePolicy.enumWrites("A.java", "new NewDelivery(key, DeliveryStatus.PENDING, null)")).hasSize(1);
        assertThat(StateWritePolicy.enumWrites("A.java", "Role r = role.revise(n, enabled ? RoleStatus.ENABLED : RoleStatus.DISABLED, scope);")).hasSize(2);
        assertThat(StateWritePolicy.enumWrites("A.java", "var s = AccountStatus.valueOf(row.status());")).anyMatch(p -> p.contains("valueOf"));
        assertThat(StateWritePolicy.enumWrites("A.java", "// status = SyncRunStatus.HELD in a comment\n/* DeliveryStatus.SENT */ int x;")).isEmpty();
    }

    @Test
    @DisplayName("injected: SQL that sets a status to a literal is caught in update, upsert and insert; a parameter, a where clause and other columns are not")
    void sqlWritesAreCaught() {
        String literalUpdate = "jdbc.sql(\"update t.runs set status = 'FAILED', finished_at = :now where id = :id\")";
        assertThat(StateWritePolicy.sqlWrites("A.java", literalUpdate)).singleElement().asString().contains("status");
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"update t.runs set confirmation_status = 'CONFIRMED' where id = 1\")")).hasSize(1);
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"\"\"\n insert into t.runs (kind, status, started_at)\n values (:k, 'RUNNING', now())\n \"\"\")")).hasSize(1);
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"insert into t.x (a, status) values (:a, 'ACTIVE') on conflict (a) do update set status = 'OLD'\")"))
                .hasSize(2);
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"update t.runs set status = :status where id = :id and status = 'RUNNING'\")")).isEmpty();
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"select id from t.runs where status = 'HELD'\")")).isEmpty();
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"insert into t.x (kind, status) values ('CARD', :status)\")")).isEmpty();
        assertThat(StateWritePolicy.sqlWrites("A.java", "jdbc.sql(\"update t.x set kind = 'CARD' where status = 'A'\")")).isEmpty();
    }
}
