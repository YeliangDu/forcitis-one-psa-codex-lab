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
 * none writes its own). Text guard over src/main/java: the test "effective_to_at is null" (case, spacing and a table alias left open) may be
 * spelled only inside one static final String constant declaration, and that constant exists exactly once. Table names in SQL elsewhere do not
 * matter; the as-of read model uses a range test, not this one. An injected hand-written predicate is shown to be caught.
 */
class CurrentGrantPredicateGuardTest {

    static final Path MAIN = Path.of("src/main/java");
    static final Pattern IS_NULL = Pattern.compile("effective_to_at\\s+is\\s+null", Pattern.CASE_INSENSITIVE);
    /** A constant declaration statement: from "static final String NAME =" up to its closing semicolon (text blocks and concatenation included). */
    static final Pattern CONSTANT = Pattern.compile("static\\s+final\\s+String\\s+\\w+\\s*=[^;]*;", Pattern.DOTALL);

    record Spelling(String where, boolean inConstant) {
    }

    static List<Spelling> spellings(String name, String source) {
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
        assertThat(spellings("Clean.java", "class Clean { static final String CURRENT = \"\"\"\n effective_to_at is null\n \"\"\"; }")).allMatch(Spelling::inConstant);
    }
}
