package com.forcitis.one.platform.integration.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.GuardMessage;
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
 * That pairing cannot see a column read through a star, so the SQL text of the integration layer names its columns (PR #163 review M-2).
 */
class CredentialColumnGuardTest {

    static final Path DENY_LIST = Path.of("../docs/data/evidence/dba-handoff/credential-columns.csv");
    static final Path MAIN_JAVA = Path.of("src/main/java");
    static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    static final Path REGISTRIES = Path.of("architecture");
    static final Path INTEGRATION_MODULE = MAIN_JAVA.resolve("com/forcitis/one/platform/integration");
    static final String EXTERNAL_CONNECTION_PORT = "ExternalConnections";

    private static final Pattern SELECT_STAR = Pattern.compile(
            "\\bSELECT\\s+(?:(?:DISTINCT|ALL)\\s+|TOP\\s*\\(?\\s*\\d+\\s*\\)?\\s+(?:PERCENT\\s+)?(?:WITH\\s+TIES\\s+)?)*\\*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIED_STAR = Pattern.compile("(?:[A-Za-z_][A-Za-z0-9_]*|\\[[^\\]]+\\])\\s*\\.\\s*\\*(?=\\s*(?:,|FROM\\b))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LISTED_STAR = Pattern.compile(",\\s*\\*\\s*(?:,|FROM\\b)", Pattern.CASE_INSENSITIVE);

    /**
     * "review" rows of the deny list that the implementation PR (ADR37-1) has judged not to be credentials, from the table and column names alone
     * (no database was read): database.table.column -> reason. Every other review row stays denied: an AppKey, a PEM key, a user or office key
     * and the value column of a system configuration table may hold a secret.
     */
    static final Map<String, String> REVIEWED_NOT_CREDENTIAL = Map.of(
            "a8_db.ADDRESSBOOK_SET.KEY_INFO", "通讯录设置表中的关键信息项配置，是显示设置，不是密钥",
            "a8_db.CAP_BUTTON.KEY_VALUE", "表单按钮的键值配置（界面元素），不是密钥",
            "a8_db.CAP_CONTROL.KEY_VALUE", "表单控件的键值配置（界面元素），不是密钥",
            "a8_db.COMMON_MENU_SWITCH.KEY_VALUE", "菜单开关的键值配置，不是密钥",
            "a8_db.COMMON_MENU_TOP_MENU.KEY_VALUE", "顶部菜单的键值配置，不是密钥",
            "a8_db.CTP_CUSTOMIZE.CKEY", "个性化设置的配置项名（键名，值在另一列），不是密钥",
            "a8_db.PORTAL_HOTSPOT.HOTSPOTKEY", "门户热点的标识键，不是密钥");

    record DeniedColumn(String database, String table, String column) {

        String key() {
            return database + "." + table + "." + column;
        }
    }

    static List<DeniedColumn> deniedColumns() throws IOException {
        assertThat(DENY_LIST).as(GuardMessage.of("ADR37-1", "the credential deny list is missing", "restore docs/data/evidence/dba-handoff/credential-columns.csv; a missing list fails and is never skipped")).exists();
        List<String> lines = Files.readAllLines(DENY_LIST);
        assertThat(lines).as(GuardMessage.of("ADR37-1", "the deny list header changed", "keep the header database,schema,table,column,classification in the deny list csv")).first().isEqualTo("database,schema,table,column,classification");
        List<DeniedColumn> denied = new ArrayList<>();
        Set<String> reviewRows = new TreeSet<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            assertThat(f).as(GuardMessage.of("ADR37-1", "the deny list row '" + line + "' has not five columns", "write the row as database,schema,table,column,classification")).hasSize(5);
            assertThat(f[4]).as(GuardMessage.of("ADR37-1", "the deny list row '" + line + "' has another classification", "classify it as credential or review")).isIn("credential", "review");
            DeniedColumn column = new DeniedColumn(f[0], f[2], f[3]);
            if (f[4].equals("review")) {
                reviewRows.add(column.key());
            }
            if (f[4].equals("credential") || !REVIEWED_NOT_CREDENTIAL.containsKey(column.key())) {
                denied.add(column);
            }
        }
        assertThat(reviewRows).as(GuardMessage.of("ADR37-1", "a column judged not a credential in REVIEWED_NOT_CREDENTIAL is no review row of the current deny list", "delete it from REVIEWED_NOT_CREDENTIAL, because a rescan dropped or reclassified it"))
                .containsAll(REVIEWED_NOT_CREDENTIAL.keySet());
        assertThat(denied).as(GuardMessage.of("ADR37-1", "the deny list has no credential rows", "restore the deny list csv; an empty list would let every credential column pass")).isNotEmpty();
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
        assertThat(root).as(GuardMessage.of("ADR37-1", "the scan root " + root + " is not a directory", "run the test from the backend directory so the scan finds src/main and architecture")).isDirectory();
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
        assertThat(java).as(GuardMessage.of("ADR37-1", "no Java source was found to scan", "run the test from the backend directory")).isNotEmpty();
        for (Path file : java) {
            namedDeniedColumns(stringLiterals(Files.readString(file)), denied).forEach(c -> violations.add(file + " names " + c));
        }
        for (Path file : files(MAIN_RESOURCES, ".sql", ".xml", ".properties", ".yaml", ".yml", ".json", ".bpmn", ".bpmn20.xml")) {
            namedDeniedColumns(Files.readString(file), denied).forEach(c -> violations.add(file + " names " + c));
        }

        assertThat(violations).as(GuardMessage.of("ADR37-1", "main SQL text names a column of the credential deny list", "remove the credential column from the statement; a new use of a credential column needs its own ADR")).isEmpty();
    }

    /** Every source of platform.integration, and of any other package that opens an external connection. */
    static List<Path> integrationLayer() throws IOException {
        List<Path> java = files(MAIN_JAVA, ".java");
        Set<Path> adapterPackages = new TreeSet<>();
        for (Path file : java) {
            if (identifier(EXTERNAL_CONNECTION_PORT).matcher(JavaCode.withoutComments(Files.readString(file))).find()) {
                adapterPackages.add(file.getParent());
            }
        }
        return java.stream().filter(file -> file.startsWith(INTEGRATION_MODULE) || adapterPackages.contains(file.getParent())).toList();
    }

    static List<String> starSelections(String sqlText) {
        List<String> found = new ArrayList<>();
        for (Pattern star : List.of(SELECT_STAR, QUALIFIED_STAR, LISTED_STAR)) {
            Matcher m = star.matcher(sqlText);
            while (m.find()) {
                found.add(m.group().replaceAll("\\s+", " "));
            }
        }
        return found;
    }

    @Test
    @DisplayName("ADR37-1 复审 M-2 凭据列守护：集成层的 SQL 文本不用 SELECT * 或 别名.*，列一一写出，凭据列才看得见")
    void integrationSqlNamesItsColumns() throws IOException {
        List<Path> layer = integrationLayer();
        assertThat(layer).as(GuardMessage.of("ADR37-1", "the scanned integration layer holds no seeyon adapter", "check integrationLayer(): the adapter packages must still be found through the ExternalConnections port"))
                .anyMatch(p -> p.toString().contains("seeyon"));
        List<String> violations = new ArrayList<>();
        for (Path file : layer) {
            starSelections(stringLiterals(Files.readString(file))).forEach(star -> violations.add(file + " selects " + star));
        }

        assertThat(violations).as(GuardMessage.of("ADR37-1", "integration SQL text reads columns through a star, which hides credential columns", "name the columns one by one instead of SELECT * or alias.*")).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 复审 M-2 凭据列守护自检：SELECT *、TOP 加 *、别名.*、[schema].[表].* 与列表中的 * 被识别，COUNT(*) 与配置键通配不被误报")
    void starDetectorFindsInjectedStars() {
        String java = "class Probe {\n"
                + "    static final String A = \"SELECT * FROM dbo.SOME_TABLE\";\n"
                + "    static final String B = \"select top (5) * from dbo.SOME_TABLE\";\n"
                + "    static final String C = \"SELECT p.* FROM dbo.SOME_TABLE p\";\n"
                + "    static final String D = \"\"\"\n            SELECT [dbo].[SOME_TABLE] . *\n            FROM dbo.SOME_TABLE\n            \"\"\";\n"
                + "    static final String E = \"SELECT ID, * FROM dbo.SOME_TABLE\";\n"
                + "}\n";
        assertThat(starSelections(stringLiterals(java))).hasSize(5);

        String clean = "class Probe {\n"
                + "    // SELECT * FROM dbo.SOME_TABLE only in a comment\n"
                + "    static final String COUNT = \"SELECT COUNT(*) FROM dbo.SOME_TABLE WHERE A = 2 * B\";\n"
                + "    static final String KEYS = \"no property of forcitis.platform.integration.* is a secret\";\n"
                + "}\n";
        assertThat(starSelections(stringLiterals(clean))).as("COUNT(*), arithmetic, a wildcard key and comments are no star selection").isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 凭据列守护：外部读取登记的任何一行都不登记凭据列拒绝清单中的列")
    void readRegistriesListNoCredentialColumn() throws IOException {
        List<DeniedColumn> denied = deniedColumns();
        List<String> violations = new ArrayList<>();
        List<Path> registries = files(REGISTRIES, ".yaml", ".yml", ".csv");
        assertThat(registries).as(GuardMessage.of("ADR37-1", "no read registry was found under backend/architecture", "run the test from the backend directory")).isNotEmpty();
        for (Path file : registries) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).replaceFirst("#.*$", "");
                for (String column : namedDeniedColumns(line, denied)) {
                    violations.add(file + ":" + (i + 1) + " registers " + column);
                }
            }
        }

        assertThat(violations).as(GuardMessage.of("ADR37-1", "a read registry under backend/architecture registers a credential column of the deny list", "delete the column from the registry line; a credential column is never registered for reading")).isEmpty();
    }

    @Test
    @DisplayName("ADR37-1 凭据列守护自检：含凭据列的语句被识别，同表的非凭据列与只出现列名的普通文字不被误报")
    void detectorFindsAnInjectedCredentialColumn() throws IOException {
        List<DeniedColumn> denied = deniedColumns();
        List<DeniedColumn> credentials = denied.stream().filter(d -> !REVIEWED_NOT_CREDENTIAL.containsKey(d.key())).toList();
        DeniedColumn first = credentials.getFirst();
        DeniedColumn other = credentials.stream().filter(d -> !d.database().equals(first.database()) && !d.table().equalsIgnoreCase(first.table()))
                .findFirst().orElseThrow(() -> new AssertionError("the deny list covers two databases with different credential tables"));
        String plainColumn = "ID";
        assertThat(denied).as("precondition: %s.%s is not deny-listed", first.table(), plainColumn)
                .noneMatch(d -> d.table().equalsIgnoreCase(first.table()) && d.column().equalsIgnoreCase(plainColumn));
        String java = "class Probe {\n"
                + "    // SELECT " + first.column() + " FROM dbo." + first.table() + " is only a comment\n"
                + "    String password = \"x\";\n"
                + "    static final String SQL = \"SELECT P." + plainColumn + ", P." + first.column() + " FROM dbo." + first.table() + " P\";\n"
                + "    static final String TEXT = \"\"\"\n"
                + "            SELECT [" + other.column() + "] FROM dbo." + other.table() + "\n"
                + "            \"\"\";\n"
                + "}\n";

        assertThat(namedDeniedColumns(stringLiterals(java), denied)).contains(first.key(), other.key());
        assertThat(namedDeniedColumns(stringLiterals("class Probe {\n    // " + first.column() + "\n    String sql = \"SELECT " + plainColumn
                + " FROM dbo." + first.table() + "\"; String password; }"), denied)).isEmpty();
        assertThat(namedDeniedColumns(stringLiterals("class Probe { String key = \"forcitis.platform.integration.legacy.password\"; }"), denied))
                .isEmpty();
        assertThat(namedDeniedColumns("  - { name: " + plainColumn + ", source: " + first.table() + "." + first.column() + ", category: EMPLOYEE }", denied))
                .contains(first.key());
    }

    @Test
    @DisplayName("ADR37-1 凭据列守护自检：判定为非凭据的 review 列不再拒绝，其余 review 列仍拒绝")
    void reviewedColumnsAreReleasedOthersStayDenied() throws IOException {
        List<DeniedColumn> denied = deniedColumns();

        assertThat(denied).extracting(DeniedColumn::key).doesNotContainAnyElementsOf(REVIEWED_NOT_CREDENTIAL.keySet());
        List<String> stillDeniedReviews = Files.readAllLines(DENY_LIST).stream().filter(l -> l.endsWith(",review"))
                .map(l -> l.split(",", -1)).map(f -> f[0] + "." + f[2] + "." + f[3])
                .filter(key -> !REVIEWED_NOT_CREDENTIAL.containsKey(key)).toList();
        assertThat(stillDeniedReviews).as("review rows judged credentials").isNotEmpty();
        assertThat(denied).extracting(DeniedColumn::key).containsAll(stillDeniedReviews);
    }
}
