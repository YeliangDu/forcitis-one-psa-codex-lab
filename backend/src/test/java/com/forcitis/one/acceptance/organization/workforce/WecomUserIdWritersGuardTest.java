package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #934 with #980 (user intent 2026-11-02 企业微信账号改绑防冒用 ③: any change of an employee's WeCom user id revokes that employee's sessions
 * and browser identifiers). Successor of #961's WecomUserIdWritersRevokeIT, whose release path went with the sync: once the sync is gone the
 * one way a WeCom user id may move on an existing employees row is the profile change, whose repository write is
 * JdbcPersonnelCommandRepository and whose caller revokes (pinned by EmployeeWecomRebindIT). Read from the main sources: an SQL statement
 * that assigns wecom_user_id on an existing employees row (UPDATE … SET … wecom_user_id =, or INSERT … ON CONFLICT … DO UPDATE SET …
 * wecom_user_id =) appears in no other file. Inserting a new row is no move and stays allowed.
 *
 * <p>The SQL is read from the string literals and text blocks of each file, adjacent literals joined by + read as one statement.
 */
class WecomUserIdWritersGuardTest {

    static final Path MAIN = Path.of("src/main/java");

    /** The profile change: its caller revokes the sessions and browser identifiers when the id changes (EmployeeWecomRebindIT). */
    static final String ALLOWED = "com/forcitis/one/organization/workforce/infrastructure/persistence/JdbcPersonnelCommandRepository.java";

    static final Pattern UPDATE = Pattern.compile("\\bupdate\\s+(?:\\w+\\.)?employees\\s+(?:as\\s+)?(?:\\w+\\s+)?set\\b(?:(?!\\bwhere\\b).)*?\\bwecom_user_id\\s*=",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static final Pattern UPSERT = Pattern.compile("\\binsert\\s+into\\s+(?:\\w+\\.)?employees\\b.*?\\bon\\s+conflict\\b.*?\\bdo\\s+update\\s+set\\b.*?\\bwecom_user_id\\s*=",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** A text block or an ordinary string literal. */
    static final Pattern LITERAL = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

    record Statement(int line, String sql) {
    }

    /** The SQL-bearing literals of a source, adjacent literals joined by + merged into one statement, with the line it starts on. */
    static List<Statement> statements(String source) {
        List<Statement> statements = new ArrayList<>();
        Matcher literal = LITERAL.matcher(source);
        int end = -1;
        while (literal.find()) {
            String text = literal.group(1) != null ? literal.group(1) : literal.group(2);
            boolean joined = end >= 0 && source.substring(end, literal.start()).strip().equals("+");
            if (joined && !statements.isEmpty()) {
                Statement last = statements.removeLast();
                statements.add(new Statement(last.line(), last.sql() + text));
            } else {
                statements.add(new Statement(lineOf(source, literal.start()), text));
            }
            end = literal.end();
        }
        return statements;
    }

    static int lineOf(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    static List<String> writersIn(Path root) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                for (Statement statement : statements(Files.readString(file))) {
                    if (UPDATE.matcher(statement.sql()).find() || UPSERT.matcher(statement.sql()).find()) {
                        found.add(relative + ":" + statement.line());
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("#934/#980 企业微信账号改绑防冒用：主代码中给已有员工行写 wecom_user_id 的 SQL（UPDATE … SET wecom_user_id =、ON CONFLICT … DO UPDATE SET wecom_user_id =）只在资料变更的仓储里")
    void onlyTheProfileChangeWritesAWecomIdOnAnExistingEmployee() throws IOException {
        List<String> writers = writersIn(MAIN);

        assertThat(writers).as("premise: the profile change writes the WeCom user id and is found by this reading").anyMatch(at -> at.startsWith(ALLOWED + ":"));
        assertThat(writers.stream().filter(at -> !at.startsWith(ALLOWED + ":")).toList())
                .as("statements that move employees.wecom_user_id outside the profile change (file:line)").isEmpty();
    }

    @Test
    @DisplayName("#934/#980 守护自检：三种写法（文本块 UPDATE、拼接的 UPDATE、ON CONFLICT DO UPDATE）都被认出，新建员工行与其他表的同名列不被误认")
    void theReadingFindsEveryShapeAndNothingElse() {
        String source = String.join("\n", "class Sample {",
                "  String a = \"\"\"",
                "      update organization_workforce.employees set display_name = :n,",
                "        wecom_user_id = :w where id = :id",
                "      \"\"\";",
                "  String b = \"update employees set \" + \"wecom_user_id = null where id = ?\";",
                "  String c = \"\"\"",
                "      insert into organization_workforce.employees (id, wecom_user_id) values (:id, :w)",
                "      on conflict (id) do update set display_name = excluded.display_name, wecom_user_id = excluded.wecom_user_id",
                "      \"\"\";",
                "  String d = \"insert into organization_workforce.employees (id, wecom_user_id) values (:id, :w)\";",
                "  String e = \"insert into organization_workforce.employees (id, wecom_user_id) values (:id, :w) on conflict (id) do nothing\";",
                "  String f = \"update organization_workforce.login_states set wecom_user_id = :w where id = :id\";",
                "  String g = \"update organization_workforce.employees set display_name = :n where wecom_user_id = :w\";",
                "}");
        List<Integer> lines = new ArrayList<>();
        for (Statement statement : statements(source)) {
            if (UPDATE.matcher(statement.sql()).find() || UPSERT.matcher(statement.sql()).find()) {
                lines.add(statement.line());
            }
        }
        assertThat(lines).containsExactly(2, 6, 7);
    }
}
