package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #934 with #980 (user intent 2026-11-02 企业微信账号改绑防冒用 ③: any change of an employee's WeCom user id revokes that employee's sessions
 * and browser identifiers). Successor of #961's WecomUserIdWritersRevokeIT, whose release path went with the sync: once the sync is gone the
 * one way a WeCom user id may move on an existing employees row is the profile change, whose caller revokes (pinned by EmployeeWecomRebindIT).
 * Read from the main sources: exactly one SQL statement assigns wecom_user_id on an existing employees row (UPDATE … SET … wecom_user_id =,
 * a tuple SET (…, wecom_user_id, …) =, or INSERT … ON CONFLICT … DO UPDATE SET … wecom_user_id =), and it is the profile-change UPDATE of
 * JdbcPersonnelCommandRepository. A second such statement anywhere, that file included, is red. Inserting a new row is no move and stays
 * allowed. Lead 2026-11-02: a surviving login-miss registration may only fill an empty id, never overwrite or clear a set one.
 *
 * <p>The SQL is read from the string literals and text blocks of each file. Literals joined by +, also across a constant or other name
 * between them, are read as one statement, the name standing for its text.
 */
class WecomUserIdWritersGuardTest {

    static final Path MAIN = Path.of("src/main/java");

    /** The file of the profile change: its caller revokes the sessions and browser identifiers when the id changes (EmployeeWecomRebindIT). */
    static final String ALLOWED = "com/forcitis/one/organization/workforce/infrastructure/persistence/JdbcPersonnelCommandRepository.java";

