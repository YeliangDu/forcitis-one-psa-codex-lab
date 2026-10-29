package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0033 section 1 item 10 and guard 2 (the SQL review rule): ids tell nothing about the order things were made in, so a top-level ORDER BY of a SQL text in the
 * production code is not by the primary key or by *_id columns alone; it carries a moment or an explicit number. An ORDER BY inside parentheses
 * (array_agg(... order by ...), a window) orders the inside of a function and is not judged. There is no exception list: a query that really needs a stable
 * order by id alone is written with the moment first.
 */
class SqlOrderByGuardTest {
    static final Path MAIN = Path.of("src/main/java");
    static final Pattern STRING = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);
    static final Pattern ORDER_BY = Pattern.compile("(?i)\\border\\s+by\\b");
    static final Pattern END = Pattern.compile("(?i)\\b(limit|offset|fetch|for\\s+(update|share|no)|union|having)\\b|;");

    static boolean idLike(String key) {
        String name = key.strip().toLowerCase(Locale.ROOT).replaceFirst("^[a-z0-9_]+\\.", "").split("\\s+")[0];
        return name.equals("id") || name.equals("id_") || name.endsWith("_id");
    }

    /** The ORDER BY clauses of one SQL text that stand at the top level and name nothing but ids. */
    static List<String> idOnlyOrders(String sql) {
        List<String> found = new ArrayList<>();
        Matcher order = ORDER_BY.matcher(sql);
        while (order.find()) {
            if (depthAt(sql, order.start()) > 0) {
                continue;
            }
            String rest = sql.substring(order.end());
            Matcher end = END.matcher(rest);
            String clause = end.find() ? rest.substring(0, end.start()) : rest;
            List<String> keys = new ArrayList<>();
            int depth = 0;
            StringBuilder key = new StringBuilder();
            for (char c : clause.toCharArray()) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                }
                if (c == ',' && depth == 0) {
                    keys.add(key.toString());
                    key.setLength(0);
                } else {
                    key.append(c);
                }
            }
            keys.add(key.toString());
            if (keys.stream().filter(k -> !k.isBlank()).allMatch(SqlOrderByGuardTest::idLike) && keys.stream().anyMatch(k -> !k.isBlank())) {
                found.add(clause.replaceAll("\\s+", " ").strip());
            }
        }
        return found;
    }

    private static int depthAt(String sql, int position) {
        int depth = 0;
        for (int i = 0; i < position; i++) {
            if (sql.charAt(i) == '(') {
                depth++;
            } else if (sql.charAt(i) == ')') {
                depth--;
            }
        }
        return depth;
    }

    static List<String> problems(String file, String source) {
        List<String> problems = new ArrayList<>();
        Matcher string = STRING.matcher(StateWritePolicy.withoutComments(source));
        while (string.find()) {
            String sql = string.group(1) != null ? string.group(1) : string.group(2);
            idOnlyOrders(sql).forEach(clause -> problems.add(file + ": ORDER BY " + clause + " orders by ids alone; add the moment (created_at, ...) or an explicit number first"));
        }
        return problems;
    }

    @Test
    @DisplayName("no SQL text in src/main/java has a top-level ORDER BY by the primary key or by *_id columns alone")
    void noOrderByIdsAlone() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                problems.addAll(problems(MAIN.relativize(file).toString(), Files.readString(file)));
            }
        }
        assertThat(problems).as(GuardMessage.of("ADR-0033 section 1 item 10 / guard 2", "a top-level ORDER BY orders by the primary key or *_id columns alone",
                "write the moment first (ORDER BY created_at, id) or an explicit number; ids tell nothing about creation order")).isEmpty();
    }

    @Test
    @DisplayName("injected: order by id, by a *_id column, by an alias.id and by two ids are caught; a moment first, a number, and an ORDER BY inside array_agg or a window are not")
    void theGuardCatchesInjectedSamples() {
        assertThat(problems("A.java", "jdbc.sql(\"select a from t order by id limit 1\")")).hasSize(1);
        assertThat(problems("A.java", "jdbc.sql(\"select a from t order by source_reference_id\")")).hasSize(1);
        assertThat(problems("A.java", "jdbc.sql(\"select a from t x order by x.id desc, x.other_id asc\")")).hasSize(1);
        assertThat(problems("A.java", "jdbc.sql(\"\"\"\n select a from t\n order by id_\n \"\"\")")).hasSize(1);

        assertThat(problems("A.java", "jdbc.sql(\"select a from t order by created_at, id\")")).isEmpty();
        assertThat(problems("A.java", "jdbc.sql(\"select a from t order by id, created_at\")")).isEmpty();
        assertThat(problems("A.java", "jdbc.sql(\"select a from t order by diff_number\")")).isEmpty();
        assertThat(problems("A.java", "jdbc.sql(\"select array_agg(employee_id order by employee_id) from t\")")).isEmpty();
        assertThat(problems("A.java", "jdbc.sql(\"select row_number() over (order by id) from t order by created_at, id\")")).isEmpty();
        assertThat(problems("A.java", "// order by id in a comment\nint order = 1;")).isEmpty();
    }
}
