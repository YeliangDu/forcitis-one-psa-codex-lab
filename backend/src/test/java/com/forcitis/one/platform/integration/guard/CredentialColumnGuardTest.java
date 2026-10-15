package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.JavaCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR37-1 (ADR-0037 item 3.4): no SQL text in src/main and no read registry names a column of the credential deny list. The list is the
 * one-ops scan of both databases (PR #143); rows classified "credential" are denied, and so is every "review" row until the implementation
 * PR records it below as not a credential, with its reason. Table and column are both required in the same SQL text (same file for Java and
 * resources, same line for a registry) because many deny-listed column names (PASSWORD, TOKEN, KEY_VALUE) are common words on their own.
 */
class CredentialColumnGuardTest {

    static final Path DENY_LIST = Path.of("../docs/modernization/phase1a/infra/handoff/B-5-dba/evidence/credential_columns.csv");
    static final Path MAIN_JAVA = Path.of("src/main/java");
    static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    static final Path REGISTRIES = Path.of("architecture");

    /** "review" rows of the deny list that the implementation PR has judged not to be credentials: table.column of database -> reason. */
    static final Map<String, String> REVIEWED_NOT_CREDENTIAL = Map.of();

    record DeniedColumn(String database, String table, String column) {

        String key() {
            return database + "." + table + "." + column;
        }
    }

    static List<DeniedColumn> deniedColumns() throws IOException {
        assertThat(DENY_LIST).as("the credential deny list of ADR-0037 item 3.4 must exist; a missing list fails, it is never skipped").exists();
        List<String> lines = Files.readAllLines(DENY_LIST);
        assertThat(lines).as("deny list header").first().isEqualTo("database,schema,table,column,classification");
        List<DeniedColumn> denied = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            assertThat(f).as("deny list row: %s", line).hasSize(5);
            assertThat(f[4]).as("classification of %s", line).isIn("credential", "review");
            DeniedColumn column = new DeniedColumn(f[0], f[2], f[3]);
            if (f[4].equals("credential") || !REVIEWED_NOT_CREDENTIAL.containsKey(column.key())) {
                denied.add(column);
            }
        }
        assertThat(denied).as("the deny list has credential rows").isNotEmpty();
        return denied;
    }

    static Pattern identifier(String name) {
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
    }

    /** The deny-listed columns whose table and column both appear in one SQL text. */
    static Set<String> namedDeniedColumns(String sqlText, List<DeniedColumn> denied) {
        Set<String> named = new TreeSet<>();
        for (DeniedColumn column : denied) {
            if (identifier(column.table()).matcher(sqlText).find() && identifier(column.column()).matcher(sqlText).find()) {
                named.add(column.key());
            }
        }
        return named;
    }

    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");

    /** Only string literals and text blocks: a Java identifier named password is not SQL. */
    static String stringLiterals(String java) {
        String code = JavaCode.withoutComments(java);
        StringBuilder text = new StringBuilder();
        Matcher blocks = TEXT_BLOCK.matcher(code);
        while (blocks.find()) {
            text.append(blocks.group(1)).append('\n');
        }
        Matcher literals = STRING_LITERAL.matcher(TEXT_BLOCK.matcher(code).replaceAll(" "));
        while (literals.find()) {
            text.append(literals.group(1)).append('\n');
        }
        return text.toString();
    }

    static List<Path> files(Path root, String... suffixes) throws IOException {
        assertThat(root).as("scan root").isDirectory();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> Stream.of(suffixes).anyMatch(s -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(s)))
                    .sorted().toList();
        }
    }

    @Test
    @DisplayName("ADR37-1 凭据列守护：src/main 的 SQL 文本（Java 字符串与资源文件）不引用凭据列拒绝清单中的任何列")
    void mainSqlNamesNoCredentialColumn() throws IOException {
        List<DeniedColumn> denied = deniedColumns();
        List<String> violations = new ArrayList<>();
        List<Path> java = files(MAIN_JAVA, ".java");
        assertThat(java).as("Java sources to scan").isNotEmpty();
        for (Path file : java) {
            namedDeniedColumns(stringLiterals(Files.readString(file)), denied).forEach(c -> violations.add(file + " names " + c));
        }
        for (Path file : files(MAIN_RESOURCES, ".sql", ".xml", ".properties", ".yaml", ".yml", ".json", ".bpmn", ".bpmn20.xml")) {
            namedDeniedColumns(Files.readString(file), denied).forEach(c -> violations.add(file + " names " + c));
        }

        assertThat(violations).as("credential columns may be read by the account but never by the application (ADR-0037 item 3.4); "
                + "a new use needs its own ADR. Remove the column from the statement").isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 凭据列守护：读取列登记的任何一行都不登记凭据列拒绝清单中的列")
    void readRegistriesListNoCredentialColumn() throws IOException {
        List<DeniedColumn> denied = deniedColumns();
        List<String> violations = new ArrayList<>();
        List<Path> registries = files(REGISTRIES, ".yaml", ".yml", ".csv");
        assertThat(registries).as("read registries under backend/architecture").isNotEmpty();
        for (Path file : registries) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).replaceFirst("#.*$", "");
                for (String column : namedDeniedColumns(line, denied)) {
                    violations.add(file + ":" + (i + 1) + " registers " + column);
                }
            }
        }

        assertThat(violations).as("a credential column must not be registered for reading (ADR-0037 items 3.4 and 6.1)").isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 凭据列守护自检：含凭据列的语句被识别，同表的非凭据列与只出现列名的普通文字不被误报")
    void detectorFindsAnInjectedCredentialColumn() throws IOException {
        List<DeniedColumn> denied = deniedColumns();
        String java = """
                class Probe {
                    // SELECT CREDENTIAL_VALUE FROM dbo.ORG_PRINCIPAL is only a comment
                    String password = "x";
                    static final String SQL = "SELECT P.LOGIN_NAME, P.CREDENTIAL_VALUE FROM dbo.ORG_PRINCIPAL P";
                    static final String TOKENS = \"""
                            SELECT [token] FROM dbo.system_user_session
                            \""";
                }
                """;

        assertThat(namedDeniedColumns(stringLiterals(java), denied))
                .containsExactlyInAnyOrder("a8_db.ORG_PRINCIPAL.CREDENTIAL_VALUE", "ruoyi-vue-pro.system_user_session.token");
        assertThat(namedDeniedColumns(stringLiterals("class Probe { String sql = \"SELECT LOGIN_NAME FROM dbo.ORG_PRINCIPAL\"; String password; }"), denied))
                .isEmpty();
        assertThat(namedDeniedColumns(stringLiterals("class Probe { String key = \"forcitis.platform.integration.legacy.password\"; }"), denied))
                .isEmpty();
        assertThat(namedDeniedColumns("  - { name: LOGIN_NAME, source: ORG_PRINCIPAL.CREDENTIAL_VALUE, category: 身份 }", denied))
                .containsExactly("a8_db.ORG_PRINCIPAL.CREDENTIAL_VALUE");
    }
}