    /** The one allowed statement: the profile-change UPDATE that writes name, gender, email and WeCom user id together. */
    static final Pattern PROFILE_CHANGE = Pattern.compile(
            "^\\s*update\\s+organization_workforce\\.employees\\s+set\\s+display_name\\s*=\\s*:name\\s*,\\s*gender\\s*=\\s*:gender\\s*,\\s*email\\s*=\\s*:email\\s*,"
                    + "\\s*wecom_user_id\\s*=\\s*:wecom\\b(?:(?!\\bwecom_user_id\\b).)*$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** The table: employees, schema-qualified or not, or a name standing for it (a constant such as EMPLOYEES or EMPLOYEES_TABLE). */
    static final String TABLE = "(?:\\w+\\.)?\\w*employees\\w*";

    /** wecom_user_id assigned: on its own (col = …) or inside a tuple assignment ((…, col, …) = …). */
    static final String ASSIGNED = "(?:\\bwecom_user_id\\s*=|\\([^()]*\\bwecom_user_id\\b[^()]*\\)\\s*=)";

    static final Pattern UPDATE = Pattern.compile("\\bupdate\\s+" + TABLE + "\\s+(?:as\\s+)?(?:\\w+\\s+)?set\\b(?:(?!\\bwhere\\b).)*?" + ASSIGNED,
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static final Pattern UPSERT = Pattern.compile("\\binsert\\s+into\\s+" + TABLE + "\\b.*?\\bon\\s+conflict\\b.*?\\bdo\\s+update\\s+set\\b.*?" + ASSIGNED,
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** A text block or an ordinary string literal. */
    static final Pattern LITERAL = Pattern.compile("\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

    /** What may stand between two literals of one statement: a plus, or a plus, a name (constant, field, call without arguments) and a plus. */
    static final Pattern JOIN = Pattern.compile("\\s*\\+\\s*(?:([\\w.]+(?:\\(\\))?)\\s*\\+\\s*)?");

    record Statement(int line, String sql) {
    }

    /** The SQL-bearing literals of a source, joined literals merged into one statement, with the line it starts on. */
    static List<Statement> statements(String source) {
        List<Statement> statements = new ArrayList<>();
        Matcher literal = LITERAL.matcher(source);
        int end = -1;
        while (literal.find()) {
            String text = literal.group(1) != null ? literal.group(1) : literal.group(2);
            Matcher join = end < 0 ? null : JOIN.matcher(source.substring(end, literal.start()));
            if (join != null && join.matches() && !statements.isEmpty()) {
                Statement last = statements.removeLast();
                String name = join.group(1) == null ? "" : " " + join.group(1) + " ";
                statements.add(new Statement(last.line(), last.sql() + name + text));
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

    static boolean writesAWecomId(String sql) {
        return UPDATE.matcher(sql).find() || UPSERT.matcher(sql).find();
    }

    /** The statements that write a WeCom user id on an existing row, by relative path, as "path:line" and whether each is the profile change. */
    record Writer(String at, boolean profileChange) {
    }

    static List<Writer> writers(Map<String, String> sources) {
        List<Writer> found = new ArrayList<>();
        sources.forEach((path, source) -> {
            for (Statement statement : statements(source)) {
                if (writesAWecomId(statement.sql())) {
                    found.add(new Writer(path + ":" + statement.line(), path.equals(ALLOWED) && PROFILE_CHANGE.matcher(statement.sql()).matches()));
                }
            }
        });
        return found;
    }

    /** Every writer but the single profile-change statement; a second statement of that shape counts too. */
    static List<String> offenders(List<Writer> writers) {
        List<String> offenders = new ArrayList<>();
        boolean profileChangeSeen = false;
        for (Writer writer : writers) {
            if (writer.profileChange() && !profileChangeSeen) {
                profileChangeSeen = true;
            } else {
                offenders.add(writer.at());
            }
        }
        return offenders;
    }

    static Map<String, String> mainSources() throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                sources.put(MAIN.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        }
        return sources;
    }

    @Test
    @DisplayName("#934/#980 企业微信账号改绑防冒用：主代码中给已有员工行写 wecom_user_id 的 SQL 恰有一条，即资料变更的 UPDATE；别的文件或同一仓储里再多一条都算违规（报文件:行）")
    void onlyTheProfileChangeWritesAWecomIdOnAnExistingEmployee() throws IOException {
        List<Writer> writers = writers(mainSources());

        assertThat(writers.stream().filter(Writer::profileChange).toList()).as("premise: the profile-change UPDATE is found by this reading").isNotEmpty();
        assertThat(offenders(writers)).as("statements that move employees.wecom_user_id other than the one profile-change UPDATE (file:line)").isEmpty();
    }

    @Test
    @DisplayName("#934/#980 守护自检：文本块 UPDATE、拼接的 UPDATE、经常量拼表名的 UPDATE、元组 SET、ON CONFLICT DO UPDATE 都被认出；新建员工行、只在 WHERE 里出现、别的表的同名列不被误认")
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
                "  String h = \"update \" + EMPLOYEES + \" set wecom_user_id = :w where id = :id\";",
                "  String i = \"update organization_workforce.employees set (display_name, wecom_user_id) = (:n, :w) where id = :id\";",
                "}");
        List<Integer> lines = new ArrayList<>();
        for (Statement statement : statements(source)) {
            if (writesAWecomId(statement.sql())) {
                lines.add(statement.line());
            }
        }
        assertThat(lines).containsExactly(2, 6, 7, 15, 16);
    }

    @Test
    @DisplayName("#934/#980 守护自检：资料变更仓储里只放过那一条 UPDATE；同一文件再多一条 upsert 或 UPDATE 写 wecom_user_id 即违规，换了形状的资料变更也违规")
    void theAllowedFileHoldsOnlyTheOneStatement() {
        String profileChange = String.join("\n", "class Repo {",
                "  void updateProfile() {",
                "    jdbc.sql(\"\"\"",
                "        update organization_workforce.employees set display_name = :name, gender = :gender, email = :email, wecom_user_id = :wecom,",
                "               updated_at = :at, version = version + 1",
                "         where id = :id",
                "        \"\"\");",
                "  }");
        String alone = profileChange + "\n}";
        String withUpsert = profileChange + "\n  String u = \"insert into organization_workforce.employees (id, wecom_user_id) values (:id, :w) "
                + "on conflict (id) do update set wecom_user_id = excluded.wecom_user_id\";\n}";
        String withSecondUpdate = profileChange + "\n  String v = \"update organization_workforce.employees set wecom_user_id = :w where id = :id\";\n}";
        String twice = profileChange + "\n" + profileChange.substring(profileChange.indexOf("  void")) + "\n}";

        assertThat(offenders(writers(Map.of(ALLOWED, alone)))).as("the profile change alone").isEmpty();
        assertThat(offenders(writers(Map.of(ALLOWED, withUpsert)))).as("a second upsert in the allowed file").containsExactly(ALLOWED + ":9");
        assertThat(offenders(writers(Map.of(ALLOWED, withSecondUpdate)))).as("a second update in the allowed file").containsExactly(ALLOWED + ":9");
        assertThat(offenders(writers(Map.of(ALLOWED, twice)))).as("the profile-change statement twice").hasSize(1);
        assertThat(offenders(writers(Map.of("other/Repo.java", alone)))).as("the same statement in another file").containsExactly("other/Repo.java:3");
    }
}
