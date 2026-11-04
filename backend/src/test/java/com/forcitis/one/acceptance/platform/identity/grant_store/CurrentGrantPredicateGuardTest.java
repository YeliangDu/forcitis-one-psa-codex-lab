package com.forcitis.one.acceptance.platform.identity.grant_store;

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
 * #732 part A, 0034-G3-03 (ADR-0034 decision 4 items 1 and 2: every reader of "current" goes through one predicate constant in the repository,
 * none writes its own; the forms "is null", "isnull", "is not distinct from null" and literals joined with + are all read). Text guard over src/main/java: the test "effective_to_at is null" (case, spacing and a table alias left open) may be
 * spelled only inside one static final String constant declaration, and that constant exists exactly once. Table names in SQL elsewhere do not
 * matter; the as-of read model uses a range test, not this one. An injected hand-written predicate is shown to be caught.
 */
class CurrentGrantPredicateGuardTest {

    static final Path MAIN = Path.of("src/main/java");
    static final Pattern IS_NULL = Pattern.compile("effective_to_at\\s+(?:is\\s+null|isnull|is\\s+not\\s+distinct\\s+from\\s+null)", Pattern.CASE_INSENSITIVE);
    /** Adjacent string literals joined with + ("a " + "b"), so that a split spelling is read as one. */
    static final Pattern CONCATENATION = Pattern.compile("\"\\s*\\+\\s*\"");
    /** A constant declaration statement: from "static final String NAME =" up to its closing semicolon (text blocks and concatenation included). */
    static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+\\w+\\s*=[^;]*;", Pattern.DOTALL);

    record Spelling(String where, boolean inConstant) {
    }

    static List<Spelling> spellings(String name, String rawSource) {
        String source = CONCATENATION.matcher(rawSource).replaceAll("");
        List<int[]> constants = new ArrayList<>();
        Matcher constant = CONSTANT.matcher(source);
        while (constant.find()) {
            constants.add(new int[] {constant.start(), constant.end()});
        }
        List<Spelling> found = new ArrayList<>();
        Matcher match = IS_NULL.matcher(source);
        while (match.find()) {
            int at = match.start();
            boolean inside = constants.stream().anyMatch(range -> at >= range[0] && at < range[1]);
            found.add(new Spelling(name + ":" + source.substring(0, at).split("\n", -1).length, inside));
        }
        return found;
    }

    private static List<Spelling> allSpellings() throws IOException {
        List<Spelling> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                found.addAll(spellings(file.toString(), Files.readString(file)));
            }
        }
        return found;
    }

    @Test
    @DisplayName("0034-G3-03 src/main 中「effective_to_at is null」只出现在唯一一个谓词常量里，没有手写的")
    void theCurrentPredicateIsSpelledOnlyInTheOneConstant() throws IOException {
        List<Spelling> found = allSpellings();

        assertThat(found.stream().filter(s -> !s.inConstant()).map(Spelling::where)).as("hand-written current-validity predicates").isEmpty();
        assertThat(found).as("the predicate constant exists, once").hasSize(1);
    }

    @Test
    @DisplayName("0034-G3-03 守护能拦：注入一条手写的 effective_to_at is null（含换行、大小写、表别名变体）即被发现；常量内的写法不被误报")
    void anInjectedHandWrittenPredicateIsCaught() {
        String injected = """
                class Injected {
                    static final String CURRENT = "effective_to_at is null";
                    String sql = "select 1 from account_role_grants g where g.user_id = :u and g.EFFECTIVE_TO_AT   IS NULL";
                    String block = ""\"
                        select 1 from role_versions where role_id = :r
                           and effective_to_at
                               is null
                        ""\";
                }
                """;

        List<Spelling> found = spellings("Injected.java", injected);

        assertThat(found).hasSize(3);
        assertThat(found.stream().filter(s -> !s.inConstant())).hasSize(2);
        assertThat(spellings("Isnull.java", "class A { String q = \"select 1 where g.effective_to_at isnull\"; }")).hasSize(1).noneMatch(Spelling::inConstant);
        assertThat(spellings("Distinct.java", "class A { String q = \"where effective_to_at IS NOT DISTINCT FROM null\"; }")).hasSize(1).noneMatch(Spelling::inConstant);
        assertThat(spellings("Joined.java", "class A { String q = \"where g.effective_to_at \" + \"is null\"; }")).hasSize(1).noneMatch(Spelling::inConstant);
        assertThat(spellings("JoinedLines.java", "class A { String q = \"where g.effective_to_at\"\n    + \" is\"\n    + \" null\"; }")).hasSize(1).noneMatch(Spelling::inConstant);
        assertThat(spellings("Clean.java", "class Clean { static final String CURRENT = \"\"\"\n effective_to_at is null\n \"\"\"; }")).allMatch(Spelling::inConstant);
    }

    // ---- every SQL text that reads the grant or role version tables ----

    /** Files that may read the tables without the predicate constant, each with its reason (ADR-0034 decision 4 items 3 and 5; the full-history read is an extension of the guard text at ADR-0034 line 412, per #1173 and user intent 2026-11-03-214823, not ADR items 3 and 5). */
    static final java.util.Map<String, String> ALLOWED_READERS = java.util.Map.of("AuthorizationHistory.java",
            "the read-only as-of restore: it reads by a range test at a moment, not the current rows",
            "JdbcFullGrantHistory.java",
            "the full-history read of one account (#1173): it returns open and closed grant rows, so it cannot contain the current-grant predicate");
    static final Pattern TOUCHES = Pattern.compile("\\b(?:account_role_grants|role_versions)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern WRITER = Pattern.compile("\\b(?:insert\\s+into|update|delete\\s+from|lock\\s+table)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern SELECT = Pattern.compile("\\bselect\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern CONSTANT_NAME = Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=[^;]*effective_to_at\\s+(?:is\\s+null|isnull|is\\s+not\\s+distinct\\s+from\\s+null)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)^\\s*//.*$", " ");
    }

    /** The statements (cut at semicolons, adjacent literals joined) that touch either table, read it, and neither use the constant nor sit in an allowed file. */
    static List<String> unguardedReads(String fileName, String rawSource, String constantName) {
        if (ALLOWED_READERS.containsKey(fileName)) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Pattern reference = Pattern.compile("\\b" + Pattern.quote(constantName) + "\\b");
        for (String statement : CONCATENATION.matcher(withoutComments(rawSource)).replaceAll("").split(";")) {
            if (statement.contains("\"") && TOUCHES.matcher(statement).find() && (SELECT.matcher(statement).find() || !WRITER.matcher(statement).find())
                    && !reference.matcher(statement).find()) {
                found.add(fileName + ": " + statement.strip().replaceAll("\\s+", " "));
            }
        }
        return found;
    }

    @Test
    @DisplayName("0034-G3-03 / ADR-0034 第 4 项第 3 条（行 412）：src/main 中触及 account_role_grants、role_versions 的每条读取 SQL 都引用那个谓词常量，或在写明理由的白名单文件（AuthorizationHistory、JdbcFullGrantHistory）里；没有谓词的读取、coalesce 写法都被拦下")
    void everyReadOfTheTablesUsesTheConstantOrIsAllowed() throws IOException {
        String constantName = null;
        List<String> sources = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                sources.add(source);
                names.add(file.getFileName().toString());
                Matcher declared = CONSTANT_NAME.matcher(CONCATENATION.matcher(withoutComments(source)).replaceAll(""));
                if (declared.find()) {
                    constantName = declared.group(1);
                }
            }
        }
        assertThat(constantName).as("the predicate constant (a static final String holding effective_to_at is null) exists").isNotNull();
        List<String> unguarded = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            unguarded.addAll(unguardedReads(names.get(i), sources.get(i), constantName));
        }
        assertThat(unguarded).as("reads of the grant or role version tables without the predicate constant").isEmpty();
    }

    @Test
    @DisplayName("0034-G3-03 守护能拦：没有谓词的读取、coalesce 写法的读取被发现；引用常量的读取、写入语句、白名单文件不被误报")
    void theReadGuardCatchesAReadWithoutThePredicate() {
        String none = "class A { String q = \"select user_id from account_role_grants where role_id = :r\"; }";
        String coalesce = "class A { String q = \"select 1 from account_role_grants g where coalesce(g.effective_to_at, 'infinity') > :t\"; }";
        String versions = "class A { String q = \"select permission_codes from role_versions where role_id = :r\"; }";
        String guarded = "class A { String q = \"select 1 from account_role_grants g where g.user_id = :u and \" + CURRENT_GRANTS; }";
        String writer = "class A { String i = \"insert into account_role_grants (a) values (:a)\"; String u = \"update account_role_grants set updated_at = :t where account_role_grant_id = :id\"; }";

        assertThat(unguardedReads("A.java", none, "CURRENT_GRANTS")).hasSize(1);
        assertThat(unguardedReads("A.java", coalesce, "CURRENT_GRANTS")).hasSize(1);
        assertThat(unguardedReads("A.java", versions, "CURRENT_GRANTS")).hasSize(1);
        assertThat(unguardedReads("A.java", guarded, "CURRENT_GRANTS")).isEmpty();
        assertThat(unguardedReads("A.java", writer, "CURRENT_GRANTS")).isEmpty();
        assertThat(unguardedReads("AuthorizationHistory.java", coalesce, "CURRENT_GRANTS")).isEmpty();
        assertThat(unguardedReads("JdbcFullGrantHistory.java", none, "CURRENT_GRANTS")).isEmpty();
        assertThat(unguardedReads("JdbcGrantHistory.java", none, "CURRENT_GRANTS")).hasSize(1);
    }
}
